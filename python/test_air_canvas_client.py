"""
Unit tests for AirCanvasProtocolHandler, AirCanvasStabilizer, and air_canvas_client integration.
"""

import unittest
from python.air_canvas_client import (
    AirCanvasProtocolHandler,
    AirCanvasStabilizer,
    run_air_canvas_client,
)


class TestAirCanvasProtocolHandler(unittest.TestCase):

    def setUp(self):
        self.handler = AirCanvasProtocolHandler()

    def test_coordinate_scaling_and_clamping(self):
        # Normal scaling
        msg = self.handler.process_frame("DRAW", 0.5, 0.25, has_hand=True)
        self.assertEqual(msg, ("DRAW", 500, 250))

        # Clamp above 1.0
        msg = self.handler.process_frame("DRAW", 1.25, 1.05, has_hand=True)
        self.assertEqual(msg, ("DRAW", 1000, 1000))

    def test_sentinel_zero_and_missing_hand_suppressed(self):
        # Sentinel (0, 0) must never be sent as DRAW
        msg = self.handler.process_frame("DRAW", 0.0, 0.0, has_hand=True)
        self.assertIsNone(msg)

        # Missing hand during brief tracking loss grace period suppresses sending dummy coordinates
        msg_brief = self.handler.process_frame("DRAW", 0.0, 0.0, has_hand=False)
        self.assertIsNone(msg_brief)

    def test_brief_tracking_loss_and_recovery(self):
        # 1. Normal DRAW frame
        msg1 = self.handler.process_frame("DRAW", 0.3, 0.4, has_hand=True)
        self.assertEqual(msg1, ("DRAW", 300, 400))

        # 2. Brief tracking dropout: suppresses output without emitting (0, 0)
        msg_dropout = self.handler.process_frame("DRAW", 0.0, 0.0, has_hand=False)
        self.assertIsNone(msg_dropout)

        # 3. Recovered nearby: continues stroke seamlessly
        msg_recovered = self.handler.process_frame("DRAW", 0.32, 0.42, has_hand=True)
        self.assertEqual(msg_recovered, ("DRAW", 320, 420))

    def test_recovery_too_far_disconnects_stroke(self):
        # 1. Normal DRAW frame at top-left
        self.assertEqual(self.handler.process_frame("DRAW", 0.1, 0.1, has_hand=True), ("DRAW", 100, 100))

        # 2. Brief tracking dropout
        self.assertIsNone(self.handler.process_frame("DRAW", 0.0, 0.0, has_hand=False))

        # 3. Hand reacquired far away at bottom-right (> 0.35 distance):
        # Must disconnect prior stroke by emitting NONE to prevent cross-canvas diagonal line
        msg_disconnect = self.handler.process_frame("DRAW", 0.8, 0.8, has_hand=True)
        self.assertEqual(msg_disconnect, ("NONE", 0, 0))

        # 4. Next frame begins fresh stroke at the new location
        msg_new_stroke = self.handler.process_frame("DRAW", 0.8, 0.8, has_hand=True)
        self.assertEqual(msg_new_stroke, ("DRAW", 800, 800))

    def test_continuous_draw_streaming(self):
        # Fast trajectory changes stream every frame
        msg1 = self.handler.process_frame("DRAW", 0.1, 0.2, has_hand=True)
        self.assertEqual(msg1, ("DRAW", 100, 200))

        msg2 = self.handler.process_frame("DRAW", 0.25, 0.35, has_hand=True)
        self.assertEqual(msg2, ("DRAW", 250, 350))

        msg3 = self.handler.process_frame("DRAW", 0.5, 0.7, has_hand=True)
        self.assertEqual(msg3, ("DRAW", 500, 700))

    def test_continuous_move_streaming(self):
        # MOVE sends every frame with valid coordinates
        msg1 = self.handler.process_frame("MOVE", 0.4, 0.4, has_hand=True)
        self.assertEqual(msg1, ("MOVE", 400, 400))

        msg2 = self.handler.process_frame("MOVE", 0.45, 0.45, has_hand=True)
        self.assertEqual(msg2, ("MOVE", 450, 450))

    def test_one_shot_undo_behavior(self):
        # 1st frame of UNDO with hand coordinates -> fires ("UNDO", 500, 500)
        msg1 = self.handler.process_frame("UNDO", 0.5, 0.5, has_hand=True)
        self.assertEqual(msg1, ("UNDO", 500, 500))

        # Subsequent frames of UNDO while held -> suppressed (None)
        msg2 = self.handler.process_frame("UNDO", 0.5, 0.5, has_hand=True)
        self.assertIsNone(msg2)
        msg3 = self.handler.process_frame("UNDO", 0.5, 0.5, has_hand=True)
        self.assertIsNone(msg3)

        # Transition away to MOVE -> re-arms
        msg4 = self.handler.process_frame("MOVE", 0.6, 0.6, has_hand=True)
        self.assertEqual(msg4, ("MOVE", 600, 600))

        # Re-entering UNDO -> fires again with coordinates
        msg5 = self.handler.process_frame("UNDO", 0.6, 0.6, has_hand=True)
        self.assertEqual(msg5, ("UNDO", 600, 600))

        # Missing hand sends dummy (0, 0)
        self.handler.one_shot_fired["UNDO"] = False
        msg6 = self.handler.process_frame("UNDO", 0.0, 0.0, has_hand=False)
        self.assertEqual(msg6, ("UNDO", 0, 0))

    def test_one_shot_redo_and_clear_behavior(self):
        # REDO with valid hand coordinates
        self.assertEqual(self.handler.process_frame("REDO", 0.5, 0.5, has_hand=True), ("REDO", 500, 500))
        self.assertIsNone(self.handler.process_frame("REDO", 0.5, 0.5, has_hand=True))

        # Transition to CLEAR
        self.assertEqual(self.handler.process_frame("CLEAR", 0.5, 0.5, has_hand=True), ("CLEAR", 500, 500))
        self.assertIsNone(self.handler.process_frame("CLEAR", 0.5, 0.5, has_hand=True))

        # Transition to NONE re-arms both
        self.assertEqual(self.handler.process_frame("NONE", 0.0, 0.0, has_hand=False), ("NONE", 0, 0))
        self.assertEqual(self.handler.process_frame("REDO", 0.5, 0.5, has_hand=True), ("REDO", 500, 500))

        # When hand is missing (has_hand=False), REDO fires with (0, 0)
        self.handler.process_frame("NONE", 0.0, 0.0, has_hand=False)
        self.assertEqual(self.handler.process_frame("REDO", 0.0, 0.0, has_hand=False), ("REDO", 0, 0))

    def test_none_transition_behavior(self):
        # First transition into NONE sends ("NONE", 0, 0)
        msg1 = self.handler.process_frame("NONE", 0.0, 0.0, has_hand=False)
        self.assertEqual(msg1, ("NONE", 0, 0))

        # Subsequent NONE frames are suppressed so idle connection isn't flooded
        msg2 = self.handler.process_frame("NONE", 0.0, 0.0, has_hand=False)
        self.assertIsNone(msg2)

        # Entering DRAW then back to NONE sends ("NONE", 0, 0) again
        self.handler.process_frame("DRAW", 0.3, 0.3, has_hand=True)
        msg3 = self.handler.process_frame("NONE", 0.0, 0.0, has_hand=False)
        self.assertEqual(msg3, ("NONE", 0, 0))

    def test_graceful_connection_refusal(self):
        # When attempting to run client with no server listening, it must exit cleanly returning False
        result = run_air_canvas_client(host="127.0.0.1", port=59998)
        self.assertFalse(result)


class TestAirCanvasStabilizer(unittest.TestCase):

    def test_fast_draw_activation(self):
        stabilizer = AirCanvasStabilizer(required_consecutive=4)
        # Frame 1: DRAW candidate (count 1 < threshold 2) -> remains NONE
        self.assertEqual(stabilizer.update("DRAW", has_hand=True), ("NONE", False))
        # Frame 2: DRAW candidate (count 2 >= threshold 2) -> transitions to DRAW
        self.assertEqual(stabilizer.update("DRAW", has_hand=True), ("DRAW", True))

    def test_instant_pen_up_on_move(self):
        stabilizer = AirCanvasStabilizer(required_consecutive=4)
        stabilizer.update("DRAW", has_hand=True)
        stabilizer.update("DRAW", has_hand=True)
        self.assertEqual(stabilizer.stable_gesture, "DRAW")

        # As soon as user opens middle finger to MOVE -> INSTANT transition on frame 1
        stable, trans = stabilizer.update("MOVE", has_hand=True)
        self.assertEqual(stable, "MOVE")
        self.assertTrue(trans)

    def test_instant_pen_up_on_intentional_none(self):
        stabilizer = AirCanvasStabilizer(required_consecutive=4)
        stabilizer.update("DRAW", has_hand=True)
        stabilizer.update("DRAW", has_hand=True)
        self.assertEqual(stabilizer.stable_gesture, "DRAW")

        # Hand present but user relaxes fingers (raw_gesture=NONE) -> INSTANT pen-up
        stable, trans = stabilizer.update("NONE", has_hand=True)
        self.assertEqual(stable, "NONE")
        self.assertTrue(trans)

    def test_brief_tracking_loss_tolerated(self):
        stabilizer = AirCanvasStabilizer(required_consecutive=4, tracking_loss_tolerance=2)
        stabilizer.update("DRAW", has_hand=True)
        stabilizer.update("DRAW", has_hand=True)
        self.assertEqual(stabilizer.stable_gesture, "DRAW")

        # Frame 1 of missing hand: tolerated, remains DRAW
        stable, trans = stabilizer.update("DRAW", has_hand=False)
        self.assertEqual(stable, "DRAW")
        self.assertFalse(trans)

        # Frame 2 of missing hand: tolerated, remains DRAW
        stable, trans = stabilizer.update("DRAW", has_hand=False)
        self.assertEqual(stable, "DRAW")
        self.assertFalse(trans)

        # Hand reacquired on Frame 3: continues DRAW seamlessly
        stable, trans = stabilizer.update("DRAW", has_hand=True)
        self.assertEqual(stable, "DRAW")
        self.assertFalse(trans)

    def test_prolonged_tracking_loss_terminates(self):
        stabilizer = AirCanvasStabilizer(required_consecutive=4, tracking_loss_tolerance=2)
        stabilizer.update("DRAW", has_hand=True)
        stabilizer.update("DRAW", has_hand=True)
        self.assertEqual(stabilizer.stable_gesture, "DRAW")

        # 2 frames of missing hand tolerated
        stabilizer.update("DRAW", has_hand=False)
        stabilizer.update("DRAW", has_hand=False)

        # 3rd frame of missing hand: exceeds tolerance -> transitions cleanly to NONE
        stable, trans = stabilizer.update("DRAW", has_hand=False)
        self.assertEqual(stable, "NONE")
        self.assertTrue(trans)

    def test_destructive_hysteresis(self):
        stabilizer = AirCanvasStabilizer(required_consecutive=4)
        # Destructive actions (CLEAR, UNDO, REDO) require 4 frames
        self.assertEqual(stabilizer.update("CLEAR", has_hand=True), ("NONE", False))
        self.assertEqual(stabilizer.update("CLEAR", has_hand=True), ("NONE", False))
        self.assertEqual(stabilizer.update("CLEAR", has_hand=True), ("NONE", False))
        stable, trans = stabilizer.update("CLEAR", has_hand=True)
        self.assertEqual(stable, "CLEAR")
        self.assertTrue(trans)


if __name__ == "__main__":
    unittest.main()

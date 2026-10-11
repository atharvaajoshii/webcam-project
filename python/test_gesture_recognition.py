"""
Unit tests for gesture classification and stabilizer logic.
"""

import unittest
from python.gesture_recognition_test import (
    classify_gesture,
    GestureStabilizer,
)


class DummyLandmark:
    def __init__(self, x=0.5, y=0.5, z=0.0):
        self.x = x
        self.y = y
        self.z = z


def build_base_hand():
    """Returns 21 dummy landmarks representing a neutral hand at center."""
    landmarks = [DummyLandmark(0.5, 0.8)]  # 0: Wrist
    # Thumb: 1, 2, 3, 4
    landmarks.append(DummyLandmark(0.45, 0.75))  # 1 CMC
    landmarks.append(DummyLandmark(0.40, 0.70))  # 2 MCP
    landmarks.append(DummyLandmark(0.38, 0.68))  # 3 IP
    landmarks.append(DummyLandmark(0.36, 0.68))  # 4 TIP (neutral)

    # Index: 5 MCP, 6 PIP, 7 DIP, 8 TIP (folded by default)
    landmarks.append(DummyLandmark(0.46, 0.60))  # 5 MCP
    landmarks.append(DummyLandmark(0.46, 0.55))  # 6 PIP
    landmarks.append(DummyLandmark(0.46, 0.58))  # 7 DIP
    landmarks.append(DummyLandmark(0.46, 0.60))  # 8 TIP (folded)

    # Middle: 9 MCP, 10 PIP, 11 DIP, 12 TIP (folded by default)
    landmarks.append(DummyLandmark(0.50, 0.58))  # 9 MCP
    landmarks.append(DummyLandmark(0.50, 0.53))  # 10 PIP
    landmarks.append(DummyLandmark(0.50, 0.56))  # 11 DIP
    landmarks.append(DummyLandmark(0.50, 0.58))  # 12 TIP (folded)

    # Ring: 13 MCP, 14 PIP, 15 DIP, 16 TIP (folded by default)
    landmarks.append(DummyLandmark(0.54, 0.60))  # 13 MCP
    landmarks.append(DummyLandmark(0.54, 0.55))  # 14 PIP
    landmarks.append(DummyLandmark(0.54, 0.58))  # 15 DIP
    landmarks.append(DummyLandmark(0.54, 0.60))  # 16 TIP (folded)

    # Pinky: 17 MCP, 18 PIP, 19 DIP, 20 TIP (folded by default)
    landmarks.append(DummyLandmark(0.58, 0.62))  # 17 MCP
    landmarks.append(DummyLandmark(0.58, 0.58))  # 18 PIP
    landmarks.append(DummyLandmark(0.58, 0.60))  # 19 DIP
    landmarks.append(DummyLandmark(0.58, 0.62))  # 20 TIP (folded)

    return landmarks


def extend_finger(landmarks, tip_idx, pip_idx, target_y):
    """Sets finger TIP higher (lower y) than PIP to simulate extension."""
    landmarks[tip_idx].y = target_y
    landmarks[pip_idx].y = target_y + 0.15


class TestGestureRecognition(unittest.TestCase):

    def test_empty_or_short_landmarks(self):
        g, states = classify_gesture([])
        self.assertEqual(g, "NONE")
        g, states = classify_gesture([DummyLandmark()] * 10)
        self.assertEqual(g, "NONE")

    def test_draw_gesture(self):
        # Index extended, middle/ring/pinky folded
        landmarks = build_base_hand()
        extend_finger(landmarks, 8, 6, target_y=0.30)
        g, states = classify_gesture(landmarks)
        self.assertEqual(g, "DRAW")
        self.assertTrue(states["index"])
        self.assertFalse(states["middle"])
        self.assertFalse(states["ring"])
        self.assertFalse(states["pinky"])

    def test_move_gesture(self):
        # Index and middle extended, ring/pinky folded
        landmarks = build_base_hand()
        extend_finger(landmarks, 8, 6, target_y=0.30)
        extend_finger(landmarks, 12, 10, target_y=0.30)
        g, states = classify_gesture(landmarks)
        self.assertEqual(g, "MOVE")
        self.assertTrue(states["index"])
        self.assertTrue(states["middle"])
        self.assertFalse(states["ring"])
        self.assertFalse(states["pinky"])

    def test_clear_gesture(self):
        # All four fingers extended
        landmarks = build_base_hand()
        extend_finger(landmarks, 8, 6, target_y=0.30)
        extend_finger(landmarks, 12, 10, target_y=0.30)
        extend_finger(landmarks, 16, 14, target_y=0.30)
        extend_finger(landmarks, 20, 18, target_y=0.30)
        g, states = classify_gesture(landmarks)
        self.assertEqual(g, "CLEAR")
        self.assertTrue(states["index"])
        self.assertTrue(states["middle"])
        self.assertTrue(states["ring"])
        self.assertTrue(states["pinky"])

    def test_redo_gesture_thumbs_up(self):
        # 4 fingers folded, thumb pointing up
        landmarks = build_base_hand()
        # Thumb MCP is at 0.70; set IP at 0.60, TIP at 0.50 (well above MCP & index knuckle 0.60)
        landmarks[3].y = 0.58
        landmarks[4].y = 0.48
        g, states = classify_gesture(landmarks)
        self.assertEqual(g, "REDO")
        self.assertEqual(states["thumb"], "UP")

    def test_undo_gesture_thumbs_down(self):
        # 4 fingers folded, thumb pointing down
        landmarks = build_base_hand()
        # Thumb MCP is at 0.70; set IP at 0.74, TIP at 0.78 (well below MCP & index knuckle 0.60)
        landmarks[3].y = 0.74
        landmarks[4].y = 0.78
        g, states = classify_gesture(landmarks)
        self.assertEqual(g, "UNDO")
        self.assertEqual(states["thumb"], "DOWN")

    def test_none_gesture_neutral_fist(self):
        # 4 fingers folded, thumb neutral
        landmarks = build_base_hand()
        g, states = classify_gesture(landmarks)
        self.assertEqual(g, "NONE")

    def test_stabilizer_hysteresis_and_noise_rejection(self):
        stabilizer = GestureStabilizer(required_consecutive=4)

        # 3 frames of DRAW (insufficient to transition)
        self.assertEqual(stabilizer.update("DRAW"), ("NONE", False))
        self.assertEqual(stabilizer.update("DRAW"), ("NONE", False))
        self.assertEqual(stabilizer.update("DRAW"), ("NONE", False))

        # 4th frame triggers transition to DRAW
        stable, trans = stabilizer.update("DRAW")
        self.assertEqual(stable, "DRAW")
        self.assertTrue(trans)

        # 5th frame remains DRAW, no transition
        stable, trans = stabilizer.update("DRAW")
        self.assertEqual(stable, "DRAW")
        self.assertFalse(trans)

        # 1-frame glitch to NONE should be rejected
        stable, trans = stabilizer.update("NONE")
        self.assertEqual(stable, "DRAW")
        self.assertFalse(trans)

        # 4 consecutive frames of CLEAR switches to CLEAR
        self.assertEqual(stabilizer.update("CLEAR"), ("DRAW", False))
        self.assertEqual(stabilizer.update("CLEAR"), ("DRAW", False))
        self.assertEqual(stabilizer.update("CLEAR"), ("DRAW", False))
        stable, trans = stabilizer.update("CLEAR")
        self.assertEqual(stable, "CLEAR")
        self.assertTrue(trans)


if __name__ == "__main__":
    unittest.main()

"""
Air Canvas - Phase 8B Step 4A: Integrated Python Gesture Recognition Client

Captures webcam frames, detects hand landmarks with MediaPipe Tasks Vision,
classifies gestures with multi-frame stabilization, and streams normalized
events over TCP to the Java GestureServer on 127.0.0.1:5000.

Protocol format:
  GESTURE,<NAME>,<X>,<Y>\\n
Coordinates:
  Normalized integers in [0, 1000].
"""

import math
import os
import sys
import time
import urllib.request
from typing import Optional, Tuple
import cv2
import mediapipe as mp

from mediapipe.tasks.python import BaseOptions
from mediapipe.tasks.python.vision import (
    HandLandmarker,
    HandLandmarkerOptions,
    HandLandmarksConnections,
    drawing_utils,
    drawing_styles,
)

try:
    from python.tcp_client import GestureClient
    from python.gesture_recognition_test import (
        classify_gesture,
        GestureStabilizer,
        get_model_path,
        GESTURE_COLORS,
    )
except ImportError:
    from tcp_client import GestureClient
    from gesture_recognition_test import (
        classify_gesture,
        GestureStabilizer,
        get_model_path,
        GESTURE_COLORS,
    )


class AirCanvasStabilizer:
    """
    Stabilizer tailored for responsive Air Canvas drawing:
      - Instant pen-up when leaving DRAW (to MOVE or NONE) or when hand is lost.
      - 2-frame responsive activation for entering DRAW.
      - Multi-frame hysteresis (4 frames) for destructive actions (UNDO, REDO, CLEAR).
    """
    def __init__(self, required_consecutive: int = 4):
        self.required_consecutive = required_consecutive
        self.current_candidate = "NONE"
        self.candidate_count = 0
        self.stable_gesture = "NONE"
        self.prev_stable_gesture = "NONE"

    def update(self, raw_gesture: str, has_hand: bool = True) -> Tuple[str, bool]:
        if not has_hand or raw_gesture == "NONE":
            # Hand is missing or gesture is NONE: immediate pen-up
            if self.stable_gesture != "NONE":
                self.prev_stable_gesture = self.stable_gesture
                self.stable_gesture = "NONE"
                self.current_candidate = "NONE"
                self.candidate_count = 0
                return "NONE", True
            return "NONE", False

        # Immediate pen-up when transitioning from DRAW to MOVE (no trailing strokes)
        if self.stable_gesture == "DRAW" and raw_gesture == "MOVE":
            self.prev_stable_gesture = "DRAW"
            self.stable_gesture = "MOVE"
            self.current_candidate = "MOVE"
            self.candidate_count = self.required_consecutive
            return "MOVE", True

        if raw_gesture == self.current_candidate:
            self.candidate_count += 1
        else:
            self.current_candidate = raw_gesture
            self.candidate_count = 1

        transition = False
        # Fast 2-frame activation into DRAW, standard threshold for others
        threshold = 2 if self.current_candidate == "DRAW" else self.required_consecutive
        if self.candidate_count >= threshold:
            if self.current_candidate != self.stable_gesture:
                self.prev_stable_gesture = self.stable_gesture
                self.stable_gesture = self.current_candidate
                transition = True

        return self.stable_gesture, transition


class AirCanvasProtocolHandler:
    """
    Manages client-side gesture state, coordinate normalization,
    and one-shot filtering before transmitting to TCP server.
    """
    def __init__(self):
        self.last_sent_gesture: Optional[str] = None
        self.one_shot_fired = {
            "UNDO": False,
            "REDO": False,
            "CLEAR": False,
        }

    def process_frame(
        self, stable_gesture: str, raw_norm_x: float, raw_norm_y: float, has_hand: bool = True
    ) -> Optional[Tuple[str, int, int]]:
        """
        Takes stable gesture, normalized float coordinates [0.0, 1.0], and hand presence flag.
        Computes integer coordinates [0, 1000] and returns (gesture, x, y)
        if a message should be sent, or None if suppressed.
        """
        # Clamp and scale coordinates to [0, 1000]
        x_int = max(0, min(1000, int(round(raw_norm_x * 1000))))
        y_int = max(0, min(1000, int(round(raw_norm_y * 1000))))

        # One-shot trigger gestures (UNDO, REDO, CLEAR)
        if stable_gesture in ("UNDO", "REDO", "CLEAR"):
            for g in self.one_shot_fired:
                if g != stable_gesture:
                    self.one_shot_fired[g] = False

            if not self.one_shot_fired[stable_gesture]:
                self.one_shot_fired[stable_gesture] = True
                self.last_sent_gesture = stable_gesture
                return (stable_gesture, 0, 0)
            else:
                return None

        # Transitioning away to non-destructive gesture re-arms all one-shots
        for g in self.one_shot_fired:
            self.one_shot_fired[g] = False

        # NONE state: notify server upon transition into NONE
        if stable_gesture == "NONE":
            if self.last_sent_gesture != "NONE":
                self.last_sent_gesture = "NONE"
                return ("NONE", 0, 0)
            return None

        # Continuous tracking gestures (DRAW, MOVE) with valid coordinates
        if stable_gesture in ("DRAW", "MOVE"):
            if not has_hand or (x_int == 0 and y_int == 0):
                # Hand is missing or invalid coordinates. Cannot draw or move.
                if self.last_sent_gesture in ("DRAW", "MOVE"):
                    self.last_sent_gesture = "NONE"
                    return ("NONE", 0, 0)
                return None

            self.last_sent_gesture = stable_gesture
            return (stable_gesture, x_int, y_int)

        return None



def run_air_canvas_client(
    host: str = "127.0.0.1", port: int = 5000, device_index: int = 0
) -> bool:
    print(f"[AirCanvasClient] Starting client...", flush=True)

    # 1. Establish connection to Java GestureServer
    client = GestureClient(host=host, port=port, timeout=3.0)
    try:
        print(f"[AirCanvasClient] Connecting to Java Paint Server at {host}:{port}...", flush=True)
        client.connect()
        print(f"[AirCanvasClient] Connected successfully to Java Paint Server!", flush=True)
    except ConnectionError as e:
        print("\n" + "=" * 64, flush=True)
        print(f"[AirCanvasClient] CONNECTION REFUSED: Could not connect to {host}:{port}.", flush=True)
        print("[AirCanvasClient] The Java Paint application must be running first!", flush=True)
        print("[AirCanvasClient] Please launch the Java PaintWindow (which starts the GestureServer)", flush=True)
        print("[AirCanvasClient] and then run this client again.", flush=True)
        print("=" * 64 + "\n", flush=True)
        return False

    # 2. Initialize MediaPipe HandLandmarker
    model_path = get_model_path()
    base_options = BaseOptions(model_asset_path=model_path)
    options = HandLandmarkerOptions(
        base_options=base_options,
        num_hands=1,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    landmarker = HandLandmarker.create_from_options(options)

    # 3. Open webcam
    print(f"[AirCanvasClient] Opening camera at index {device_index}...", flush=True)
    cap = cv2.VideoCapture(device_index, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(device_index)

    if not cap.isOpened():
        print(f"[AirCanvasClient] ERROR: Could not open camera at index {device_index}.", flush=True)
        client.close()
        landmarker.close()
        return False

    ret, test_frame = cap.read()
    if not ret or test_frame is None:
        print("[AirCanvasClient] ERROR: Camera opened but failed to capture a frame.", flush=True)
        cap.release()
        client.close()
        landmarker.close()
        return False

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"[AirCanvasClient] Camera initialized: {width}x{height}", flush=True)
    print("[AirCanvasClient] Streaming gesture events to Java. Press 'Q' or ESC to exit.", flush=True)

    window_title = "Air Canvas - Live Gesture Client"
    cv2.namedWindow(window_title, cv2.WINDOW_NORMAL)

    stabilizer = AirCanvasStabilizer(required_consecutive=4)
    protocol_handler = AirCanvasProtocolHandler()

    hand_landmarks_style = drawing_styles.get_default_hand_landmarks_style()
    hand_connections_style = drawing_styles.get_default_hand_connections_style()

    last_trigger_text = ""
    last_trigger_time = 0.0

    try:
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                print("[AirCanvasClient] Warning: Empty video frame.", flush=True)
                break

            # A. Mirror horizontally for natural selfie view
            frame = cv2.flip(frame, 1)

            # B. RGB conversion and MediaPipe inference
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
            result = landmarker.detect(mp_image)

            raw_gesture = "NONE"
            norm_x, norm_y = 0.0, 0.0
            index_pixel_pt = None
            has_hand = False

            if result.hand_landmarks and len(result.hand_landmarks) > 0:
                landmarks = result.hand_landmarks[0]
                has_hand = True

                # Draw landmarks
                drawing_utils.draw_landmarks(
                    frame,
                    landmarks,
                    HandLandmarksConnections.HAND_CONNECTIONS,
                    hand_landmarks_style,
                    hand_connections_style,
                )

                raw_gesture, _ = classify_gesture(landmarks)

                # Index fingertip is landmark 8
                index_tip = landmarks[8]
                norm_x = index_tip.x
                norm_y = index_tip.y
                index_pixel_pt = (int(norm_x * width), int(norm_y * height))

            # C. Stabilize gesture with instant pen-up
            stable_gesture, transition = stabilizer.update(raw_gesture, has_hand=has_hand)

            # D. Dispatch TCP message
            msg = protocol_handler.process_frame(stable_gesture, norm_x, norm_y, has_hand=has_hand)
            if msg is not None:
                g_name, gx, gy = msg
                try:
                    client.send_gesture(g_name, gx, gy)
                    if g_name in ("UNDO", "REDO", "CLEAR"):
                        last_trigger_text = f"ONE-SHOT DISPATCHED: {g_name}"
                        last_trigger_time = time.time()
                        print(f"[AirCanvasClient] >>> {last_trigger_text}", flush=True)
                except ConnectionError as ce:
                    print(f"[AirCanvasClient] Java server disconnected: {ce}", flush=True)
                    break

            if transition:
                print(f"[AirCanvasClient] Transition: {stabilizer.prev_stable_gesture} -> {stable_gesture}", flush=True)

            # E. Render On-Screen HUD
            # Top Banner
            cv2.rectangle(frame, (10, 10), (width - 10, 115), (20, 20, 20), -1)
            cv2.rectangle(frame, (10, 10), (width - 10, 115), (70, 70, 70), 1)

            gesture_color = GESTURE_COLORS.get(stable_gesture, (200, 200, 200))
            cv2.putText(
                frame,
                f"GESTURE: {stable_gesture}",
                (25, 48),
                cv2.FONT_HERSHEY_DUPLEX,
                1.1,
                gesture_color,
                2,
                cv2.LINE_AA,
            )

            # Status line
            conn_status = f"TCP: Connected ({host}:{port})"
            cv2.putText(
                frame,
                conn_status,
                (25, 76),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (0, 255, 128),
                1,
                cv2.LINE_AA,
            )

            # Coordinates display
            curr_x = int(norm_x * 1000)
            curr_y = int(norm_y * 1000)
            coord_str = f"Normalized Coord: X={curr_x}, Y={curr_y} (Index Tip)"
            cv2.putText(
                frame,
                coord_str,
                (25, 102),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (180, 180, 180),
                1,
                cv2.LINE_AA,
            )

            # Drawing pointer cursor overlay
            if index_pixel_pt and stable_gesture in ("DRAW", "MOVE"):
                cursor_color = (0, 255, 0) if stable_gesture == "DRAW" else (255, 200, 0)
                cv2.circle(frame, index_pixel_pt, 8, cursor_color, -1)
                cv2.circle(frame, index_pixel_pt, 12, (255, 255, 255), 2)

            # One-shot trigger notification
            if last_trigger_text and (time.time() - last_trigger_time < 1.5):
                cv2.rectangle(frame, (10, 125), (420, 160), (0, 0, 180), -1)
                cv2.putText(
                    frame,
                    last_trigger_text,
                    (20, 150),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (255, 255, 255),
                    2,
                    cv2.LINE_AA,
                )

            # Bottom instructions
            cv2.putText(
                frame,
                "Press 'Q' or ESC to exit",
                (width - 240, height - 20),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                (220, 220, 220),
                1,
                cv2.LINE_AA,
            )

            cv2.imshow(window_title, frame)

            key = cv2.waitKey(1) & 0xFF
            if key in (ord('q'), ord('Q'), 27):
                print("[AirCanvasClient] Exit requested by user.", flush=True)
                break

            if cv2.getWindowProperty(window_title, cv2.WND_PROP_VISIBLE) < 1:
                print("[AirCanvasClient] Window closed by user.", flush=True)
                break

    finally:
        cap.release()
        cv2.destroyAllWindows()
        client.close()
        landmarker.close()
        print("[AirCanvasClient] Resources cleaned up and sockets closed cleanly.", flush=True)

    return True


if __name__ == "__main__":
    success = run_air_canvas_client()
    sys.exit(0 if success else 1)

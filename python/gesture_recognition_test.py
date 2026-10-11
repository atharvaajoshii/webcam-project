"""
Air Canvas - Phase 8B Step 3: Finger Classification & Gesture Recognition

Recognizes six states from 21 MediaPipe hand landmarks:
  1. DRAW  - Index extended; middle, ring, pinky folded
  2. MOVE  - Index & middle extended; ring, pinky folded
  3. UNDO  - Fist with thumb pointing downward
  4. REDO  - Thumbs-up; thumb pointing upward, other 4 folded
  5. CLEAR - Open palm with four fingers extended
  6. NONE  - No hand detected or unsupported/ambiguous pose

Includes multi-frame gesture stabilization and one-shot transition tracking.
"""

import math
import os
import sys
import time
import urllib.request
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

MODEL_URL = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"


def get_model_path() -> str:
    """Returns local path to hand_landmarker.task, downloading if missing."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    model_path = os.path.join(script_dir, "hand_landmarker.task")
    if not os.path.exists(model_path):
        print(f"[GestureTest] Model file not found. Downloading to {model_path}...", flush=True)
        req = urllib.request.Request(MODEL_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as resp, open(model_path, "wb") as f:
            f.write(resp.read())
        print(f"[GestureTest] Downloaded model ({os.path.getsize(model_path)} bytes).", flush=True)
    return model_path


def dist_2d(p1, p2) -> float:
    """Euclidean distance between two normalized 2D points."""
    return math.hypot(p1.x - p2.x, p1.y - p2.y)


def classify_gesture(landmarks) -> tuple[str, dict]:
    """
    Classifies finger configuration and returns (gesture_name, finger_states).
    
    Landmark Indices:
      0: Wrist
      Thumb: 1 (CMC), 2 (MCP), 3 (IP), 4 (TIP)
      Index: 5 (MCP), 6 (PIP), 7 (DIP), 8 (TIP)
      Middle: 9 (MCP), 10 (PIP), 11 (DIP), 12 (TIP)
      Ring: 13 (MCP), 14 (PIP), 15 (DIP), 16 (TIP)
      Pinky: 17 (MCP), 18 (PIP), 19 (DIP), 20 (TIP)
    """
    if not landmarks or len(landmarks) < 21:
        return "NONE", {}

    wrist = landmarks[0]

    # Helper: Check if finger is extended
    # In an upright hand: tip is higher (lower y) than PIP, and tip is further from wrist
    def is_finger_extended(tip_idx: int, pip_idx: int, mcp_idx: int) -> bool:
        tip = landmarks[tip_idx]
        pip = landmarks[pip_idx]
        mcp = landmarks[mcp_idx]
        is_above_pip = tip.y < pip.y
        is_further = dist_2d(tip, wrist) > (dist_2d(pip, wrist) * 1.05)
        is_away_from_mcp = dist_2d(tip, mcp) > dist_2d(pip, mcp)
        return is_above_pip and is_further and is_away_from_mcp

    index_ext = is_finger_extended(8, 6, 5)
    middle_ext = is_finger_extended(12, 10, 9)
    ring_ext = is_finger_extended(16, 14, 13)
    pinky_ext = is_finger_extended(20, 18, 17)

    # Thumb analysis
    thumb_tip = landmarks[4]
    thumb_ip = landmarks[3]
    thumb_mcp = landmarks[2]
    index_mcp = landmarks[5]

    thumb_state = "NEUTRAL"
    # Thumbs-up: thumb tip is clearly above MCP and IP, above index knuckle, pointing up
    if (thumb_mcp.y - thumb_tip.y > 0.05) and (thumb_tip.y < thumb_ip.y) and (thumb_tip.y < index_mcp.y):
        thumb_state = "UP"
    # Thumbs-down: thumb tip is clearly below MCP and IP, lower than index knuckle, pointing down
    elif (thumb_tip.y - thumb_mcp.y > 0.04) and (thumb_tip.y > thumb_ip.y) and (thumb_tip.y > index_mcp.y):
        thumb_state = "DOWN"

    finger_states = {
        "thumb": thumb_state,
        "index": index_ext,
        "middle": middle_ext,
        "ring": ring_ext,
        "pinky": pinky_ext,
    }

    four_folded = (not index_ext) and (not middle_ext) and (not ring_ext) and (not pinky_ext)
    four_extended = index_ext and middle_ext and ring_ext and pinky_ext

    # 1. DRAW: Index extended; middle, ring, pinky folded
    if index_ext and (not middle_ext) and (not ring_ext) and (not pinky_ext):
        return "DRAW", finger_states

    # 2. MOVE: Index and middle extended; ring and pinky folded
    if index_ext and middle_ext and (not ring_ext) and (not pinky_ext):
        return "MOVE", finger_states

    # 3. UNDO: Fist with thumb pointing downward
    if four_folded and (thumb_state == "DOWN"):
        return "UNDO", finger_states

    # 4. REDO: Thumbs-up (thumb pointing upward, other 4 folded)
    if four_folded and (thumb_state == "UP"):
        return "REDO", finger_states

    # 5. CLEAR: Open palm with all four fingers extended
    if four_extended:
        return "CLEAR", finger_states

    # 6. NONE: Ambiguous or unsupported pose
    return "NONE", finger_states


class GestureStabilizer:
    """
    Ensures a gesture must be observed consistently across several consecutive frames
    before transitioning the stable recognized gesture.
    """
    def __init__(self, required_consecutive: int = 5):
        self.required_consecutive = required_consecutive
        self.current_candidate = "NONE"
        self.candidate_count = 0
        self.stable_gesture = "NONE"
        self.prev_stable_gesture = "NONE"

    def update(self, raw_gesture: str) -> tuple[str, bool]:
        """
        Updates stabilizer with raw detected gesture.
        Returns (stable_gesture, transition_occurred).
        """
        if raw_gesture == self.current_candidate:
            self.candidate_count += 1
        else:
            self.current_candidate = raw_gesture
            self.candidate_count = 1

        transition = False
        if self.candidate_count >= self.required_consecutive:
            if self.current_candidate != self.stable_gesture:
                self.prev_stable_gesture = self.stable_gesture
                self.stable_gesture = self.current_candidate
                transition = True

        return self.stable_gesture, transition


# Color palette for recognized gestures
GESTURE_COLORS = {
    "DRAW": (0, 255, 0),        # Vibrant Green
    "MOVE": (255, 200, 0),      # Sky Blue / Cyan
    "UNDO": (0, 140, 255),      # Orange
    "REDO": (255, 0, 255),      # Magenta
    "CLEAR": (0, 255, 255),     # Yellow
    "NONE": (160, 160, 160),    # Neutral Gray
}


def run_gesture_recognition_test(device_index: int = 0) -> bool:
    print(f"[GestureTest] Initializing MediaPipe HandLandmarker...", flush=True)
    model_path = get_model_path()

    base_options = BaseOptions(model_asset_path=model_path)
    options = HandLandmarkerOptions(
        base_options=base_options,
        num_hands=1,  # Single primary hand for Air Canvas gestures
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    landmarker = HandLandmarker.create_from_options(options)

    print(f"[GestureTest] Opening camera at index {device_index}...", flush=True)
    cap = cv2.VideoCapture(device_index, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(device_index)

    if not cap.isOpened():
        print(f"[GestureTest] ERROR: Could not open webcam at index {device_index}.", flush=True)
        landmarker.close()
        return False

    ret, test_frame = cap.read()
    if not ret or test_frame is None:
        print("[GestureTest] ERROR: Camera opened but failed to capture a frame.", flush=True)
        cap.release()
        landmarker.close()
        return False

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"[GestureTest] Camera initialized: {width}x{height}", flush=True)
    print("[GestureTest] Ready. Press 'Q' or ESC to exit.", flush=True)

    window_title = "Air Canvas - Gesture Recognition Test"
    cv2.namedWindow(window_title, cv2.WINDOW_NORMAL)

    stabilizer = GestureStabilizer(required_consecutive=4)
    last_trigger_text = ""
    last_trigger_time = 0.0

    hand_landmarks_style = drawing_styles.get_default_hand_landmarks_style()
    hand_connections_style = drawing_styles.get_default_hand_connections_style()

    try:
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                print("[GestureTest] Warning: Dropped or empty video frame.", flush=True)
                break

            # 1. Mirror horizontally for natural selfie view
            frame = cv2.flip(frame, 1)

            # 2. RGB conversion for MediaPipe
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

            # 3. Detect hand landmarks
            result = landmarker.detect(mp_image)

            raw_gesture = "NONE"
            finger_states = {}

            if result.hand_landmarks and len(result.hand_landmarks) > 0:
                landmarks = result.hand_landmarks[0]

                # Draw skeletal landmarks
                drawing_utils.draw_landmarks(
                    frame,
                    landmarks,
                    HandLandmarksConnections.HAND_CONNECTIONS,
                    hand_landmarks_style,
                    hand_connections_style,
                )

                raw_gesture, finger_states = classify_gesture(landmarks)

            # 4. Stabilize gesture across frames
            stable_gesture, transition = stabilizer.update(raw_gesture)

            if transition:
                print(f"[Gesture] Transition: {stabilizer.prev_stable_gesture} -> {stable_gesture}", flush=True)
                # Check for one-shot action triggers
                if stable_gesture in ("UNDO", "REDO", "CLEAR"):
                    last_trigger_text = f"ONE-SHOT EVENT TRIGGERED: {stable_gesture}"
                    last_trigger_time = time.time()
                    print(f"[Action] {last_trigger_text}", flush=True)

            # 5. Render On-Screen HUD
            # Background header banner
            cv2.rectangle(frame, (10, 10), (width - 10, 115), (20, 20, 20), -1)
            cv2.rectangle(frame, (10, 10), (width - 10, 115), (70, 70, 70), 1)

            # Recognized Gesture Display
            gesture_color = GESTURE_COLORS.get(stable_gesture, (200, 200, 200))
            cv2.putText(
                frame,
                f"GESTURE: {stable_gesture}",
                (25, 50),
                cv2.FONT_HERSHEY_DUPLEX,
                1.1,
                gesture_color,
                2,
                cv2.LINE_AA,
            )

            # Status subtitle: raw candidate & hand detection state
            if not result.hand_landmarks:
                status_desc = "No Hand Detected"
                status_col = (120, 120, 120)
            else:
                status_desc = f"Tracking Hand | Raw: {raw_gesture} ({stabilizer.candidate_count}/4 frames)"
                status_col = (0, 220, 220)

            cv2.putText(
                frame,
                status_desc,
                (25, 78),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                status_col,
                1,
                cv2.LINE_AA,
            )

            # Finger State Breakdown
            if finger_states:
                t_str = finger_states.get("thumb", "NEUTRAL")
                i_str = "UP" if finger_states.get("index") else "DN"
                m_str = "UP" if finger_states.get("middle") else "DN"
                r_str = "UP" if finger_states.get("ring") else "DN"
                p_str = "UP" if finger_states.get("pinky") else "DN"
                finger_summary = f"Thumb: {t_str} | Index: {i_str} | Middle: {m_str} | Ring: {r_str} | Pinky: {p_str}"
            else:
                finger_summary = "Fingers: None"

            cv2.putText(
                frame,
                finger_summary,
                (25, 102),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (180, 180, 180),
                1,
                cv2.LINE_AA,
            )

            # Flash one-shot event banner if recently triggered (< 1.5s)
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

            # Exit Instructions
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
            if key in (ord('q'), ord('Q'), 27):  # 'q', 'Q', or ESC
                print("[GestureTest] Exit key pressed by user.", flush=True)
                break

            if cv2.getWindowProperty(window_title, cv2.WND_PROP_VISIBLE) < 1:
                print("[GestureTest] Window closed by user.", flush=True)
                break

    finally:
        cap.release()
        cv2.destroyAllWindows()
        landmarker.close()
        print("[GestureTest] Camera and resources released cleanly.", flush=True)

    return True


if __name__ == "__main__":
    success = run_gesture_recognition_test(0)
    sys.exit(0 if success else 1)

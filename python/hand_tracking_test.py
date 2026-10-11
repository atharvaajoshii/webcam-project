"""
Air Canvas - Phase 8B Step 2: MediaPipe Hand Landmark Detection

Detects 21 hand landmarks and renders connections over a mirrored webcam feed.
Uses MediaPipe Tasks Vision API (HandLandmarker) with the float16 model bundle.
Exits cleanly on 'Q' or ESC.
"""

import os
import sys
import urllib.request
import cv2
import mediapipe as mp

# MediaPipe Tasks Vision shortcuts
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
        print(f"[HandTracking] Model file not found. Downloading to {model_path}...", flush=True)
        req = urllib.request.Request(MODEL_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as resp, open(model_path, "wb") as f:
            f.write(resp.read())
        print(f"[HandTracking] Downloaded model ({os.path.getsize(model_path)} bytes).", flush=True)
    return model_path


def run_hand_tracking_test(device_index: int = 0) -> bool:
    print(f"[HandTracking] Initializing MediaPipe HandLandmarker...", flush=True)
    model_path = get_model_path()

    base_options = BaseOptions(model_asset_path=model_path)
    options = HandLandmarkerOptions(
        base_options=base_options,
        num_hands=2,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    landmarker = HandLandmarker.create_from_options(options)

    print(f"[HandTracking] Opening camera at index {device_index}...", flush=True)
    cap = cv2.VideoCapture(device_index, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(device_index)

    if not cap.isOpened():
        print(f"[HandTracking] ERROR: Could not open webcam at index {device_index}.", flush=True)
        print("[HandTracking] Please verify that your camera is connected and not in use.", flush=True)
        landmarker.close()
        return False

    ret, test_frame = cap.read()
    if not ret or test_frame is None:
        print("[HandTracking] ERROR: Camera opened but failed to capture a frame.", flush=True)
        cap.release()
        landmarker.close()
        return False

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"[HandTracking] Camera initialized successfully: {width}x{height}", flush=True)
    print("[HandTracking] Displaying window. Press 'Q' or ESC to exit.", flush=True)

    window_title = "Air Canvas - Hand Tracking Test"
    cv2.namedWindow(window_title, cv2.WINDOW_NORMAL)

    hand_landmarks_style = drawing_styles.get_default_hand_landmarks_style()
    hand_connections_style = drawing_styles.get_default_hand_connections_style()

    try:
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                print("[HandTracking] Warning: Dropped or empty frame.", flush=True)
                break

            # 1. Mirror horizontally for natural selfie-view air canvas
            frame = cv2.flip(frame, 1)

            # 2. Convert BGR to RGB for MediaPipe
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

            # 3. Detect hand landmarks
            result = landmarker.detect(mp_image)

            hand_count = len(result.hand_landmarks) if result.hand_landmarks else 0
            if hand_count > 0:
                status_text = f"Hand Detected ({hand_count} hand{'s' if hand_count > 1 else ''})"
                status_color = (0, 255, 0)  # Green

                # Draw 21 landmarks and skeletal connections for each detected hand
                for hand_landmarks in result.hand_landmarks:
                    drawing_utils.draw_landmarks(
                        frame,
                        hand_landmarks,
                        HandLandmarksConnections.HAND_CONNECTIONS,
                        hand_landmarks_style,
                        hand_connections_style,
                    )
            else:
                status_text = "No Hand Detected"
                status_color = (0, 0, 255)  # Red

            # 4. Render HUD overlays
            cv2.putText(
                frame,
                "Air Canvas - Hand Tracking Test",
                (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.75,
                (255, 255, 255),
                2,
                cv2.LINE_AA,
            )
            cv2.putText(
                frame,
                f"Status: {status_text}",
                (20, 70),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.65,
                status_color,
                2,
                cv2.LINE_AA,
            )
            cv2.putText(
                frame,
                "Press 'Q' or ESC to exit",
                (20, 105),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.55,
                (200, 200, 200),
                1,
                cv2.LINE_AA,
            )

            cv2.imshow(window_title, frame)

            # 5. Handle key press / exit
            key = cv2.waitKey(1) & 0xFF
            if key in (ord('q'), ord('Q'), 27):  # 'q', 'Q', or ESC
                print("[HandTracking] Exit requested by user.", flush=True)
                break

            if cv2.getWindowProperty(window_title, cv2.WND_PROP_VISIBLE) < 1:
                print("[HandTracking] Window closed by user.", flush=True)
                break

    finally:
        cap.release()
        cv2.destroyAllWindows()
        landmarker.close()
        print("[HandTracking] Resources released and windows closed cleanly.", flush=True)

    return True


if __name__ == "__main__":
    success = run_hand_tracking_test(0)
    sys.exit(0 if success else 1)

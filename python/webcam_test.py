"""
Air Canvas - Phase 8B Step 1: Webcam Feed Verification

Opens the default camera (index 0), renders a live preview with OpenCV,
and exits cleanly when 'q' or ESC is pressed.
"""

import sys
import cv2


def run_webcam_test(device_index: int = 0) -> bool:
    print(f"[WebcamTest] Attempting to open webcam at index {device_index}...", flush=True)

    # On Windows, cv2.CAP_DSHOW provides fast, reliable DirectShow initialization
    cap = cv2.VideoCapture(device_index, cv2.CAP_DSHOW)
    if not cap.isOpened():
        cap = cv2.VideoCapture(device_index)

    if not cap.isOpened():
        print(f"[WebcamTest] ERROR: Could not open webcam at index {device_index}.", flush=True)
        print("[WebcamTest] Please verify that a camera is connected and not in use by another application.", flush=True)
        return False

    # Confirm frame capture
    ret, test_frame = cap.read()
    if not ret or test_frame is None:
        print("[WebcamTest] ERROR: Camera opened but failed to capture a frame.", flush=True)
        cap.release()
        return False

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    print(f"[WebcamTest] Camera initialized successfully: {width}x{height} @ {fps:.1f} FPS", flush=True)
    print("[WebcamTest] Displaying window. Press 'Q' or ESC to exit.", flush=True)

    window_title = "Air Canvas - Webcam Test"
    cv2.namedWindow(window_title, cv2.WINDOW_NORMAL)

    try:
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                print("[WebcamTest] Warning: Dropped or empty video frame.", flush=True)
                break

            # Mirror horizontally for natural air-canvas interaction
            frame = cv2.flip(frame, 1)

            # Draw HUD instruction overlay
            cv2.putText(
                frame,
                "Air Canvas - Webcam Test",
                (20, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 255, 0),
                2,
                cv2.LINE_AA,
            )
            cv2.putText(
                frame,
                "Press 'Q' or ESC to exit",
                (20, 80),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                (255, 255, 255),
                1,
                cv2.LINE_AA,
            )

            cv2.imshow(window_title, frame)

            key = cv2.waitKey(1) & 0xFF
            if key in (ord('q'), ord('Q'), 27):  # 'q', 'Q', or ESC
                print("[WebcamTest] Exit key pressed by user.", flush=True)
                break

            # Break if window was closed via OS 'X' button
            if cv2.getWindowProperty(window_title, cv2.WND_PROP_VISIBLE) < 1:
                print("[WebcamTest] Window closed by user.", flush=True)
                break

    finally:
        cap.release()
        cv2.destroyAllWindows()
        print("[WebcamTest] Camera released and windows closed cleanly.", flush=True)

    return True


if __name__ == "__main__":
    success = run_webcam_test(0)
    sys.exit(0 if success else 1)

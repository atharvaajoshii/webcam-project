# Air Canvas — Project Log & Architecture Reference

## 1. Project Overview
Air Canvas is a contactless, real-time air-drawing computer vision application. A user draws in the air using hand gestures captured by a webcam, and the strokes are rendered on a virtual canvas.

The system uses a **decoupled two-window architecture**:
1. **Authoritative Java Paint Window**: A desktop Swing/AWT application hosting the canvas, toolbar (color palette, brush thickness slider, clear/undo/redo controls), and status bar. It maintains stroke history and canvas state using custom data structures.
2. **Python Vision & Tracking Window**: An OpenCV/MediaPipe application capturing webcam video, detecting 21 hand landmarks, classifying finger poses into gestures, and sending normalized coordinate/gesture events over a local TCP connection.

---

## 2. Architecture & Tech Stack

```
+-----------------------------------+               +--------------------------------------+
|       Python Vision Client        |               |          Java Paint Server           |
|  - OpenCV Camera Capture (640x480)|               |  - GestureServer (TCP Port 5000)     |
|  - MediaPipe HandLandmarker       | ---(TCP Socket)-->  - GestureController (EDT bridge)   |
|  - Gesture Classifier             |   127.0.0.1:5000  |  - DrawingEngine (stroke coordinator)|
|  - tcp_client.py                  |               |  - CanvasPanel & PaintWindow (Swing) |
+-----------------------------------+               +--------------------------------------+
```

- **Java**: Java 17+ (Swing, AWT, `java.net.ServerSocket`, zero Java Collections Framework in model layer).
- **Python**: Python 3.11.9 (`opencv-python` 5.0.0.93, `mediapipe` 1.1.0 Tasks API, `numpy` 2.4.6).
- **Inter-Process Communication**: Local TCP socket on `127.0.0.1:5000`, single-line newline-delimited UTF-8 protocol (`GESTURE,NAME,X,Y\n`).
- **Coordinate Space**: Normalized integer grid `0` to `1000` on both axes, dynamically mapped to `CanvasPanel` pixel bounds in Java.

---

## 3. User Requirements & Instructions

### Explicit User Requirements & Constraints
- **Zero Java Collections Framework**: Project core data structures cannot use `java.util.List`, `ArrayList`, `LinkedList`, `Stack`, `Queue`, or other built-in collection classes. All storage must use custom-built data structures.
- **Strict Undo Limit**: Undo history is strictly capped at **5 strokes**. When capacity is reached, the oldest stroke is evicted.
- **Redo Stack**: LIFO stack; any new stroke clears redo history.
- **Mirrored Camera View**: Webcam feed must be mirrored horizontally (selfie view) so physical hand movement matches screen movement.
- **Two Separate Windows**: Java Paint window and Python tracking window must remain distinct applications connected over TCP.
- **Thread Safety**: All incoming socket events must be marshaled to Swing's Event Dispatch Thread (EDT) via `SwingUtilities.invokeLater`.
- **Git Hygiene**: No commits or pushes without explicit user review and command.

### Implementation Choices (Agent Design)
- **Coordinate Smoothing**: `CircularBuffer` rolling average applied to incoming `(x, y)` coordinates to eliminate webcam jitter.
- **One-Shot Trigger Handling**: State machine in `GestureController` using rising-edge transitions (`prevGesture != currentGesture`) for `UNDO`, `REDO`, and `CLEAR` to prevent repeat triggering while holding a pose.
- **Direct Link Traversal Rendering**: `CanvasPanel.paintComponent` renders directly by traversing `StrokeList` nodes to avoid any intermediate array allocations during paint cycles.

---

## 4. Gesture Mapping

| Gesture Name | Hand Pose / Finger Configuration | Action | Trigger Type |
|---|---|---|---|
| `DRAW` | Index finger extended; middle, ring, pinky folded | Draws continuous stroke at index fingertip `(X, Y)` | Continuous |
| `MOVE` | Index + Middle fingers extended ("peace" sign); ring, pinky folded | Repositions cursor without drawing; ends active stroke | Continuous |
| `UNDO` | Fist with thumb pointing downward | Undoes most recent stroke (up to 5 history limit) | One-shot (rising edge) |
| `REDO` | Thumbs-up (thumb pointing upward, other 4 fingers folded) | Redoes most recently undone stroke | One-shot (rising edge) |
| `CLEAR` | Open palm with all four fingers extended | Clears canvas and resets undo/redo history | One-shot (rising edge) |
| `NONE` | Hand absent, neutral fist, or unrecognized/ambiguous pose | Halts active stroke; cursor inactive | Continuous |

**One-Shot Behavior**: Trigger actions (`UNDO`, `REDO`, `CLEAR`) fire exactly once on the rising edge when first recognized. The user must transition through another state (e.g. `MOVE` or `NONE`) before triggering the action again.

**Stability Mechanism**: Raw per-frame classifications pass through `GestureStabilizer`, requiring 4 consecutive identical frames before updating the recognized state, preventing single-frame camera noise and transient pose flicker.


---

## 5. Data Structures
All data structures are located in `src/main/java/aircanvas/ds/` without JCF dependencies:

1. **`PointNode`**: Singly-linked list node storing integer coordinates `(x, y)` and a pointer to the next point.
2. **`Stroke`**: Singly-linked list of `PointNode` instances with stroke metadata (`Color color`, `int thickness`, point count).
3. **`StrokeList`**: Doubly-linked list with head and tail pointers storing the active, visible strokes rendered on the canvas.
4. **`CircularBuffer`**: Fixed-size circular array for moving-average smoothing of raw coordinate input.
5. **`CircularArrayQueue`**: Fixed-size FIFO queue for event buffering between server intake and engine consumption.
6. **`UndoStack`**: Circular-array LIFO stack with fixed capacity of 5. Overwrites oldest stroke on overflow.
7. **`RedoStack`**: Dynamic singly-linked node-based LIFO stack. Unbounded; reset whenever a new stroke is initiated.

---

## 6. Setup & Environment

- **Root Directory**: `C:\Users\Atharva Joshi\Projects\WebcamProject`
- **Git Branch**: `dev` (Remote: `origin`)
- **Python Virtual Environment**: `.\.venv\` (Python 3.11.9)
  - Interpreter: `.\.venv\Scripts\python.exe`
  - Key Libraries: `opencv-python` 5.0.0.93, `mediapipe` 1.1.0, `numpy` 2.4.6.
- **MediaPipe Task Model**: `python/hand_landmarker.task` (MediaPipe Tasks Vision float16 bundle).
- **Java Toolchain**: JDK 17+ (AWT, Swing, Networking).

---

## 7. Commands Reference

| Command | Purpose / Tested Behavior |
|---|---|
| `.\.venv\Scripts\python.exe python\webcam_test.py` | Opens default webcam (index 0) with OpenCV, renders mirrored 640x480 feed, exits cleanly on 'Q' or ESC. |
| `.\.venv\Scripts\python.exe python\hand_tracking_test.py` | Detects hand landmarks with MediaPipe Tasks and draws 21 joints + connections on mirrored webcam feed. |
| `.\.venv\Scripts\python.exe python\gesture_recognition_test.py` | Detects hand landmarks, classifies 6 gesture states (DRAW, MOVE, UNDO, REDO, CLEAR, NONE), applies multi-frame stabilization, and tracks one-shot transitions on mirrored webcam feed. |
| `.\.venv\Scripts\python.exe python\air_canvas_client.py` | Full integrated client: captures webcam, detects hand, classifies gestures, and streams normalized [0, 1000] events over TCP to Java GestureServer with one-shot filtering. |
| `.\.venv\Scripts\python.exe -m unittest python\test_air_canvas_client.py` | Runs 7 unit tests validating protocol handler, coordinate normalization, one-shot dispatching, and connection refusal. |
| `.\.venv\Scripts\python.exe -m unittest python\test_gesture_recognition.py` | Runs 8 unit tests validating gesture classification rules and stabilizer hysteresis. |
| `.\.venv\Scripts\python.exe -m unittest python\test_tcp_client.py` | Runs 9 unit tests for Python TCP client connection, protocol formatting, and socket error handling. |
| `.\.venv\Scripts\python.exe python\test_java_integration.py` | Runs live end-to-end integration test connecting Python client to Java `GestureServer`. |
| `git status --short` | Checks modified and untracked repository files. |

---

## 8. Implementation Phases

| Phase | Description | Status | Verification & Test Results |
|---|---|---|---|
| **Phase 1–5** | Custom Data Structures (`aircanvas.ds`) | Completed & Pushed (`9ae0350`) | 203/203 assertions passing across 5 suites |
| **Phase 6A** | Core Drawing Engine (`aircanvas.engine.DrawingEngine`) | Completed | 70/70 assertions passing across suite |
| **Phase 6B** | Swing Canvas UI (`PaintWindow`, `CanvasPanel`, resize fix) | Completed & Pushed (`db5daf0`) | 11/11 assertions passing; window resize flicker resolved |
| **Phase 7A** | Java TCP Server (`GestureServer`) | Completed | 56/56 assertions passing across suite |
| **Phase 7B** | Python TCP Client (`tcp_client.py`) | Completed | 9/9 Python unit tests passing |
| **Phase 8A** | Java Server + UI Integration (`GestureController`) | Completed | 371/371 Java assertions passing (9 suites); 10/10 Python tests passing |
| **Phase 8B (Step 1)** | OpenCV Webcam Feed Verification (`python/webcam_test.py`) | Completed | Camera index 0 opened at 640x480; live video confirmed |
| **Phase 8B (Step 2)** | MediaPipe Hand Landmark Detection (`python/hand_tracking_test.py`) | Completed | HandLandmarker operational with 21 landmarks & skeletal connections drawn on mirrored webcam feed |
| **Phase 8B (Step 3)** | Finger Classification & Gesture Recognition (`python/gesture_recognition_test.py`) | Completed | 8/8 unit tests passing; live HUD with 4-frame stabilization running |
| **Phase 8B (Step 4A)** | Integrate Python Gesture Recognition with TCP (`python/air_canvas_client.py`) | Completed | 30/30 Python unit tests passing; 379/379 Java assertions passing. Fixed top-left diagonal lines and tangled stroke delays. |
| **Phase 8B (Step 4B)** | Full Live System Verification (Concurrent Paint Window + Python Client) | Pending | Scheduled next |

---

## 9. Decisions & Constraints

1. **Undo History Limit**: Strictly 5 strokes. Handled by circular buffer overwriting head index when count reaches 5.
2. **Protocol Format**: `GESTURE,<NAME>,<X>,<Y>\n` where coordinates are integers in `[0, 1000]`. Example: `GESTURE,DRAW,520,310\n`.
3. **EDT Isolation**: Socket networking runs on dedicated daemon worker thread; events dispatched to EDT via `SwingUtilities.invokeLater`.
4. **MediaPipe Version Compatibility**: MediaPipe 1.1.0 uses MediaPipe Tasks (`mediapipe.tasks.python.vision.HandLandmarker`) rather than legacy `mediapipe.solutions.hands`. Model asset `hand_landmarker.task` is stored locally in `python/`.
5. **Instant Pen-Up on Transition**: Transitioning out of DRAW (to MOVE or NONE) executes with zero frame delay to prevent drag artifacts. DRAW activation uses 2-frame confirmation; destructive actions retain 4-frame hysteresis.
6. **Sentinel & Zero Bounds Guard**: Java and Python reject/suppress `DRAW,0,0` coordinates; Java ignores events if canvas width/height <= 0.
7. **Coordinate Smoothing**: `CircularBuffer<Integer>` (capacity 4) applied to X and Y coordinates in `GestureController` to smooth webcam jitter.

---

## 10. Known Issues & Manual Checks

- **Manual Webcam Verification**: Live video feed and MediaPipe landmark tracking requires a physical webcam connected to the host machine.
- **Port 5000 Conflict**: If a background Java server instance is left running, port 5000 may report `BindException`; automated test suites use ephemeral ports (`0`) to prevent port collisions.

---

## 11. Change Log

- **2026-10-11**:
  - Completed Phase 1–6B: Custom data structures, `DrawingEngine`, `CanvasPanel`, and `PaintWindow` committed and pushed to `dev` branch (`db5daf00c4a84f128f685dbfb155e043076f4049`).
  - Completed Phase 7A & 7B: Created `GestureServer.java`, `GestureServerTest.java`, `python/tcp_client.py`, and `python/test_tcp_client.py`.
  - Completed Phase 8A: Implemented `GestureController.java`, integrated auto-starting TCP server into `PaintWindow.java`, verified end-to-end integration test (`python/test_java_integration.py`). (371/371 Java tests passing).
  - Completed Phase 8B Step 1: Created `python/webcam_test.py` with OpenCV capture, mirror view, and clean teardown. Verified camera index 0 opens at 640x480.
  - Created `PROJECT_LOG.md` persistent documentation file.
  - Completed Phase 8B Step 2: Created `python/hand_tracking_test.py` utilizing MediaPipe Tasks Vision (`HandLandmarker`) and `python/hand_landmarker.task`. Live camera feed opens, mirrors frame, detects hand, renders 21 landmarks + connections, and displays real-time detection status. Exits cleanly on 'Q'/ESC.
  - Completed Phase 8B Step 3: Created `python/gesture_recognition_test.py` and `python/test_gesture_recognition.py`. Implemented geometric classification for 6 states (DRAW, MOVE, UNDO, REDO, CLEAR, NONE), 4-frame hysteresis stabilizer, console transition logging, and one-shot trigger banners. 8/8 automated unit tests pass.
  - Completed Phase 8B Step 4A: Created `python/air_canvas_client.py` and `python/test_air_canvas_client.py`. Integrated live MediaPipe hand tracking with TCP streaming to Java server on `127.0.0.1:5000`. Implemented `AirCanvasProtocolHandler` with [0, 1000] index fingertip coordinates, continuous DRAW/MOVE, one-shot gate for UNDO/REDO/CLEAR, and graceful connection refusal handling.
  - Resolved Phase 8B Step 4A Drawing Discrepancies:
    - Root Cause 1 (Diagonal lines to (0,0)): When hand tracking flickered during DRAW, `norm_x, norm_y` defaulted to `(0, 0)` while the multi-frame stabilizer held `DRAW`, sending `DRAW,0,0` over TCP.
    - Root Cause 2 (Tangled strokes): The 4-frame delay when transitioning out of `DRAW` dragged strokes across the screen while the user was curling fingers or moving to `MOVE`.
    - Fix 1: Added `AirCanvasStabilizer` with instant pen-up (0 frame delay on leaving DRAW or losing hand) and 2-frame fast DRAW activation.
    - Fix 2: Added coordinate guards in `AirCanvasProtocolHandler` suppressing `DRAW` when `has_hand=False` or `(x, y) == (0, 0)`.
    - Fix 3: In Java `GestureController.java`, added sentinel (0, 0) rejection, zero-sized canvas protection, and wired `CircularBuffer<Integer>` moving-average trajectory smoothing.
    - Validation: 379/379 Java assertions passing (all 9 suites), 30/30 Python tests passing (all suites).
  - Completed Phase 8B Step 4B: Fast-Motion Tracking & Adaptive Smoothing:
    - Root Cause Analysis:
      - Primary cause of discontinuous strokes & gaps: Rapid hand motion causes camera motion blur, dropping MediaPipe hand detection for 1 frame (`has_hand=False`). Previously, `AirCanvasStabilizer` triggered immediate pen-up on frame 1 of dropped hand, emitting `NONE, 0, 0` and committing the stroke. Upon hand reacquisition 33ms later, the 2-frame stabilization requirement caused an additional delay, splitting a single rapid stroke into fragmented strokes with noticeable gaps.
      - Primary cause of lag & angular/cut corners: Java `GestureController.java` used a fixed-window 4-point simple moving average (`CircularBuffer<Integer>(4)`). At fast speeds (50-120 px/frame), this fixed 4-frame window produced a ~2-frame (~66ms) group delay, lagging 50-100+ pixels behind the fingertip and severely cutting corners/flattening curves into angular polygons.
      - Stationary landmark jitter: Natural camera sensor and landmark predictor noise causes 2-5 pixel jitter when hand is still, which requires strong smoothing when stationary but zero smoothing when moving quickly.
    - Implementation Details:
      - Java Adaptive Smoothing (`GestureController.java`): Calculates displacement $\Delta d = \sqrt{\Delta x^2 + \Delta y^2}$ between successive points.
        - High-speed motion ($\Delta d \ge 28$ px): Clears stale pre-motion history from `CircularBuffer` and uses instantaneous raw coordinate ($k = 1$). Zero lag, sharp corners preserved with 100% fidelity.
        - Moderate motion ($10 \le \Delta d < 28$ px): Averages the newest 2 points ($k = 2$) for responsive line smoothing.
        - Low-speed / stationary ($\Delta d < 10$ px): Uses the full 4-sample `CircularBuffer` window ($k = 4$) to suppress webcam tremor and sensor jitter.
      - Python Tracking Dropout Tolerance (`AirCanvasStabilizer` in `air_canvas_client.py`):
        - Tolerates brief camera detection loss (`has_hand=False`) up to 2 consecutive frames (~60ms) during active `DRAW`.
        - `AirCanvasProtocolHandler` suppresses output during grace period: no dummy `(0, 0)` is sent, and Java stroke remains open.
        - When hand reacquires within tolerance and within 35% normalized screen distance, drawing resumes seamlessly in the same stroke.
        - If hand reacquires across the screen (> 35% distance jump), prior stroke is cleanly disconnected with `NONE` first to prevent cross-canvas diagonal lines.
        - Prolonged tracking loss ($\ge 3$ frames) cleanly transitions to `NONE` and finishes the stroke.
      - Instant Pen-Up Preserved:
        - When hand is present (`has_hand=True`), intentional transitions from `DRAW` to `MOVE` or `NONE` trigger immediate pen-up on frame 1 (0-frame delay), ensuring consecutive strokes are never joined and no trailing tails occur.
      - One-Shot Actions Preserved:
        - `UNDO`, `REDO`, and `CLEAR` maintain 4-frame hysteresis and single-trigger firing.
    - Test Results:
      - Java Tests: 389/389 passing assertions across 9 test suites (+10 new assertions in `GestureControllerTest.java` verifying jitter suppression, high-speed tracking, corner preservation, and stroke isolation).
      - Python Tests: 34/34 passing tests across 4 test suites (+4 new tests in `test_air_canvas_client.py` verifying brief tracking loss tolerance, cross-canvas disconnect, prolonged loss termination, and intentional pen-up).
    - Status:
      - Part A: Checkpoint `135fbce` pushed to `origin/dev`.
      - Part B: Adaptive smoothing and fast-motion tracking changes implemented and fully verified locally. Not committed or pushed per instructions.
  - Completed Live Fingertip Pointer Implementation:
    - Live Pointer Behavior:
      - Added real-time circular pointer overlay in `CanvasPanel.java` with vibrant red core (`rgb(230, 30, 30)`), white inner ring, and dark outline (radius 7px) for high contrast on white canvas and across all stroke colors.
      - Decoupled pointer state (`pointerVisible`, `pointerNormX`, `pointerNormY`) from `DrawingEngine`, stroke list, and undo/redo history.
      - During `MOVE`: updates pointer position continuously without appending any stroke points or modifying undo/redo stacks.
      - During `DRAW`: keeps pointer visible at current index fingertip position while streaming smoothed stroke points.
      - During `UNDO` / `REDO` / `CLEAR`: preserves existing one-shot functionality and displays pointer at fingertip when hand coordinates are available.
      - Upon `NONE`, tracking loss, or dummy sentinel `(0, 0)` coordinates: hides pointer immediately; never renders pointer at `(0, 0)` or leaves stale pointer artifacts.
    - Clean PNG Export:
      - Implemented `exportToImage()` and `exportToPNG(File)` on `CanvasPanel.java` rendering background and visible strokes directly without the transient pointer overlay. Verified in automated tests.
    - Responsive Coordinate Alignment & Resizing:
      - Pointer uses identical normalized mapping `[0, 1000]` mapped to dynamic panel dimensions `(getWidth(), getHeight())`. Resizing window keeps pointer and drawing strokes aligned.
    - Test Results:
      - Java Tests: 419/419 assertions passing across 9 test suites (+30 new assertions in `GestureControllerTest.java` and `PaintWindowTest.java` verifying MOVE pointer, DRAW pointer, NONE hide, tracking loss hide, resize alignment, and PNG export exclusion).
      - Python Tests: 34/34 passing tests across 4 test suites (100% pass rate).
    - Status:
      - All changes verified locally. Documentation added (README.md). Ready for final push.


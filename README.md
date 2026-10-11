# Air Canvas: Real-Time Gesture Drawing Application

Air Canvas is a cross-platform, multi-process computer vision drawing system that lets you draw in the air using hand gestures tracked through your webcam.

The application pairs a Python client (powered by **OpenCV** and **MediaPipe Tasks Vision**) with an authoritative **Java Swing/AWT** desktop application communicating over a high-speed, local **TCP socket protocol** on port `5000`.

---

## Architecture Overview

Air Canvas operates as a decoupled two-window distributed system:

```
+------------------------------------+          TCP Socket (localhost:5000)          +------------------------------------+
|           PYTHON CLIENT            |  ------------------------------------------->  |            JAVA SERVER             |
|                                    |    Protocol: GESTURE,<NAME>,<X>,<Y>\n         |                                    |
| - OpenCV Video Capture (Webcam)    |                                                | - GestureServer (Background Thread)|
| - MediaPipe Tasks Vision (21 3D pt)|                                                | - GestureController (EDT Dispatch) |
| - Geometric Gesture Classifier     |                                                | - Live Fingertip Pointer Overlay   |
| - 4-Frame Hysteresis Stabilizer    |                                                | - Velocity-Adaptive Smoothing      |
| - Tracking Loss Dropout Tolerance  |                                                | - Authoritative DrawingEngine      |
| - On-Screen HUD & Visualizer       |                                                | - Zero Java Collections Framework  |
+------------------------------------+                                                +------------------------------------+
```

### Key Highlights
- **Authoritative Canvas in Java:** The Java Swing application owns the master canvas, drawing history, and undo/redo stacks. Python acts as a pure sensing and input device.
- **Strict Custom Data Structures:** Developed with **zero Java Collections Framework (`java.util.*`) classes**. All linked lists, buffers, queues, and undo/redo stacks are built from scratch.
- **Real-Time Live Pointer:** A high-contrast circular pointer overlay tracks your fingertip in real-time during both `MOVE` and `DRAW`, while being strictly excluded from clean PNG image exports.
- **Velocity-Adaptive Smoothing:** Smooths coordinate tremors when drawing slowly or holding still, and adaptively shrinks the smoothing window at high velocity to eliminate lag and corner cutting.
- **Brief Tracking-Loss Recovery:** Tolerates transient 1–2 frame camera motion-blur dropouts without breaking strokes or creating stray lines to `(0, 0)`.
- **Instant Pen-Up:** Immediate 0-frame response when switching to `MOVE` or `NONE`, preventing trailing stroke tails or hooks.

---

## Hand Gesture Controls

| Gesture | Pose Configuration | Action Description |
|:---:|:---|:---|
| **DRAW** | Index finger extended; middle, ring, pinky folded | Draws continuous strokes at the fingertip. Live pointer visible. |
| **MOVE** | Index and middle fingers extended (V / Peace sign); ring and pinky folded | Moves the live pointer across the canvas without drawing. |
| **UNDO** | Closed fist with thumb pointing downward | Reverts the most recent stroke (one-shot trigger, max 5 undos). |
| **REDO** | Closed fist with thumb pointing upward | Restores the most recently undone stroke (one-shot trigger). |
| **CLEAR** | Open palm with all four fingers extended upright | Clears the canvas and resets undo/redo history (one-shot trigger). |
| **NONE** | Neutral hand, closed hand, or hand out of frame | Hides the live pointer overlay and cleanly completes active strokes. |

---

## Custom Java Data Structures (Zero JCF)

To satisfy strict foundational systems programming constraints, no Java Collections Framework classes (`ArrayList`, `LinkedList`, `Stack`, `Queue`, `HashMap`, etc.) are used:

| Data Structure | Implementation Details | Purpose in Air Canvas |
|:---|:---|:---|
| **`PointNode`** | Singly-linked list node with `int x`, `int y`, and `PointNode next` | Stores individual stroke coordinate vertices. |
| **`Stroke`** | Head and tail `PointNode` pointers, `Color`, `int thickness`, point count | Encapsulates a complete stroke with $O(1)$ point insertion and empty detection. |
| **`StrokeList`** | Doubly-linked list with head and tail sentinel nodes | Manages visible strokes on the canvas with $O(1)$ append, remove, and restore without copying. |
| **`CircularArrayQueue`** | Array-based circular queue with `head`, `tail`, `size`, `capacity` | Provides circular FIFO event buffering and overflow protection. |
| **`UndoStack`** | Array-based bounded stack capped at 5 elements | Enforces the strict 5-stroke undo history limit with $O(1)$ push/pop. |
| **`RedoStack`** | Array-based bounded stack | Stores undone strokes for restoration until invalidated by new drawing. |
| **`CircularBuffer`** | Array-based circular buffer with generic element storage | Used in `GestureController` for trajectory smoothing, speed calculation, and flicker reduction. |

---

## Velocity-Adaptive Coordinate Smoothing

Fast air drawing often introduces motion blur, sparse frame intervals, and corner clipping. Air Canvas solves this through speed-weighted filtering:

1. **Euclidean Displacement Calculation:**
   $$\Delta d = \sqrt{(pixelX - lastSmoothX)^2 + (pixelY - lastSmoothY)^2}$$
2. **Dynamic Windowing in `GestureController.java`:**
   - **Fast Motion ($\Delta d \ge 28$ px):** Clears stale pre-motion buffer history and applies window size $k = 1$ (instantaneous raw coordinate). Eliminates lag and preserves sharp corners with 100% fidelity.
   - **Moderate Motion ($10 \le \Delta d < 28$ px):** Averages the newest 2 points ($k = 2$) for responsive line flow.
   - **Slow / Stationary ($\Delta d < 10$ px):** Uses the full 4-sample window ($k = 4$) in `CircularBuffer` to eliminate webcam landmark jitter and sensor tremor.
3. **Cross-Canvas Line Protection:** If hand tracking is interrupted and reacquired across the screen ($> 35\%$ screen distance), the system disconnects the previous stroke before starting a new one, preventing diagonal screen slashes.

---

## Live Fingertip Pointer & Clean PNG Export

- **High-Contrast Design:** 14px diameter circular overlay with a vibrant red center (`#E61E1E`), a crisp white inner boundary, and a subtle dark outline. Highly visible against white canvas and any brush color.
- **Dynamic Resizing Alignment:** Normalized coordinates in $[0, 1000]$ are dynamically scaled to the panel's current pixel dimensions (`getWidth()`, `getHeight()`), keeping the pointer perfectly aligned with strokes before and after window resize.
- **Decoupled PNG Export:** The `CanvasPanel.exportToImage()` and `exportToPNG(File)` methods render background and stroke paths directly, strictly excluding the transient pointer overlay.

---

## Project Structure

```
WebcamProject/
│
├── src/main/java/aircanvas/
│   ├── ds/                              # Custom Data Structures (Zero JCF)
│   │   ├── PointNode.java               # Singly-linked list node for points
│   │   ├── Stroke.java                  # Stroke linked-list model
│   │   ├── StrokeList.java              # Doubly-linked list for canvas strokes
│   │   ├── CircularArrayQueue.java      # Circular FIFO queue
│   │   ├── UndoStack.java               # Bounded 5-element undo stack
│   │   ├── RedoStack.java               # Bounded redo stack
│   │   └── CircularBuffer.java          # Smoothing circular buffer
│   ├── engine/
│   │   ├── DrawingEngine.java           # Authoritative canvas & history coordinator
│   │   └── GestureController.java       # Adaptive smoothing & pointer coordinator
│   ├── network/
│   │   └── GestureServer.java           # Background TCP server on 127.0.0.1:5000
│   └── ui/
│       ├── CanvasPanel.java             # Swing drawing panel & live pointer overlay
│       └── PaintWindow.java             # Main application GUI & toolbar
│
├── src/test/java/aircanvas/             # Java Automated Test Suites (419 assertions)
│   ├── ds/                              # Custom DS unit tests
│   ├── engine/                          # DrawingEngine & GestureController tests
│   ├── network/                         # Protocol & TCP socket integration tests
│   └── ui/                              # PaintWindow & CanvasPanel export tests
│
├── python/                              # Python Vision & Gesture Recognition Client
│   ├── air_canvas_client.py             # Main live webcam client & TCP streaming
│   ├── gesture_recognition_test.py      # Landmark classifier & stabilization rules
│   ├── hand_tracking_test.py            # Standalone MediaPipe diagnostic tool
│   ├── webcam_test.py                   # Standalone camera verification tool
│   ├── tcp_client.py                    # Reusable TCP socket client
│   ├── test_air_canvas_client.py        # Protocol handler & tracking loss unit tests
│   ├── test_gesture_recognition.py      # Classification geometry unit tests
│   ├── test_java_integration.py         # End-to-end Python-to-Java loopback tests
│   └── test_tcp_client.py               # TCP client socket unit tests
│
├── .gitignore                           # Git ignore rules (builds, caches, .venv, *.task)
├── PROJECT_LOG.md                       # Comprehensive project log and architectural records
└── README.md                            # Complete documentation & usage guide
```

---

## Getting Started

### Prerequisites

1. **Java:** JDK 17 or higher
2. **Python:** Python 3.10 or 3.11
3. **Webcam:** Any standard USB or integrated webcam

### 1. Setup Python Virtual Environment

From the project root directory:

```powershell
# Create virtual environment
python -m venv .venv

# Activate virtual environment
.\.venv\Scripts\Activate.ps1

# Install dependencies
pip install opencv-python mediapipe
```

> **Note:** The MediaPipe Hand Landmarker model (`hand_landmarker.task`) will be downloaded automatically on the first run if not already present in the `python/` directory.

### 2. Compile Java Source Files

```powershell
# Compile all source files into out/production/WebcamProject
javac --release 17 -d out/production/WebcamProject $(Get-ChildItem -Recurse -Filter *.java src/main | ForEach-Object { $_.FullName })
```

---

## How to Run Air Canvas

The Java Paint window must be started first so that its background TCP server is listening on `127.0.0.1:5000`.

### Option A: Running from Terminal (Two Windows)

**Terminal 1 — Launch Java Paint Application:**
```powershell
java -cp out/production/WebcamProject aircanvas.ui.PaintWindow
```
*Output: `[Air Canvas] GestureServer listening on 127.0.0.1:5000`*

**Terminal 2 — Launch Python Gesture Recognition Client:**
```powershell
.\.venv\Scripts\python.exe python\air_canvas_client.py
```
*The webcam window will open displaying live video, hand landmarks, gesture HUD, and coordinate tracking.*

### Option B: Running from IntelliJ IDEA
1. Open the project in IntelliJ IDEA.
2. Navigate to `src/main/java/aircanvas/ui/PaintWindow.java`.
3. Right-click and select **Run 'PaintWindow.main()'**.
4. In the IntelliJ terminal or PowerShell, run:
   ```powershell
   .\.venv\Scripts\python.exe python\air_canvas_client.py
   ```

---

## Automated Testing & Verification

The project includes comprehensive test suites across both Java and Python with **100% pass rates**:

### Run All Java Test Suites (419 Assertions)

```powershell
# Compile test files
javac --release 17 -d out/production/WebcamProject $(Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })

# Run all 9 test suites
$classes = @(
  "aircanvas.ds.CircularArrayQueueTest",
  "aircanvas.ds.CircularBufferTest",
  "aircanvas.ds.StrokeAndPointTest",
  "aircanvas.ds.StrokeListTest",
  "aircanvas.ds.UndoRedoTest",
  "aircanvas.engine.DrawingEngineTest",
  "aircanvas.engine.GestureControllerTest",
  "aircanvas.network.GestureServerTest",
  "aircanvas.ui.PaintWindowTest"
);
foreach ($c in $classes) {
  java -cp out/production/WebcamProject $c
}
```

### Run All Python Test Suites (34 Tests)

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s python
```

---

## Communication Protocol Reference

- **Transport:** TCP over IPv4 loopback (`127.0.0.1:5000`)
- **Format:** Single UTF-8 line per event ending with `\n`
- **Syntax:** `GESTURE,<NAME>,<X>,<Y>\n`
- **Coordinate Space:** Normalized integers in range `[0, 1000]` where `(0, 0)` is top-left and `(1000, 1000)` is bottom-right

| Message Example | Interpretation |
|:---|:---|
| `GESTURE,DRAW,450,320` | Draw event at normalized position X=45%, Y=32%. |
| `GESTURE,MOVE,500,200` | Pointer motion event at normalized position X=50%, Y=20%. No drawing. |
| `GESTURE,UNDO,500,500` | One-shot undo event with live hand coordinates for pointer overlay. |
| `GESTURE,REDO,500,500` | One-shot redo event with live hand coordinates for pointer overlay. |
| `GESTURE,CLEAR,500,500`| One-shot clear event with live hand coordinates for pointer overlay. |
| `GESTURE,NONE,0,0` | No hand / neutral pose. Hides pointer and terminates active stroke. |

---

## License

Developed as part of the Air Canvas Computer Vision Project. Open for educational and non-commercial exploration.

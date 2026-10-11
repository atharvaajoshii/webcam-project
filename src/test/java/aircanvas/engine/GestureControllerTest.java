package aircanvas.engine;

import aircanvas.network.GestureServer;

import javax.swing.JPanel;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class GestureControllerTest {
    private static int testsRun = 0;
    private static int testsPassed = 0;

    private static void assertTrue(String name, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("[PASS] " + name);
        } else {
            System.err.println("[FAIL] " + name);
            throw new AssertionError("Failed: " + name);
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        testsRun++;
        boolean match = (expected == null && actual == null) || (expected != null && expected.equals(actual));
        if (match) {
            testsPassed++;
            System.out.println("[PASS] " + name + " (expected=" + expected + ", actual=" + actual + ")");
        } else {
            System.err.println("[FAIL] " + name + " (expected=" + expected + ", actual=" + actual + ")");
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing GestureController Routing & One-Shot Invariants ===");

        DrawingEngine engine = new DrawingEngine();
        JPanel mockCanvas = new JPanel();
        mockCanvas.setSize(1000, 1000);

        final int[] callbacksReceived = {0};
        GestureController controller = new GestureController(engine, mockCanvas, () -> callbacksReceived[0]++);

        // 1. Initial State
        assertEquals("Initial previous gesture is NONE", GestureServer.GESTURE_NONE, controller.getPreviousGesture());
        assertEquals("Visible strokes 0 initially", 0, engine.getVisibleStrokeCount());

        // 2. Continuous DRAW trajectory streaming
        controller.processGesture("DRAW", 100, 200, 1000, 1000);
        assertTrue("Active stroke started on DRAW transition", engine.hasActiveStroke());
        assertEquals("Active stroke has 1 point", 1, engine.getActiveStroke().getPointCount());

        controller.processGesture("DRAW", 150, 250, 1000, 1000);
        assertEquals("Active stroke has 2 points", 2, engine.getActiveStroke().getPointCount());

        controller.processGesture("DRAW", 200, 300, 1000, 1000);
        assertEquals("Active stroke has 3 points", 3, engine.getActiveStroke().getPointCount());
        assertEquals("Visible strokes still 0 before finishing", 0, engine.getVisibleStrokeCount());

        // 3. MOVE finishes active stroke without adding a point
        controller.processGesture("MOVE", 400, 400, 1000, 1000);
        assertTrue("No active stroke after MOVE", !engine.hasActiveStroke());
        assertEquals("Stroke committed to canvas (count: 1)", 1, engine.getVisibleStrokeCount());
        assertEquals("Undo count is 1", 1, engine.getUndoCount());

        // Repeated MOVE does not draw anything
        controller.processGesture("MOVE", 500, 500, 1000, 1000);
        assertEquals("Visible strokes remain 1 on repeated MOVE", 1, engine.getVisibleStrokeCount());

        // 4. One-Shot UNDO behavior
        // First UNDO message: triggers undo
        controller.processGesture("UNDO", 0, 0, 1000, 1000);
        assertEquals("Visible strokes is 0 after first UNDO", 0, engine.getVisibleStrokeCount());
        assertEquals("Redo count is 1 after first UNDO", 1, engine.getRedoCount());

        // Repeated UNDO messages while gesture remains active: MUST NOT TRIGGER AGAIN
        controller.processGesture("UNDO", 0, 0, 1000, 1000);
        controller.processGesture("UNDO", 0, 0, 1000, 1000);
        assertEquals("Visible strokes remain 0 during repeated UNDO", 0, engine.getVisibleStrokeCount());
        assertEquals("Redo count remains 1 (no duplicate undos)", 1, engine.getRedoCount());

        // 5. One-Shot REDO behavior and Re-arming
        // Transition to NONE re-arms gestures
        controller.processGesture("NONE", 0, 0, 1000, 1000);
        assertEquals("Transitioned to NONE", GestureServer.GESTURE_NONE, controller.getPreviousGesture());

        // First REDO message: restores stroke
        controller.processGesture("REDO", 0, 0, 1000, 1000);
        assertEquals("Visible strokes restored to 1 on first REDO", 1, engine.getVisibleStrokeCount());
        assertEquals("Redo count is 0 after REDO", 0, engine.getRedoCount());

        // Repeated REDO messages: MUST NOT TRIGGER AGAIN
        controller.processGesture("REDO", 0, 0, 1000, 1000);
        controller.processGesture("REDO", 0, 0, 1000, 1000);
        assertEquals("Visible strokes remain 1 on repeated REDO", 1, engine.getVisibleStrokeCount());

        // 6. One-Shot CLEAR behavior
        // Draw a second stroke
        controller.processGesture("NONE", 0, 0, 1000, 1000);
        controller.processGesture("DRAW", 10, 10, 1000, 1000);
        controller.processGesture("NONE", 0, 0, 1000, 1000);
        assertEquals("Visible strokes count is 2", 2, engine.getVisibleStrokeCount());

        // First CLEAR message: clears canvas and history
        controller.processGesture("CLEAR", 0, 0, 1000, 1000);
        assertEquals("Visible strokes 0 after CLEAR", 0, engine.getVisibleStrokeCount());
        assertEquals("Undo count 0 after CLEAR", 0, engine.getUndoCount());

        // Repeated CLEAR messages: safe no-op
        controller.processGesture("CLEAR", 0, 0, 1000, 1000);
        assertEquals("Visible strokes remain 0 on repeated CLEAR", 0, engine.getVisibleStrokeCount());

        // 7. Coordinate scaling to canvas dimensions
        // Canvas of size 800 x 600
        controller.processGesture("NONE", 0, 0, 800, 600);
        controller.processGesture("DRAW", 500, 500, 800, 600); // 500/1000 -> 400x300
        controller.processGesture("NONE", 0, 0, 800, 600);
        assertEquals("Stroke committed with scaled coords", 1, engine.getVisibleStrokeCount());
        assertEquals("X scaled to 400", 400, engine.getVisibleStrokesHead().getStroke().getHead().x);
        assertEquals("Y scaled to 300", 300, engine.getVisibleStrokesHead().getStroke().getHead().y);

        // 8. Regression: Guard against sentinel (0, 0) coordinate during DRAW
        engine.clear();
        controller.processGesture("NONE", 0, 0, 1000, 1000);
        controller.processGesture("DRAW", 0, 0, 1000, 1000);
        assertTrue("Sentinel (0,0) does not start an active stroke", !engine.hasActiveStroke());
        assertEquals("Visible strokes remain 0 on sentinel DRAW", 0, engine.getVisibleStrokeCount());

        // 9. Regression: Guard against zero-sized canvas dimensions
        controller.processGesture("DRAW", 500, 500, 0, 0);
        assertTrue("Zero-sized canvas safely ignored", !engine.hasActiveStroke());

        // 10. Regression: Separate consecutive strokes start independently at fingertip, never connected
        engine.clear();
        controller.processGesture("NONE", 0, 0, 1000, 1000);
        // Stroke 1
        controller.processGesture("DRAW", 200, 200, 1000, 1000);
        controller.processGesture("DRAW", 250, 250, 1000, 1000);
        controller.processGesture("MOVE", 400, 400, 1000, 1000); // Pen-up
        // Stroke 2
        controller.processGesture("DRAW", 700, 700, 1000, 1000);
        controller.processGesture("DRAW", 750, 750, 1000, 1000);
        controller.processGesture("NONE", 0, 0, 1000, 1000); // Pen-up
        assertEquals("Two distinct strokes committed", 2, engine.getVisibleStrokeCount());
        aircanvas.ds.Stroke s1 = engine.getVisibleStrokesHead().getStroke();
        aircanvas.ds.Stroke s2 = engine.getVisibleStrokesHead().next.getStroke();
        assertEquals("Stroke 1 first point X is 200", 200, s1.getHead().x);
        assertEquals("Stroke 1 first point Y is 200", 200, s1.getHead().y);
        assertEquals("Stroke 2 first point X is 700", 700, s2.getHead().x);
        assertEquals("Stroke 2 first point Y is 700", 700, s2.getHead().y);

        // 11. Adaptive Smoothing: Low-speed jitter suppression vs. High-speed zero-lag tracking
        engine.clear();
        controller.processGesture("NONE", 0, 0, 1000, 1000);
        // A. Start slow stroke with slight sensor jitter (all displacements < 10 px)
        controller.processGesture("DRAW", 100, 100, 1000, 1000); // Point 1: (100, 100)
        controller.processGesture("DRAW", 102, 100, 1000, 1000); // Point 2: smoothed with Point 1
        controller.processGesture("DRAW", 98, 100, 1000, 1000);  // Point 3: smoothed with Points 1 & 2
        controller.processGesture("DRAW", 100, 100, 1000, 1000); // Point 4: smoothed over 4-point buffer
        aircanvas.ds.Stroke jitterStroke = engine.getActiveStroke();
        assertEquals("Active stroke has 4 jitter points", 4, jitterStroke.getPointCount());
        // Trailing point should average out 100, 102, 98, 100 -> exactly 100
        aircanvas.ds.PointNode lastJitterPt = jitterStroke.getTail();
        assertEquals("Jitter point X smoothed to average 100", 100, lastJitterPt.x);
        assertEquals("Jitter point Y smoothed to average 100", 100, lastJitterPt.y);

        // B. High-speed rapid motion (displacement 80 px >= 28 px)
        // Must clear stale slow history and track raw coordinate with zero lag
        controller.processGesture("DRAW", 180, 100, 1000, 1000); // Fast move to 180
        aircanvas.ds.PointNode fastPt1 = jitterStroke.getTail();
        assertEquals("Fast motion point X tracks raw position at 180 (zero lag)", 180, fastPt1.x);
        assertEquals("Fast motion point Y tracks raw position at 100", 100, fastPt1.y);

        // C. High-speed sharp corner preservation (displacement 80 px downwards)
        controller.processGesture("DRAW", 180, 180, 1000, 1000); // Fast 90-degree turn
        aircanvas.ds.PointNode cornerPt = jitterStroke.getTail();
        assertEquals("Sharp corner X preserved at 180 without corner clipping", 180, cornerPt.x);
        assertEquals("Sharp corner Y preserved at 180 without lag", 180, cornerPt.y);

        // D. Immediate pen-up upon intentional transition to MOVE
        controller.processGesture("MOVE", 250, 250, 1000, 1000);
        assertTrue("Active stroke immediately finished on MOVE", !engine.hasActiveStroke());
        assertEquals("Stroke committed to canvas (count: 1)", 1, engine.getVisibleStrokeCount());
        assertEquals("Total points in committed stroke is 6", 6, engine.getVisibleStrokesHead().getStroke().getPointCount());

        // 12. Live Fingertip Pointer Behavior
        DrawingEngine pointerEngine = new DrawingEngine();
        aircanvas.ui.CanvasPanel pointerCanvas = new aircanvas.ui.CanvasPanel(pointerEngine, null);
        pointerCanvas.setSize(1000, 1000);
        GestureController pointerController = new GestureController(pointerEngine, pointerCanvas, null);

        // A. Initially pointer is not visible
        assertTrue("Pointer hidden initially", !pointerCanvas.isPointerVisible());

        // B. MOVE updates pointer position without adding stroke or altering undo/redo history
        pointerController.processGesture("MOVE", 350, 450, 1000, 1000);
        assertTrue("MOVE makes pointer visible", pointerCanvas.isPointerVisible());
        assertEquals("Pointer X is 350 on MOVE", 350, pointerCanvas.getPointerX());
        assertEquals("Pointer Y is 450 on MOVE", 450, pointerCanvas.getPointerY());
        assertEquals("No strokes added on MOVE", 0, pointerEngine.getVisibleStrokeCount());
        assertEquals("Undo history unchanged on MOVE", 0, pointerEngine.getUndoCount());
        assertEquals("Redo history unchanged on MOVE", 0, pointerEngine.getRedoCount());

        // C. DRAW updates pointer and draws normally
        pointerController.processGesture("DRAW", 500, 600, 1000, 1000);
        assertTrue("DRAW keeps pointer visible", pointerCanvas.isPointerVisible());
        assertEquals("Pointer X is 500 on DRAW", 500, pointerCanvas.getPointerX());
        assertEquals("Pointer Y is 600 on DRAW", 600, pointerCanvas.getPointerY());
        assertTrue("DRAW has active stroke", pointerEngine.hasActiveStroke());

        // D. NONE hides the pointer
        pointerController.processGesture("NONE", 0, 0, 1000, 1000);
        assertTrue("NONE hides pointer", !pointerCanvas.isPointerVisible());
        assertEquals("Stroke committed after NONE", 1, pointerEngine.getVisibleStrokeCount());

        // E. Tracking loss (0, 0) hides pointer and never moves to (0, 0)
        pointerController.processGesture("MOVE", 200, 200, 1000, 1000);
        assertTrue("Pointer visible before tracking loss", pointerCanvas.isPointerVisible());
        pointerController.processGesture("MOVE", 0, 0, 1000, 1000);
        assertTrue("Tracking loss (0,0) in MOVE hides pointer", !pointerCanvas.isPointerVisible());

        pointerController.processGesture("DRAW", 0, 0, 1000, 1000);
        assertTrue("Sentinel (0,0) in DRAW keeps pointer hidden", !pointerCanvas.isPointerVisible());

        // F. Coordinate alignment after resizing
        // Canvas resized from 1000x1000 to 500x500
        pointerCanvas.setSize(500, 500);
        pointerController.processGesture("MOVE", 400, 600, 500, 500); // 40% of 500 = 200, 60% of 500 = 300
        assertTrue("Pointer visible after resize", pointerCanvas.isPointerVisible());
        assertEquals("Pointer X scaled to 200 on 500px width", 200, pointerCanvas.getPointerX());
        assertEquals("Pointer Y scaled to 300 on 500px height", 300, pointerCanvas.getPointerY());

        // G. PNG export excludes the live pointer overlay
        java.awt.image.BufferedImage exportedImg = pointerCanvas.exportToImage();
        assertEquals("Exported image width matches canvas", 500, exportedImg.getWidth());
        assertEquals("Exported image height matches canvas", 500, exportedImg.getHeight());
        int exportedPixel = exportedImg.getRGB(200, 300);
        assertEquals("Exported image at pointer coord is white (no pointer rendered)", java.awt.Color.WHITE.getRGB(), exportedPixel);

        // 13. End-to-end TCP Socket + GestureServer + GestureController Integration
        int testPort;
        try (ServerSocket tempSocket = new ServerSocket(0)) {
            testPort = tempSocket.getLocalPort();
        }

        DrawingEngine socketEngine = new DrawingEngine();
        JPanel socketCanvas = new JPanel();
        socketCanvas.setSize(1000, 1000);

        final Object syncLock = new Object();
        final int[] eventsDelivered = {0};

        GestureController socketController = new GestureController(socketEngine, socketCanvas, () -> {
            synchronized (syncLock) {
                eventsDelivered[0]++;
                syncLock.notifyAll();
            }
        });

        GestureServer server = new GestureServer("127.0.0.1", testPort);
        server.setEventListener(socketController);
        server.start();

        try (Socket clientSocket = new Socket("127.0.0.1", testPort);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Stream DRAW packets over real TCP socket
            writer.println("GESTURE,DRAW,100,200");
            writer.println("GESTURE,DRAW,200,300");
            writer.println("GESTURE,MOVE,300,400"); // finishes stroke

            // Wait for EDT processing
            synchronized (syncLock) {
                long deadline = System.currentTimeMillis() + 3000;
                while (eventsDelivered[0] < 3 && System.currentTimeMillis() < deadline) {
                    syncLock.wait(500);
                }
            }

            // Allow EDT queue to settle
            Thread.sleep(100);

            assertEquals("Visible stroke count is 1 via TCP stream", 1, socketEngine.getVisibleStrokeCount());
            assertEquals("Undo count is 1 via TCP stream", 1, socketEngine.getUndoCount());

            // Send UNDO over TCP
            writer.println("GESTURE,UNDO,0,0");

            synchronized (syncLock) {
                long deadline = System.currentTimeMillis() + 3000;
                while (eventsDelivered[0] < 4 && System.currentTimeMillis() < deadline) {
                    syncLock.wait(500);
                }
            }
            Thread.sleep(100);

            assertEquals("Visible stroke count is 0 after TCP UNDO", 0, socketEngine.getVisibleStrokeCount());
            assertEquals("Redo count is 1 after TCP UNDO", 1, socketEngine.getRedoCount());
        }

        server.stop();
        assertTrue("Server stopped cleanly after test", !server.isRunning());

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

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


        // 8. End-to-end TCP Socket + GestureServer + GestureController Integration
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

package aircanvas.network;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class GestureServerTest {
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
        System.out.println("=== Testing GestureServer Protocol & Networking ===");

        // --- 1. Protocol Parsing Unit Tests ---

        // 1.1 Valid supported gestures
        String[] validGestures = {"DRAW", "MOVE", "UNDO", "REDO", "CLEAR", "NONE"};
        for (String g : validGestures) {
            GestureServer.GestureEvent ev = GestureServer.parseMessage("GESTURE," + g + ",500,400");
            assertTrue("Valid parsing for " + g, ev != null);
            assertEquals("Gesture name matches " + g, g, ev.getGesture());
            assertEquals("X is 500 for " + g, 500, ev.getX());
            assertEquals("Y is 400 for " + g, 400, ev.getY());
        }

        // 1.2 Coordinate boundaries (0 and 1000)
        GestureServer.GestureEvent minCoord = GestureServer.parseMessage("GESTURE,DRAW,0,0");
        assertTrue("Min coord boundary (0, 0) parsed", minCoord != null);
        assertEquals("Min X is 0", 0, minCoord.getX());
        assertEquals("Min Y is 0", 0, minCoord.getY());

        GestureServer.GestureEvent maxCoord = GestureServer.parseMessage("GESTURE,MOVE,1000,1000");
        assertTrue("Max coord boundary (1000, 1000) parsed", maxCoord != null);
        assertEquals("Max X is 1000", 1000, maxCoord.getX());
        assertEquals("Max Y is 1000", 1000, maxCoord.getY());

        // 1.3 Invalid gesture names
        assertEquals("Reject unknown gesture ZOOM", null, GestureServer.parseMessage("GESTURE,ZOOM,500,400"));
        assertEquals("Reject lowercase gesture draw", null, GestureServer.parseMessage("GESTURE,draw,500,400"));
        assertEquals("Reject empty gesture", null, GestureServer.parseMessage("GESTURE,,500,400"));

        // 1.4 Out-of-bounds, negative, non-numeric coordinates
        assertEquals("Reject negative X (-1)", null, GestureServer.parseMessage("GESTURE,DRAW,-1,500"));
        assertEquals("Reject negative Y (-1)", null, GestureServer.parseMessage("GESTURE,DRAW,500,-1"));
        assertEquals("Reject X > 1000 (1001)", null, GestureServer.parseMessage("GESTURE,DRAW,1001,500"));
        assertEquals("Reject Y > 1000 (1001)", null, GestureServer.parseMessage("GESTURE,DRAW,500,1001"));
        assertEquals("Reject non-numeric X (abc)", null, GestureServer.parseMessage("GESTURE,DRAW,abc,400"));
        assertEquals("Reject non-numeric Y (xyz)", null, GestureServer.parseMessage("GESTURE,DRAW,500,xyz"));
        assertEquals("Reject floating-point coord (500.5)", null, GestureServer.parseMessage("GESTURE,DRAW,500.5,400"));

        // 1.5 Malformed structure and token counts
        assertEquals("Reject wrong header COMMAND", null, GestureServer.parseMessage("COMMAND,DRAW,500,400"));
        assertEquals("Reject too few tokens (3 tokens)", null, GestureServer.parseMessage("GESTURE,DRAW,500"));
        assertEquals("Reject too many tokens (5 tokens)", null, GestureServer.parseMessage("GESTURE,DRAW,500,400,EXTRA"));
        assertEquals("Reject null string", null, GestureServer.parseMessage(null));
        assertEquals("Reject empty string", null, GestureServer.parseMessage(""));

        // --- 2. Live TCP Socket Communication Tests ---

        // Find an available port for testing
        int testPort;
        try (ServerSocket tempSocket = new ServerSocket(0)) {
            testPort = tempSocket.getLocalPort();
        }

        GestureServer server = new GestureServer("127.0.0.1", testPort);

        // Holder for received event
        final GestureServer.GestureEvent[] receivedEvent = new GestureServer.GestureEvent[1];
        final Object lock = new Object();

        server.setEventListener(event -> {
            synchronized (lock) {
                receivedEvent[0] = event;
                lock.notifyAll();
            }
        });

        server.start();
        assertTrue("Server is running on port " + testPort, server.isRunning());

        // 2.1 Connect client, send valid event over TCP
        try (Socket clientSocket = new Socket("127.0.0.1", testPort);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            synchronized (lock) {
                receivedEvent[0] = null;
            }

            writer.println("GESTURE,DRAW,350,750");

            synchronized (lock) {
                if (receivedEvent[0] == null) {
                    lock.wait(2000);
                }
            }

            assertTrue("Event received over TCP", receivedEvent[0] != null);
            assertEquals("TCP Gesture is DRAW", "DRAW", receivedEvent[0].getGesture());
            assertEquals("TCP X is 350", 350, receivedEvent[0].getX());
            assertEquals("TCP Y is 750", 750, receivedEvent[0].getY());

            // 2.2 Malformed input over TCP must not crash the server
            synchronized (lock) {
                receivedEvent[0] = null;
            }

            writer.println("CORRUPTED,LINE,TEST");
            writer.println("GESTURE,UNDO,0,0"); // Followed by valid command

            synchronized (lock) {
                if (receivedEvent[0] == null) {
                    lock.wait(2000);
                }
            }

            assertTrue("Valid event received after malformed packet", receivedEvent[0] != null);
            assertEquals("Recovered event is UNDO", "UNDO", receivedEvent[0].getGesture());
        } // clientSocket closes here (client disconnect)

        // 2.3 Client reconnect test (proves server survives client disconnect)
        try (Socket clientSocket2 = new Socket("127.0.0.1", testPort);
             PrintWriter writer2 = new PrintWriter(
                     new OutputStreamWriter(clientSocket2.getOutputStream(), StandardCharsets.UTF_8), true)) {

            synchronized (lock) {
                receivedEvent[0] = null;
            }

            writer2.println("GESTURE,CLEAR,0,0");

            synchronized (lock) {
                if (receivedEvent[0] == null) {
                    lock.wait(2000);
                }
            }

            assertTrue("New client reconnected and event received", receivedEvent[0] != null);
            assertEquals("Reconnected event is CLEAR", "CLEAR", receivedEvent[0].getGesture());
        }

        // 2.4 Server shutdown and port release
        server.stop();
        assertTrue("Server stopped", !server.isRunning());

        // Verify port was cleanly released by immediately binding a new ServerSocket
        boolean portReleased = false;
        try (ServerSocket probe = new ServerSocket(testPort, 1, InetAddress.getByName("127.0.0.1"))) {
            portReleased = true;
        } catch (Exception e) {
            portReleased = false;
        }
        assertTrue("Port " + testPort + " was released and can be rebound", portReleased);

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

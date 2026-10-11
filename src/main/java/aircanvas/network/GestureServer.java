package aircanvas.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Background TCP Server for Receiving Webcam Gesture Events.
 *
 * Listens on 127.0.0.1:5000 (configurable) for incoming connections from
 * the Python MediaPipe tracking client.
 *
 * Protocol:
 *  UTF-8 newline-delimited ASCII strings:
 *  GESTURE,<NAME>,<X>,<Y>
 *
 *  Supported Gestures:
 *   - DRAW
 *   - MOVE
 *   - UNDO
 *   - REDO
 *   - CLEAR
 *   - NONE
 *
 *  Normalized Coordinates:
 *   - Integer values in range [0, 1000]
 *
 * Threading & Integration Contract:
 *  Network socket acceptance and line reading run on an independent background thread
 *  to avoid blocking the Swing Event Dispatch Thread (EDT).
 *  When integrated with the UI, the listener should dispatch drawing commands
 *  to Swing via `SwingUtilities.invokeLater(...)`.
 *
 * Zero Java Collections Framework classes used.
 */
public class GestureServer {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 5000;

    public static final String GESTURE_DRAW  = "DRAW";
    public static final String GESTURE_MOVE  = "MOVE";
    public static final String GESTURE_UNDO  = "UNDO";
    public static final String GESTURE_REDO  = "REDO";
    public static final String GESTURE_CLEAR = "CLEAR";
    public static final String GESTURE_NONE  = "NONE";

    /**
     * Immutable value object representing a validated gesture packet.
     */
    public static class GestureEvent {
        private final String gesture;
        private final int x;
        private final int y;

        public GestureEvent(String gesture, int x, int y) {
            this.gesture = gesture;
            this.x = x;
            this.y = y;
        }

        public String getGesture() {
            return gesture;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public String toString() {
            return "GestureEvent[" + gesture + ", " + x + ", " + y + "]";
        }
    }

    @FunctionalInterface
    public interface GestureEventListener {
        void onGestureEvent(GestureEvent event);
    }

    public interface ErrorListener {
        void onError(String message, Throwable cause);
    }

    private final String host;
    private final int port;
    private GestureEventListener eventListener;
    private ErrorListener errorListener;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running;
    private volatile Socket currentClientSocket;

    public GestureServer() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public GestureServer(int port) {
        this(DEFAULT_HOST, port);
    }

    public GestureServer(String host, int port) {
        this.host = (host != null) ? host : DEFAULT_HOST;
        this.port = port;
        this.running = false;
    }

    public void setEventListener(GestureEventListener listener) {
        this.eventListener = listener;
    }

    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    /**
     * Starts listening for TCP connections on a background worker thread.
     *
     * @throws IOException if server socket fails to bind
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
        running = true;

        serverThread = new Thread(this::listenLoop, "GestureServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Stops the server, closes client and server sockets, and releases the port.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        closeCurrentClient();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return (serverSocket != null && !serverSocket.isClosed()) ? serverSocket.getLocalPort() : port;
    }

    private void listenLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                this.currentClientSocket = client;
                handleClient(client);
            } catch (SocketException se) {
                // Socket closed during stop()
                if (!running) {
                    break;
                }
                reportError("Socket exception while waiting for connection", se);
            } catch (IOException e) {
                if (!running) {
                    break;
                }
                reportError("I/O error accepting client connection", e);
            } finally {
                closeCurrentClient();
            }
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                GestureEvent event = parseMessage(line);
                if (event != null) {
                    dispatch(event);
                } else {
                    reportError("Rejected malformed packet: '" + line + "'", null);
                }
            }
        } catch (SocketException se) {
            // Client disconnected or socket closed
            if (running) {
                reportError("Client disconnected: " + se.getMessage(), null);
            }
        } catch (IOException e) {
            if (running) {
                reportError("Error reading from client socket", e);
            }
        }
    }

    private void closeCurrentClient() {
        if (currentClientSocket != null && !currentClientSocket.isClosed()) {
            try {
                currentClientSocket.close();
            } catch (IOException ignored) {
            }
        }
        currentClientSocket = null;
    }

    private void dispatch(GestureEvent event) {
        GestureEventListener listener = this.eventListener;
        if (listener != null) {
            try {
                listener.onGestureEvent(event);
            } catch (Exception e) {
                reportError("Error in gesture event listener callback", e);
            }
        }
    }

    private void reportError(String message, Throwable cause) {
        ErrorListener listener = this.errorListener;
        if (listener != null) {
            try {
                listener.onError(message, cause);
            } catch (Exception ignored) {
            }
        } else {
            System.err.println("[GestureServer] " + message + (cause != null ? ": " + cause.getMessage() : ""));
        }
    }

    /**
     * Parses and validates a raw protocol line.
     *
     * Valid format:
     *   GESTURE,<TYPE>,<X>,<Y>
     *
     * @param line raw UTF-8 string
     * @return validated GestureEvent, or null if line is malformed
     */
    public static GestureEvent parseMessage(String line) {
        if (line == null) {
            return null;
        }

        String[] tokens = line.split(",", -1);
        if (tokens.length != 4) {
            return null;
        }

        if (!"GESTURE".equals(tokens[0].trim())) {
            return null;
        }

        String gesture = tokens[1].trim();
        if (!isValidGesture(gesture)) {
            return null;
        }

        int x;
        int y;
        try {
            x = Integer.parseInt(tokens[2].trim());
            y = Integer.parseInt(tokens[3].trim());
        } catch (NumberFormatException e) {
            return null;
        }

        if (x < 0 || x > 1000 || y < 0 || y > 1000) {
            return null;
        }

        return new GestureEvent(gesture, x, y);
    }

    /**
     * Validates whether a gesture name matches one of the 6 supported types.
     */
    public static boolean isValidGesture(String gesture) {
        if (gesture == null) {
            return false;
        }
        return GESTURE_DRAW.equals(gesture)
                || GESTURE_MOVE.equals(gesture)
                || GESTURE_UNDO.equals(gesture)
                || GESTURE_REDO.equals(gesture)
                || GESTURE_CLEAR.equals(gesture)
                || GESTURE_NONE.equals(gesture);
    }

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        GestureServer server = new GestureServer(port);
        server.setEventListener(event -> {
            System.out.println("RECEIVED:" + event.getGesture() + ":" + event.getX() + ":" + event.getY());
            System.out.flush();
        });
        server.start();
        System.out.println("READY:" + server.getPort());
        System.out.flush();

        // Keep running until stdin EOF
        try {
            while (System.in.read() != -1) {
                // keep open
            }
        } finally {
            server.stop();
        }
    }
}

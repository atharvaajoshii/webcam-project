package aircanvas.engine;

import aircanvas.ds.CircularBuffer;
import aircanvas.network.GestureServer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Controller routing incoming network gesture packets to the authoritative DrawingEngine.
 *
 * Implements one-shot trigger behavior for discrete actions (UNDO, REDO, CLEAR)
 * and streams continuous smoothed trajectory coordinates during DRAW.
 *
 * Ensures all Swing and DrawingEngine state updates execute safely on the
 * Event Dispatch Thread (EDT).
 *
 * Zero Java Collections Framework classes used.
 */
public class GestureController implements GestureServer.GestureEventListener {

    private final DrawingEngine engine;
    private final JPanel canvasPanel;
    private final Runnable onStateChanged;

    private final CircularBuffer<Integer> xBuffer;
    private final CircularBuffer<Integer> yBuffer;

    private String previousGesture;

    public GestureController(DrawingEngine engine, JPanel canvasPanel, Runnable onStateChanged) {
        if (engine == null) {
            throw new IllegalArgumentException("DrawingEngine cannot be null");
        }
        this.engine = engine;
        this.canvasPanel = canvasPanel;
        this.onStateChanged = onStateChanged;
        this.xBuffer = new CircularBuffer<>(4);
        this.yBuffer = new CircularBuffer<>(4);
        this.previousGesture = GestureServer.GESTURE_NONE;
    }

    /**
     * Listener callback invoked by GestureServer on its background network thread.
     * Safely marshals processing to the Swing Event Dispatch Thread.
     */
    @Override
    public void onGestureEvent(GestureServer.GestureEvent event) {
        if (event == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            int width = (canvasPanel != null && canvasPanel.getWidth() > 0) ? canvasPanel.getWidth() : 0;
            int height = (canvasPanel != null && canvasPanel.getHeight() > 0) ? canvasPanel.getHeight() : 0;

            if (width <= 0 || height <= 0) {
                // Canvas panel not yet laid out or visible; safely ignore coordinate events
                return;
            }

            processGesture(event.getGesture(), event.getX(), event.getY(), width, height);

            if (canvasPanel != null) {
                canvasPanel.repaint();
            }
            if (onStateChanged != null) {
                onStateChanged.run();
            }
        });
    }

    /**
     * Core gesture state machine logic.
     * Can be invoked directly on the EDT or in standalone unit tests.
     *
     * @param gesture validated gesture string
     * @param normX normalized X coordinate [0, 1000]
     * @param normY normalized Y coordinate [0, 1000]
     * @param canvasWidth current width in pixels
     * @param canvasHeight current height in pixels
     */
    public void processGesture(String gesture, int normX, int normY, int canvasWidth, int canvasHeight) {
        if (gesture == null || canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }

        // Map normalized coordinates [0, 1000] to canvas pixel space
        int pixelX = (int) Math.round((normX / 1000.0) * canvasWidth);
        int pixelY = (int) Math.round((normY / 1000.0) * canvasHeight);
        pixelX = Math.max(0, Math.min(canvasWidth, pixelX));
        pixelY = Math.max(0, Math.min(canvasHeight, pixelY));

        boolean gestureChanged = !gesture.equals(previousGesture);

        switch (gesture) {
            case GestureServer.GESTURE_DRAW:
                // Guard: Ignore dummy sentinel coordinates (0, 0)
                if (normX == 0 && normY == 0) {
                    return;
                }

                if (gestureChanged || !engine.hasActiveStroke()) {
                    // Transition into DRAW -> begin a new stroke independently
                    engine.beginStroke();
                    xBuffer.clear();
                    yBuffer.clear();
                }

                xBuffer.add(pixelX);
                yBuffer.add(pixelY);
                int smoothX = calculateAverage(xBuffer);
                int smoothY = calculateAverage(yBuffer);

                engine.addPoint(smoothX, smoothY);
                break;

            case GestureServer.GESTURE_MOVE:
                terminateActiveStroke();
                // Cursor movement without drawing points
                break;

            case GestureServer.GESTURE_UNDO:
                terminateActiveStroke();
                if (gestureChanged) {
                    // One-shot: trigger exactly once per gesture activation
                    engine.undo();
                }
                break;

            case GestureServer.GESTURE_REDO:
                terminateActiveStroke();
                if (gestureChanged) {
                    // One-shot: trigger exactly once per gesture activation
                    engine.redo();
                }
                break;

            case GestureServer.GESTURE_CLEAR:
                terminateActiveStroke();
                if (gestureChanged) {
                    // One-shot: trigger exactly once per gesture activation
                    engine.clear();
                }
                break;

            case GestureServer.GESTURE_NONE:
            default:
                terminateActiveStroke();
                // Re-arms all one-shot gestures by transitioning state away
                break;
        }

        this.previousGesture = gesture;
    }

    private void terminateActiveStroke() {
        if (engine.hasActiveStroke()) {
            engine.finishStroke();
        }
        xBuffer.clear();
        yBuffer.clear();
    }

    private int calculateAverage(CircularBuffer<Integer> buffer) {
        if (buffer.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (int i = 0; i < buffer.size(); i++) {
            sum += buffer.get(i);
        }
        return Math.round((float) sum / buffer.size());
    }

    public String getPreviousGesture() {
        return previousGesture;
    }

    public void reset() {
        terminateActiveStroke();
        this.previousGesture = GestureServer.GESTURE_NONE;
    }
}


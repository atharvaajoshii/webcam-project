package aircanvas.engine;

import aircanvas.ds.CircularBuffer;
import aircanvas.network.GestureServer;
import aircanvas.ui.CanvasPanel;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Controller routing incoming network gesture packets to the authoritative DrawingEngine.
 *
 * Implements one-shot trigger behavior for discrete actions (UNDO, REDO, CLEAR)
 * and streams continuous smoothed trajectory coordinates during DRAW.
 *
 * Coordinates live fingertip pointer overlay updates on CanvasPanel for MOVE and DRAW,
 * hiding the pointer when tracking is lost or inactive.
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
    private int lastSmoothX;
    private int lastSmoothY;
    private boolean hasLastSmoothPoint;

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
        this.lastSmoothX = 0;
        this.lastSmoothY = 0;
        this.hasLastSmoothPoint = false;
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
        boolean hasValidCoords = (normX > 0 || normY > 0);

        switch (gesture) {
            case GestureServer.GESTURE_DRAW:
                // Guard: Ignore dummy sentinel coordinates (0, 0)
                if (normX == 0 && normY == 0) {
                    hidePointer();
                    return;
                }

                // Keep pointer visible at current fingertip position during DRAW
                updatePointerPosition(normX, normY);

                if (gestureChanged || !engine.hasActiveStroke()) {
                    // Transition into DRAW -> begin a new stroke independently
                    engine.beginStroke();
                    xBuffer.clear();
                    yBuffer.clear();
                    hasLastSmoothPoint = false;
                }

                // Compute displacement from previous smoothed point to adapt smoothing
                double distance = 0.0;
                if (hasLastSmoothPoint) {
                    double dx = pixelX - lastSmoothX;
                    double dy = pixelY - lastSmoothY;
                    distance = Math.hypot(dx, dy);
                }

                // Adaptive smoothing:
                // High speed (distance >= 28 px): reset stale history, use current coordinate (0 lag, sharp corners)
                // Moderate speed (10 <= distance < 28 px): average newest 2 points (light smoothing)
                // Low speed / stationary (distance < 10 px): average full buffer (suppresses webcam jitter)
                if (distance >= 28.0) {
                    xBuffer.clear();
                    yBuffer.clear();
                }

                xBuffer.add(pixelX);
                yBuffer.add(pixelY);

                int smoothX = calculateAdaptiveAverage(xBuffer, distance);
                int smoothY = calculateAdaptiveAverage(yBuffer, distance);

                lastSmoothX = smoothX;
                lastSmoothY = smoothY;
                hasLastSmoothPoint = true;

                engine.addPoint(smoothX, smoothY);
                break;

            case GestureServer.GESTURE_MOVE:
                terminateActiveStroke();
                // Move pointer without drawing points
                if (hasValidCoords) {
                    updatePointerPosition(normX, normY);
                } else {
                    hidePointer();
                }
                break;

            case GestureServer.GESTURE_UNDO:
                terminateActiveStroke();
                if (hasValidCoords) {
                    updatePointerPosition(normX, normY);
                } else {
                    hidePointer();
                }
                if (gestureChanged) {
                    // One-shot: trigger exactly once per gesture activation
                    engine.undo();
                }
                break;

            case GestureServer.GESTURE_REDO:
                terminateActiveStroke();
                if (hasValidCoords) {
                    updatePointerPosition(normX, normY);
                } else {
                    hidePointer();
                }
                if (gestureChanged) {
                    // One-shot: trigger exactly once per gesture activation
                    engine.redo();
                }
                break;

            case GestureServer.GESTURE_CLEAR:
                terminateActiveStroke();
                if (hasValidCoords) {
                    updatePointerPosition(normX, normY);
                } else {
                    hidePointer();
                }
                if (gestureChanged) {
                    // One-shot: trigger exactly once per gesture activation
                    engine.clear();
                }
                break;

            case GestureServer.GESTURE_NONE:
            default:
                terminateActiveStroke();
                hidePointer();
                // Re-arms all one-shot gestures by transitioning state away
                break;
        }

        this.previousGesture = gesture;
    }

    private void updatePointerPosition(int normX, int normY) {
        if (canvasPanel instanceof CanvasPanel cp) {
            cp.setPointerPosition(normX, normY);
        }
    }

    private void hidePointer() {
        if (canvasPanel instanceof CanvasPanel cp) {
            cp.hidePointer();
        }
    }

    private void terminateActiveStroke() {
        if (engine.hasActiveStroke()) {
            engine.finishStroke();
        }
        xBuffer.clear();
        yBuffer.clear();
        hasLastSmoothPoint = false;
    }

    private int calculateAdaptiveAverage(CircularBuffer<Integer> buffer, double distance) {
        if (buffer.isEmpty()) {
            return 0;
        }
        int k;
        if (distance >= 28.0) {
            k = 1;
        } else if (distance >= 10.0) {
            k = Math.min(2, buffer.size());
        } else {
            k = buffer.size();
        }

        int sum = 0;
        int startIndex = buffer.size() - k;
        for (int i = startIndex; i < buffer.size(); i++) {
            sum += buffer.get(i);
        }
        return Math.round((float) sum / k);
    }

    public boolean isPointerVisible() {
        if (canvasPanel instanceof CanvasPanel cp) {
            return cp.isPointerVisible();
        }
        return false;
    }

    public int getPointerX() {
        if (canvasPanel instanceof CanvasPanel cp) {
            return cp.getPointerX();
        }
        return 0;
    }

    public int getPointerY() {
        if (canvasPanel instanceof CanvasPanel cp) {
            return cp.getPointerY();
        }
        return 0;
    }

    public String getPreviousGesture() {
        return previousGesture;
    }

    public void reset() {
        terminateActiveStroke();
        hidePointer();
        this.previousGesture = GestureServer.GESTURE_NONE;
    }
}


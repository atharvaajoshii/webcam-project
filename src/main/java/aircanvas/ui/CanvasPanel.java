package aircanvas.ui;

import aircanvas.ds.PointNode;
import aircanvas.ds.Stroke;
import aircanvas.ds.StrokeList;
import aircanvas.engine.DrawingEngine;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

/**
 * Authoritative Swing drawing canvas.
 *
 * Renders all visible strokes in drawing order directly from DrawingEngine
 * without copying point data into any Collections.
 *
 * Provides a live fingertip pointer overlay for non-drawing and drawing feedback.
 * Pointer is kept strictly decoupled from stroke data and excluded from exports.
 *
 * Supports mouse input for interactive local testing before webcam integration.
 */
public class CanvasPanel extends JPanel {

    private final DrawingEngine engine;
    private final Runnable onStateChanged;

    // Transient live fingertip pointer state (never committed to drawing history)
    private boolean pointerVisible = false;
    private int pointerNormX = 0;
    private int pointerNormY = 0;

    public CanvasPanel(DrawingEngine engine, Runnable onStateChanged) {
        if (engine == null) {
            throw new IllegalArgumentException("DrawingEngine cannot be null");
        }
        this.engine = engine;
        this.onStateChanged = onStateChanged;

        setBackground(Color.WHITE);
        setOpaque(true);
        setDoubleBuffered(true);

        setupMouseListeners();
    }

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                engine.beginStroke();
                engine.addPoint(e.getX(), e.getY());
                repaint();
                notifyStateChanged();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                engine.finishStroke();
                repaint();
                notifyStateChanged();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                engine.addPoint(e.getX(), e.getY());
                repaint();
                notifyStateChanged();
            }
        });
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    /**
     * Updates the pointer position using normalized coordinates [0, 1000]
     * and ensures it is displayed on screen.
     */
    public void setPointerPosition(int normX, int normY) {
        if (normX <= 0 && normY <= 0) {
            hidePointer();
            return;
        }
        this.pointerNormX = normX;
        this.pointerNormY = normY;
        this.pointerVisible = true;
        repaint();
    }

    /**
     * Hides the live pointer overlay when tracking is lost or inactive.
     */
    public void hidePointer() {
        if (this.pointerVisible) {
            this.pointerVisible = false;
            repaint();
        }
    }

    public boolean isPointerVisible() {
        return pointerVisible;
    }

    public int getPointerX() {
        return (int) Math.round((pointerNormX / 1000.0) * getWidth());
    }

    public int getPointerY() {
        return (int) Math.round((pointerNormY / 1000.0) * getHeight());
    }

    public int getPointerNormX() {
        return pointerNormX;
    }

    public int getPointerNormY() {
        return pointerNormY;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Explicitly clear entire component bounds to eliminate uninitialized
        // native buffer artifacts during window resizing
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.clipRect(0, 0, getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // 1. Render all committed and active drawing strokes
            renderStrokes(g2);

            // 2. Render transient fingertip pointer overlay if visible
            if (pointerVisible) {
                renderPointer(g2);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Renders all visible strokes in drawing order directly from DrawingEngine.
     */
    private void renderStrokes(Graphics2D g2) {
        // 1. Render completed visible strokes in original drawing order
        StrokeList.StrokeNode current = engine.getVisibleStrokesHead();
        while (current != null) {
            renderStroke(g2, current.getStroke());
            current = current.next;
        }

        // 2. Render active in-flight stroke if one is being drawn
        Stroke active = engine.getActiveStroke();
        if (active != null) {
            renderStroke(g2, active);
        }
    }

    /**
     * Renders a contrasting, non-intrusive live pointer at the fingertip.
     * Features a red core, crisp white boundary, and subtle dark outline.
     */
    private void renderPointer(Graphics2D g2) {
        int x = getPointerX();
        int y = getPointerY();
        int radius = 7;

        g2.setColor(new Color(230, 30, 30));
        g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);

        g2.setColor(new Color(0, 0, 0, 140));
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawOval(x - radius - 1, y - radius - 1, (radius + 1) * 2, (radius + 1) * 2);
    }

    /**
     * Exports the authoritative canvas content (background and all visible strokes)
     * as a BufferedImage, strictly excluding any transient UI overlays such as the live pointer.
     *
     * @return BufferedImage containing only the committed and active strokes on canvas background.
     */
    public BufferedImage exportToImage() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, w, h);
            renderStrokes(g2);
        } finally {
            g2.dispose();
        }
        return img;
    }

    /**
     * Exports canvas to a PNG file, excluding live pointer overlay.
     */
    public void exportToPNG(java.io.File file) throws java.io.IOException {
        if (file == null) {
            throw new IllegalArgumentException("Target file cannot be null");
        }
        BufferedImage img = exportToImage();
        javax.imageio.ImageIO.write(img, "PNG", file);
    }

    /**
     * Traverses the singly linked list of points directly using head -> next.
     * Zero collections copied.
     */
    private void renderStroke(Graphics2D g2, Stroke stroke) {
        if (stroke == null || stroke.isEmpty()) {
            return;
        }

        g2.setColor(stroke.getColor());
        int thickness = stroke.getThickness();
        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        PointNode prev = stroke.getHead();
        if (prev == null) {
            return;
        }

        if (prev.next == null) {
            // Single-point stroke: render circle dot
            int radius = Math.max(2, thickness / 2);
            g2.fillOval(prev.x - radius, prev.y - radius, radius * 2, radius * 2);
            return;
        }

        PointNode curr = prev.next;
        while (curr != null) {
            g2.drawLine(prev.x, prev.y, curr.x, curr.y);
            prev = curr;
            curr = curr.next;
        }
    }
}

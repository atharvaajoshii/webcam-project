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

/**
 * Authoritative Swing drawing canvas.
 *
 * Renders all visible strokes in drawing order directly from DrawingEngine
 * without copying point data into any Collections.
 *
 * Supports mouse input for interactive local testing before webcam integration.
 */
public class CanvasPanel extends JPanel {

    private final DrawingEngine engine;
    private final Runnable onStateChanged;

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
        } finally {
            g2.dispose();
        }
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

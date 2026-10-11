package aircanvas.ui;

import aircanvas.engine.DrawingEngine;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Authoritative Air Canvas Paint Window.
 *
 * Provides a Swing GUI containing the interactive CanvasPanel,
 * controls for Undo/Redo/Clear, color palette buttons, thickness slider,
 * and a status bar indicating canvas state.
 */
public class PaintWindow extends JFrame {

    private final DrawingEngine engine;
    private final CanvasPanel canvasPanel;

    private final JLabel statusLabel;
    private String colorName = "Black";

    public PaintWindow() {
        this(new DrawingEngine());
    }

    public PaintWindow(DrawingEngine engine) {
        super("Air Canvas");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(800, 500));
        setPreferredSize(new Dimension(1024, 720));

        // Create status bar first so canvas can update it
        this.statusLabel = new JLabel();
        this.statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        this.statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        // Create CanvasPanel
        this.canvasPanel = new CanvasPanel(engine, this::updateStatusBar);

        // Build UI structure
        getContentPane().setBackground(Color.WHITE);
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.NORTH);
        add(canvasPanel, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        updateStatusBar();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBackground(new Color(245, 245, 247));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 215)));

        // Action Buttons
        JButton btnUndo = createActionButton("Undo", () -> {
            engine.undo();
            canvasPanel.repaint();
            updateStatusBar();
        });

        JButton btnRedo = createActionButton("Redo", () -> {
            engine.redo();
            canvasPanel.repaint();
            updateStatusBar();
        });

        JButton btnClear = createActionButton("Clear", () -> {
            engine.clear();
            canvasPanel.repaint();
            updateStatusBar();
        });

        toolbar.add(btnUndo);
        toolbar.add(btnRedo);
        toolbar.add(btnClear);
        toolbar.add(Box.createHorizontalStrut(10));

        // Color Palette
        toolbar.add(new JLabel("Color:"));
        toolbar.add(createColorButton("Black", new Color(30, 30, 30)));
        toolbar.add(createColorButton("Blue", new Color(30, 144, 255)));
        toolbar.add(createColorButton("Green", new Color(34, 139, 34)));
        toolbar.add(createColorButton("Red", new Color(220, 20, 60)));
        toolbar.add(createColorButton("Yellow", new Color(230, 180, 0)));

        toolbar.add(Box.createHorizontalStrut(10));

        // Brush Thickness Slider
        toolbar.add(new JLabel("Size:"));
        JSlider sizeSlider = new JSlider(1, 24, engine.getCurrentThickness());
        sizeSlider.setPreferredSize(new Dimension(130, 30));
        sizeSlider.setBackground(new Color(245, 245, 247));
        sizeSlider.addChangeListener(e -> {
            int newSize = sizeSlider.getValue();
            engine.setCurrentThickness(newSize);
            updateStatusBar();
        });
        toolbar.add(sizeSlider);

        return toolbar;
    }

    private JButton createActionButton(String text, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private JButton createColorButton(String name, Color color) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setBackground(color);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        btn.setToolTipText(name);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            engine.setCurrentColor(color);
            this.colorName = name;
            updateStatusBar();
        });
        return btn;
    }

    private JPanel createStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(245, 245, 247));
        statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 215)));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        return statusPanel;
    }

    private void updateStatusBar() {
        SwingUtilities.invokeLater(() -> {
            String text = String.format(
                    "Color: %s  |  Size: %dpx  |  Visible Strokes: %d  |  Undo: %d/5  |  Redo: %d",
                    colorName,
                    engine.getCurrentThickness(),
                    engine.getVisibleStrokeCount(),
                    engine.getUndoCount(),
                    engine.getRedoCount()
            );
            statusLabel.setText(text);
        });
    }

    public DrawingEngine getEngine() {
        return engine;
    }

    public CanvasPanel getCanvasPanel() {
        return canvasPanel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PaintWindow window = new PaintWindow();
            window.setVisible(true);
        });
    }
}

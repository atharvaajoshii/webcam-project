package aircanvas.ui;

import aircanvas.engine.DrawingEngine;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class PaintWindowTest {
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

    public static void main(String[] args) {
        System.out.println("=== Testing PaintWindow and CanvasPanel ===");

        DrawingEngine engine = new DrawingEngine();
        PaintWindow window = new PaintWindow(engine, false);

        // 1. Window properties
        assertEquals("Title is 'Air Canvas'", "Air Canvas", window.getTitle());
        assertTrue("CanvasPanel is attached", window.getCanvasPanel() != null);
        assertTrue("Engine reference matches", window.getEngine() == engine);

        CanvasPanel canvas = window.getCanvasPanel();
        canvas.setSize(new Dimension(800, 600));

        // 2. Offscreen rendering with empty canvas
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        canvas.paint(g2); // Should execute cleanly without error
        g2.dispose();
        assertTrue("Empty canvas renders cleanly without exceptions", true);

        // 3. Draw single-point stroke and multi-point stroke and render
        engine.beginStroke(Color.RED, 8);
        engine.addPoint(50, 50);
        engine.finishStroke(); // Single-point dot

        engine.beginStroke(Color.BLUE, 4);
        engine.addPoint(100, 100);
        engine.addPoint(150, 150);
        engine.addPoint(200, 150);
        engine.finishStroke(); // Connected line stroke

        assertEquals("Visible strokes count is 2", 2, engine.getVisibleStrokeCount());

        // In-flight active stroke rendering
        engine.beginStroke(Color.GREEN, 6);
        engine.addPoint(300, 300);
        engine.addPoint(350, 350);

        // Render both completed strokes and active stroke
        g2 = img.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        assertTrue("Canvas with multi-color and active stroke renders without exceptions", true);

        engine.finishStroke();
        assertEquals("Visible strokes count is now 3", 3, engine.getVisibleStrokeCount());

        // 4. Test Undo / Redo through UI actions
        engine.undo();
        assertEquals("Visible stroke count is 2 after undo", 2, engine.getVisibleStrokeCount());
        g2 = img.createGraphics();
        canvas.paint(g2);
        g2.dispose();

        engine.redo();
        assertEquals("Visible stroke count restored to 3 after redo", 3, engine.getVisibleStrokeCount());

        // 5. Test Clear
        engine.clear();
        assertEquals("Visible stroke count is 0 after clear", 0, engine.getVisibleStrokeCount());
        g2 = img.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        assertTrue("Canvas renders cleanly after clear", true);

        // Clean up window
        window.dispose();

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

package aircanvas.engine;

import aircanvas.ds.PointNode;
import aircanvas.ds.Stroke;
import aircanvas.ds.StrokeList;

import java.awt.Color;

public class DrawingEngineTest {
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
        System.out.println("=== Testing DrawingEngine ===");

        DrawingEngine engine = new DrawingEngine();

        // 1. Initial State
        assertEquals("Initial visible stroke count is 0", 0, engine.getVisibleStrokeCount());
        assertEquals("Initial undo count is 0", 0, engine.getUndoCount());
        assertEquals("Initial redo count is 0", 0, engine.getRedoCount());
        assertTrue("No active stroke initially", !engine.hasActiveStroke());

        // 2. Safe handling of invalid operations before any stroke
        assertTrue("addPoint() without active stroke returns false", !engine.addPoint(100, 200));
        assertTrue("finishStroke() without active stroke returns false", !engine.finishStroke());
        assertTrue("undo() on empty history returns false (no crash)", !engine.undo());
        assertTrue("redo() on empty history returns false (no crash)", !engine.redo());

        // 3. Begin, add points, and finish a stroke
        engine.beginStroke(Color.RED, 6);
        assertTrue("hasActiveStroke() is true after beginStroke", engine.hasActiveStroke());
        assertEquals("Active stroke is not null", true, engine.getActiveStroke() != null);

        assertTrue("addPoint 1 succeeded", engine.addPoint(10, 20));
        assertTrue("addPoint 2 succeeded", engine.addPoint(30, 40));
        assertTrue("addPoint 3 succeeded", engine.addPoint(50, 60));

        // In-flight active stroke has 3 points before finish
        assertEquals("Active stroke has 3 points", 3, engine.getActiveStroke().getPointCount());
        assertEquals("Visible stroke count is still 0 before finish", 0, engine.getVisibleStrokeCount());

        assertTrue("finishStroke() succeeded", engine.finishStroke());
        assertTrue("No active stroke after finish", !engine.hasActiveStroke());
        assertEquals("Visible stroke count is now 1", 1, engine.getVisibleStrokeCount());
        assertEquals("Undo count is now 1", 1, engine.getUndoCount());
        assertEquals("Redo count is 0", 0, engine.getRedoCount());

        // 4. Repeated finish calls do not duplicate stroke
        assertTrue("Second finishStroke() call returns false", !engine.finishStroke());
        assertEquals("Visible stroke count remains 1", 1, engine.getVisibleStrokeCount());
        assertEquals("Undo count remains 1", 1, engine.getUndoCount());

        // 5. Stroke and point order verification
        StrokeList.StrokeNode headNode = engine.getVisibleStrokesHead();
        assertTrue("Head node is not null", headNode != null);
        Stroke s1 = headNode.getStroke();
        assertEquals("s1 Color is RED", Color.RED, s1.getColor());
        assertEquals("s1 Thickness is 6", 6, s1.getThickness());
        assertEquals("s1 pointCount is 3", 3, s1.getPointCount());

        PointNode p1 = s1.getHead();
        assertEquals("p1 is (10, 20)", "(10, 20)", p1.toString());
        assertEquals("p2 is (30, 40)", "(30, 40)", p1.next.toString());
        assertEquals("p3 is (50, 60)", "(50, 60)", p1.next.next.toString());
        assertEquals("p3.next is null", null, p1.next.next.next);

        // 6. Undo and Redo behavior with reference preservation
        PointNode originalHeadPoint = s1.getHead();
        PointNode originalTailPoint = s1.getTail();

        assertTrue("undo() succeeded", engine.undo());
        assertEquals("Visible stroke count is 0 after undo", 0, engine.getVisibleStrokeCount());
        assertEquals("Undo count is 0 after undo", 0, engine.getUndoCount());
        assertEquals("Redo count is 1 after undo", 1, engine.getRedoCount());
        assertEquals("Head visible stroke is null after undo", null, engine.getVisibleStrokesHead());

        assertTrue("redo() succeeded", engine.redo());
        assertEquals("Visible stroke count restored to 1", 1, engine.getVisibleStrokeCount());
        assertEquals("Undo count restored to 1", 1, engine.getUndoCount());
        assertEquals("Redo count is 0 after redo", 0, engine.getRedoCount());

        Stroke restoredStroke = engine.getVisibleStrokesHead().getStroke();
        assertTrue("Restored stroke is identical instance s1", restoredStroke == s1);
        assertTrue("Head PointNode reference preserved", restoredStroke.getHead() == originalHeadPoint);
        assertTrue("Tail PointNode reference preserved", restoredStroke.getTail() == originalTailPoint);

        // 7. Empty stroke discard (begin then finish with 0 points)
        engine.beginStroke();
        assertTrue("hasActiveStroke() is true", engine.hasActiveStroke());
        assertTrue("finishStroke() on empty stroke returns false", !engine.finishStroke());
        assertTrue("Active stroke cleared", !engine.hasActiveStroke());
        assertEquals("Visible stroke count still 1", 1, engine.getVisibleStrokeCount());

        // 8. 5-stroke undo limit and older stroke persistence
        // Draw 5 more strokes: total visible = 6
        engine.beginStroke(Color.BLUE, 4);
        engine.addPoint(100, 100);
        engine.finishStroke(); // Stroke 2 (Undo count = 2)

        engine.beginStroke(Color.GREEN, 4);
        engine.addPoint(200, 200);
        engine.finishStroke(); // Stroke 3 (Undo count = 3)

        engine.beginStroke(Color.MAGENTA, 4);
        engine.addPoint(300, 300);
        engine.finishStroke(); // Stroke 4 (Undo count = 4)

        engine.beginStroke(Color.ORANGE, 4);
        engine.addPoint(400, 400);
        engine.finishStroke(); // Stroke 5 (Undo count = 5: full)

        engine.beginStroke(Color.PINK, 4);
        engine.addPoint(500, 500);
        engine.finishStroke(); // Stroke 6 (Undo count = 5: Stroke 1 evicted from undo history)

        assertEquals("Total visible strokes on canvas is 6", 6, engine.getVisibleStrokeCount());
        assertEquals("Undo count is capped at 5", 5, engine.getUndoCount());

        // Perform 5 undos
        assertTrue("Undo Stroke 6 succeeded", engine.undo());
        assertTrue("Undo Stroke 5 succeeded", engine.undo());
        assertTrue("Undo Stroke 4 succeeded", engine.undo());
        assertTrue("Undo Stroke 3 succeeded", engine.undo());
        assertTrue("Undo Stroke 2 succeeded", engine.undo());
        assertEquals("Undo count is 0 after 5 undos", 0, engine.getUndoCount());
        assertEquals("Redo count is 5 after 5 undos", 5, engine.getRedoCount());

        // 6th undo must be a safe no-op because Stroke 1 was evicted from undo stack
        assertTrue("6th undo() returns false (oldest stroke cannot be undone)", !engine.undo());

        // Verify Stroke 1 REMAINS VISIBLE on canvas!
        assertEquals("Canvas still displays the 1 non-undoable stroke (Stroke 1)", 1, engine.getVisibleStrokeCount());
        assertTrue("Remaining visible stroke is s1", engine.getVisibleStrokesHead().getStroke() == s1);

        // 9. New stroke clears redo history
        engine.beginStroke(Color.YELLOW, 8);
        engine.addPoint(600, 600);
        engine.finishStroke();

        assertEquals("Redo history cleared upon new stroke", 0, engine.getRedoCount());
        assertTrue("redo() returns false after new stroke", !engine.redo());
        assertEquals("Visible stroke count is 2 (Stroke 1 + new stroke)", 2, engine.getVisibleStrokeCount());
        assertEquals("Undo count is 1", 1, engine.getUndoCount());

        // 10. Clear resets everything
        engine.clear();
        assertEquals("Visible strokes 0 after clear()", 0, engine.getVisibleStrokeCount());
        assertEquals("Undo count 0 after clear()", 0, engine.getUndoCount());
        assertEquals("Redo count 0 after clear()", 0, engine.getRedoCount());
        assertTrue("No active stroke after clear()", !engine.hasActiveStroke());
        assertTrue("getVisibleStrokesHead() is null after clear()", engine.getVisibleStrokesHead() == null);

        // Subsequent stroke after clear works normally
        engine.beginStroke();
        engine.addPoint(1, 1);
        engine.finishStroke();
        assertEquals("Visible stroke count is 1 after reuse", 1, engine.getVisibleStrokeCount());
        assertEquals("Undo count is 1 after reuse", 1, engine.getUndoCount());

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

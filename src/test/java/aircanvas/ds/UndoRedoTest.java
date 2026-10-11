package aircanvas.ds;

public class UndoRedoTest {
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
        System.out.println("=== Testing UndoStack and RedoStack ===");

        // 1. UndoStack empty state & exceptions
        UndoStack undo = new UndoStack();
        assertTrue("UndoStack empty initially", undo.isEmpty());
        assertTrue("UndoStack not full initially", !undo.isFull());
        assertEquals("UndoStack size 0 initially", 0, undo.size());
        assertEquals("UndoStack capacity is 3", 3, undo.capacity());

        boolean caughtEmptyUndoPop = false;
        try {
            undo.pop();
        } catch (IllegalStateException e) {
            caughtEmptyUndoPop = true;
        }
        assertTrue("pop() on empty UndoStack throws IllegalStateException", caughtEmptyUndoPop);

        boolean caughtEmptyUndoPeek = false;
        try {
            undo.peek();
        } catch (IllegalStateException e) {
            caughtEmptyUndoPeek = true;
        }
        assertTrue("peek() on empty UndoStack throws IllegalStateException", caughtEmptyUndoPeek);

        // 2. RedoStack empty state & exceptions
        RedoStack redo = new RedoStack();
        assertTrue("RedoStack empty initially", redo.isEmpty());
        assertEquals("RedoStack size 0 initially", 0, redo.size());

        boolean caughtEmptyRedoPop = false;
        try {
            redo.pop();
        } catch (IllegalStateException e) {
            caughtEmptyRedoPop = true;
        }
        assertTrue("pop() on empty RedoStack throws IllegalStateException", caughtEmptyRedoPop);

        boolean caughtEmptyRedoPeek = false;
        try {
            redo.peek();
        } catch (IllegalStateException e) {
            caughtEmptyRedoPeek = true;
        }
        assertTrue("peek() on empty RedoStack throws IllegalStateException", caughtEmptyRedoPeek);

        // 3. UndoStack single & LIFO ordering within capacity
        Stroke s1 = new Stroke();
        s1.addPoint(1, 1);
        StrokeList.StrokeNode n1 = new StrokeList.StrokeNode(s1);

        Stroke s2 = new Stroke();
        s2.addPoint(2, 2);
        StrokeList.StrokeNode n2 = new StrokeList.StrokeNode(s2);

        undo.push(n1);
        assertEquals("Undo size 1", 1, undo.size());
        assertTrue("Undo peek is n1", undo.peek() == n1);

        undo.push(n2);
        assertEquals("Undo size 2", 2, undo.size());
        assertTrue("Undo peek is n2", undo.peek() == n2);

        assertTrue("First pop is n2 (LIFO)", undo.pop() == n2);
        assertEquals("Undo size after 1 pop", 1, undo.size());
        assertTrue("Second pop is n1", undo.pop() == n1);
        assertTrue("Undo empty after popping all", undo.isEmpty());

        // 4. UndoStack 3-stroke capacity limit & overflow eviction
        Stroke s3 = new Stroke();
        StrokeList.StrokeNode n3 = new StrokeList.StrokeNode(s3);

        Stroke s4 = new Stroke();
        StrokeList.StrokeNode n4 = new StrokeList.StrokeNode(s4);

        undo.push(n1);
        undo.push(n2);
        undo.push(n3);
        assertTrue("UndoStack is full at 3 elements", undo.isFull());
        assertEquals("Undo size is 3", 3, undo.size());

        // Push 4th stroke -> oldest (n1) must be evicted!
        StrokeList.StrokeNode evicted = undo.push(n4);
        assertTrue("Evicted element is oldest stroke n1", evicted == n1);
        assertTrue("UndoStack remains full at 3 elements", undo.isFull());
        assertEquals("Undo size remains 3", 3, undo.size());

        // Popping the 3 elements must return n4, n3, n2 (n1 is no longer in undo history)
        assertTrue("Pop 1 is newest n4", undo.pop() == n4);
        assertTrue("Pop 2 is n3", undo.pop() == n3);
        assertTrue("Pop 3 is n2", undo.pop() == n2);
        assertTrue("UndoStack empty; n1 was discarded", undo.isEmpty());

        // 5. Invariant: Discarded undo history remains visible in StrokeList
        StrokeList visibleCanvas = new StrokeList();
        UndoStack undoHistory = new UndoStack();

        Stroke st1 = new Stroke();
        Stroke st2 = new Stroke();
        Stroke st3 = new Stroke();
        Stroke st4 = new Stroke();

        StrokeList.StrokeNode vn1 = visibleCanvas.append(st1);
        undoHistory.push(vn1);

        StrokeList.StrokeNode vn2 = visibleCanvas.append(st2);
        undoHistory.push(vn2);

        StrokeList.StrokeNode vn3 = visibleCanvas.append(st3);
        undoHistory.push(vn3);

        StrokeList.StrokeNode vn4 = visibleCanvas.append(st4);
        undoHistory.push(vn4); // evicts vn1 from undo stack

        assertEquals("All 4 strokes remain visible on canvas", 4, visibleCanvas.size());

        // Perform 3 undos (all available in undo history)
        StrokeList.StrokeNode undone4 = undoHistory.pop();
        visibleCanvas.remove(undone4);

        StrokeList.StrokeNode undone3 = undoHistory.pop();
        visibleCanvas.remove(undone3);

        StrokeList.StrokeNode undone2 = undoHistory.pop();
        visibleCanvas.remove(undone2);

        assertTrue("Undo history is now completely exhausted", undoHistory.isEmpty());
        assertEquals("Canvas still displays the 1 non-undoable stroke (st1)", 1, visibleCanvas.size());
        assertTrue("Visible stroke is st1", visibleCanvas.getHead().getStroke() == st1);

        // 6. RedoStack LIFO ordering & integration restoration
        RedoStack redoHistory = new RedoStack();
        redoHistory.push(undone2);
        redoHistory.push(undone3);
        redoHistory.push(undone4);

        assertEquals("Redo size is 3", 3, redoHistory.size());
        assertTrue("Redo peek is most recently undone (undone4)", redoHistory.peek() == undone4);

        // Redo 1 stroke -> restores undone4
        StrokeList.StrokeNode redone4 = redoHistory.pop();
        assertTrue("Redone node is undone4", redone4 == undone4);
        visibleCanvas.restore(redone4);
        assertEquals("Canvas size increased to 2 after redo", 2, visibleCanvas.size());
        assertTrue("Canvas tail is now restored st4", visibleCanvas.getTail().getStroke() == st4);

        // 7. New stroke invalidates redo history
        Stroke stNew = new Stroke();
        StrokeList.StrokeNode vnNew = visibleCanvas.append(stNew);
        undoHistory.push(vnNew);
        redoHistory.clear(); // Contract: drawing a new stroke clears redo

        assertTrue("Redo history cleared upon new stroke", redoHistory.isEmpty());
        assertEquals("Redo size 0", 0, redoHistory.size());

        // 8. Canvas clear resets both histories
        visibleCanvas.clear();
        undoHistory.clear();
        redoHistory.clear();

        assertTrue("Canvas empty after clear", visibleCanvas.isEmpty());
        assertTrue("Undo empty after clear", undoHistory.isEmpty());
        assertTrue("Redo empty after clear", redoHistory.isEmpty());

        // 9. Null safety check
        boolean caughtNullUndo = false;
        try {
            undo.push(null);
        } catch (IllegalArgumentException e) {
            caughtNullUndo = true;
        }
        assertTrue("UndoStack rejects null with IllegalArgumentException", caughtNullUndo);

        boolean caughtNullRedo = false;
        try {
            redo.push(null);
        } catch (IllegalArgumentException e) {
            caughtNullRedo = true;
        }
        assertTrue("RedoStack rejects null with IllegalArgumentException", caughtNullRedo);

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

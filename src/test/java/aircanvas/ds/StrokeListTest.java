package aircanvas.ds;

public class StrokeListTest {
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
        System.out.println("=== Testing StrokeList ===");

        // 1. Empty state
        StrokeList list = new StrokeList();
        assertTrue("Initially empty", list.isEmpty());
        assertEquals("Initial size is 0", 0, list.size());
        assertEquals("Initial head is null", null, list.getHead());
        assertEquals("Initial tail is null", null, list.getTail());
        assertEquals("removeLast() on empty list returns null", null, list.removeLast());

        // 2. Single stroke append
        Stroke s1 = new Stroke();
        s1.addPoint(10, 10);
        StrokeList.StrokeNode n1 = list.append(s1);

        assertTrue("Not empty after append", !list.isEmpty());
        assertEquals("Size is 1", 1, list.size());
        assertTrue("Head equals tail for single stroke", list.getHead() == list.getTail());
        assertTrue("Head references n1", list.getHead() == n1);
        assertEquals("n1 prev is null", null, n1.prev);
        assertEquals("n1 next is null", null, n1.next);

        // 3. Multiple appends and bidirectional traversal
        Stroke s2 = new Stroke();
        s2.addPoint(20, 20);
        Stroke s3 = new Stroke();
        s3.addPoint(30, 30);

        StrokeList.StrokeNode n2 = list.append(s2);
        StrokeList.StrokeNode n3 = list.append(s3);

        assertEquals("Size is 3", 3, list.size());
        assertTrue("Head is n1", list.getHead() == n1);
        assertTrue("Tail is n3", list.getTail() == n3);

        // Forward traversal
        assertTrue("n1.next == n2", n1.next == n2);
        assertTrue("n2.next == n3", n2.next == n3);
        assertEquals("n3.next == null", null, n3.next);

        // Backward traversal
        assertTrue("n3.prev == n2", n3.prev == n2);
        assertTrue("n2.prev == n1", n2.prev == n1);
        assertEquals("n1.prev == null", null, n1.prev);

        // 4. Middle node removal
        list.remove(n2);
        assertEquals("Size is 2 after removing middle node", 2, list.size());
        assertTrue("n1.next is now n3", n1.next == n3);
        assertTrue("n3.prev is now n1", n3.prev == n1);
        assertEquals("n2.prev is cleared", null, n2.prev);
        assertEquals("n2.next is cleared", null, n2.next);

        // 5. Middle node restoration in O(1)
        list.restore(n2);
        assertEquals("Size restored to 3", 3, list.size());
        assertTrue("n1.next restored to n2", n1.next == n2);
        assertTrue("n2.prev restored to n1", n2.prev == n1);
        assertTrue("n2.next restored to n3", n2.next == n3);
        assertTrue("n3.prev restored to n2", n3.prev == n2);

        // 6. Tail removal via removeLast()
        StrokeList.StrokeNode removedTail = list.removeLast();
        assertTrue("removeLast() returns n3", removedTail == n3);
        assertEquals("Size is 2 after tail removal", 2, list.size());
        assertTrue("Tail is now n2", list.getTail() == n2);
        assertEquals("n2.next is null", null, n2.next);

        // 7. Tail restoration
        list.restore(removedTail);
        assertEquals("Size restored to 3", 3, list.size());
        assertTrue("Tail is n3 again", list.getTail() == n3);
        assertTrue("n2.next is n3 again", n2.next == n3);
        assertTrue("n3.prev is n2 again", n3.prev == n2);

        // 8. Head removal
        list.remove(n1);
        assertEquals("Size is 2 after head removal", 2, list.size());
        assertTrue("Head is now n2", list.getHead() == n2);
        assertEquals("n2.prev is null", null, n2.prev);

        // 9. Head restoration
        list.restore(n1);
        assertEquals("Size restored to 3", 3, list.size());
        assertTrue("Head is n1 again", list.getHead() == n1);
        assertEquals("n1.prev is null", null, n1.prev);
        assertTrue("n1.next is n2 again", n1.next == n2);
        assertTrue("n2.prev is n1 again", n2.prev == n1);

        // 10. Single-element removal to empty
        StrokeList singleList = new StrokeList();
        Stroke sSingle = new Stroke();
        StrokeList.StrokeNode nSingle = singleList.append(sSingle);
        singleList.remove(nSingle);
        assertTrue("List is empty after removing only node", singleList.isEmpty());
        assertEquals("Head is null", null, singleList.getHead());
        assertEquals("Tail is null", null, singleList.getTail());
        assertEquals("Size is 0", 0, singleList.size());

        // Restore single node back to empty list
        singleList.restore(nSingle);
        assertTrue("List not empty after single restore", !singleList.isEmpty());
        assertEquals("Size is 1 after single restore", 1, singleList.size());
        assertTrue("Head is nSingle", singleList.getHead() == nSingle);
        assertTrue("Tail is nSingle", singleList.getTail() == nSingle);

        // 11. Point preservation check (no points copied during removal/restore)
        Stroke testStroke = new Stroke();
        testStroke.addPoint(1, 1);
        testStroke.addPoint(2, 2);
        PointNode originalHeadPoint = testStroke.getHead();
        PointNode originalTailPoint = testStroke.getTail();

        StrokeList.StrokeNode testNode = list.append(testStroke);
        list.remove(testNode);
        list.restore(testNode);

        assertTrue("Stroke instance unchanged", testNode.getStroke() == testStroke);
        assertTrue("Head PointNode reference preserved", testNode.getStroke().getHead() == originalHeadPoint);
        assertTrue("Tail PointNode reference preserved", testNode.getStroke().getTail() == originalTailPoint);
        assertEquals("Point count unchanged", 2, testNode.getStroke().getPointCount());

        // 12. Clear and reuse
        list.clear();
        assertTrue("List empty after clear()", list.isEmpty());
        assertEquals("Size 0 after clear()", 0, list.size());
        assertEquals("Head null after clear()", null, list.getHead());
        assertEquals("Tail null after clear()", null, list.getTail());

        Stroke sReused = new Stroke();
        sReused.addPoint(99, 99);
        StrokeList.StrokeNode nReused = list.append(sReused);
        assertEquals("Size is 1 after reuse", 1, list.size());
        assertTrue("Head is nReused", list.getHead() == nReused);
        assertTrue("Tail is nReused", list.getTail() == nReused);

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

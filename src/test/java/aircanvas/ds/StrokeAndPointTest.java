package aircanvas.ds;

import java.awt.Color;

public class StrokeAndPointTest {
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
        System.out.println("=== Testing Stroke and PointNode ===");

        // 1. PointNode basic behavior
        PointNode p1 = new PointNode(100, 200);
        assertEquals("p1 X is 100", 100, p1.getX());
        assertEquals("p1 Y is 200", 200, p1.getY());
        assertEquals("p1 next is initially null", null, p1.getNext());

        PointNode p2 = new PointNode(150, 250);
        p1.setNext(p2);
        assertTrue("p1 next references p2", p1.getNext() == p2);
        assertEquals("p1 toString()", "(100, 200)", p1.toString());

        // 2. Stroke initial empty state
        Stroke stroke = new Stroke(Color.BLUE, 5);
        assertTrue("Stroke is initially empty", stroke.isEmpty());
        assertEquals("Point count is initially 0", 0, stroke.getPointCount());
        assertEquals("Head is null initially", null, stroke.getHead());
        assertEquals("Tail is null initially", null, stroke.getTail());
        assertEquals("Color is Blue", Color.BLUE, stroke.getColor());
        assertEquals("Thickness is 5", 5, stroke.getThickness());

        // 3. First point insertion (boundary: empty -> 1 element)
        stroke.addPoint(10, 20);
        assertTrue("Stroke is not empty after 1 insertion", !stroke.isEmpty());
        assertEquals("Point count is 1", 1, stroke.getPointCount());
        assertTrue("Head is not null", stroke.getHead() != null);
        assertTrue("Tail is not null", stroke.getTail() != null);
        assertTrue("Head and Tail reference the exact same node", stroke.getHead() == stroke.getTail());
        assertEquals("Head X is 10", 10, stroke.getHead().x);
        assertEquals("Head Y is 20", 20, stroke.getHead().y);
        assertEquals("Head next is null", null, stroke.getHead().next);

        // 4. Multiple point insertions (head/tail invariant with multiple elements)
        stroke.addPoint(30, 40);
        stroke.addPoint(50, 60);
        stroke.addPoint(70, 80);

        assertEquals("Point count is 4 after 4 insertions", 4, stroke.getPointCount());
        assertEquals("Head coordinates remain first point (10, 20)", "(10, 20)", stroke.getHead().toString());
        assertEquals("Tail coordinates are last point (70, 80)", "(70, 80)", stroke.getTail().toString());
        assertEquals("Tail next must be null", null, stroke.getTail().next);
        assertTrue("Head and Tail are distinct nodes", stroke.getHead() != stroke.getTail());

        // 5. Traversal verification in strict drawing order
        int[] expectedX = {10, 30, 50, 70};
        int[] expectedY = {20, 40, 60, 80};
        PointNode current = stroke.getHead();
        int traversalCount = 0;
        while (current != null) {
            assertEquals("Point " + traversalCount + " X matches", expectedX[traversalCount], current.x);
            assertEquals("Point " + traversalCount + " Y matches", expectedY[traversalCount], current.y);
            traversalCount++;
            current = current.next;
        }
        assertEquals("Traversal visited exactly pointCount nodes", stroke.getPointCount(), traversalCount);

        // 6. Adding by PointNode reference
        PointNode pCustom = new PointNode(90, 100);
        stroke.addPoint(pCustom);
        assertEquals("Point count increased to 5", 5, stroke.getPointCount());
        assertTrue("Tail is now pCustom", stroke.getTail() == pCustom);
        assertEquals("Tail next is null", null, stroke.getTail().next);

        // 7. Null safety check
        boolean caughtNull = false;
        try {
            stroke.addPoint((PointNode) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue("Adding null PointNode throws IllegalArgumentException", caughtNull);

        // 8. Clear and reuse
        stroke.clear();
        assertTrue("Stroke is empty after clear()", stroke.isEmpty());
        assertEquals("Point count is 0 after clear()", 0, stroke.getPointCount());
        assertEquals("Head is null after clear()", null, stroke.getHead());
        assertEquals("Tail is null after clear()", null, stroke.getTail());

        stroke.addPoint(1, 2);
        assertEquals("Point count is 1 after re-adding", 1, stroke.getPointCount());
        assertTrue("Head and Tail reference new node after re-adding", stroke.getHead() == stroke.getTail());
        assertEquals("New head X is 1", 1, stroke.getHead().x);
        assertEquals("New head Y is 2", 2, stroke.getHead().y);

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

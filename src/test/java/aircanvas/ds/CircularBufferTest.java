package aircanvas.ds;
import aircanvas.ds.CircularBuffer;

public class CircularBufferTest {
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
        System.out.println("=== Testing CircularBuffer ===");

        // 1. Empty state
        CircularBuffer<String> buf = new CircularBuffer<>(3);
        assertTrue("Empty on start", buf.isEmpty());
        assertEquals("Size is 0", 0, buf.size());

        // 2. Fill to capacity
        buf.add("A");
        buf.add("B");
        buf.add("C");
        assertTrue("Full when size == capacity", buf.isFull());
        assertEquals("Oldest is A", "A", buf.getOldest());
        assertEquals("Newest is C", "C", buf.getNewest());

        // 3. Wraparound / Overwrite oldest
        buf.add("D"); // Overwrites "A"
        assertEquals("Size stays 3", 3, buf.size());
        assertEquals("Oldest is now B", "B", buf.getOldest());
        assertEquals("Newest is now D", "D", buf.getNewest());
        assertEquals("Index 0 is B", "B", buf.get(0));
        assertEquals("Index 2 is D", "D", buf.get(2));

        // 4. Gesture stabilization: majority confirmation
        CircularBuffer<String> gestureBuf = new CircularBuffer<>(5);
        gestureBuf.add("DRAW");
        gestureBuf.add("DRAW");
        gestureBuf.add("DRAW");
        gestureBuf.add("DRAW");
        gestureBuf.add("MOVE");
        assertEquals("4/5 threshold confirms DRAW", "DRAW", gestureBuf.getStableElement(4));

        // 5. Gesture stabilization: noise rejection
        CircularBuffer<String> noiseBuf = new CircularBuffer<>(5);
        noiseBuf.add("DRAW");
        noiseBuf.add("MOVE");
        noiseBuf.add("DRAW");
        noiseBuf.add("MOVE");
        noiseBuf.add("NONE");
        assertEquals("Noisy frames reject false trigger", null, noiseBuf.getStableElement(4));

        // 6. Clear and reuse
        buf.clear();
        assertTrue("Empty after clear", buf.isEmpty());
        assertEquals("Size is 0 after clear", 0, buf.size());

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

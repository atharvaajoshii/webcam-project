package aircanvas.ds;

public class CircularArrayQueueTest {
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
        System.out.println("=== Testing CircularArrayQueue ===");

        // 1. Initial empty state
        CircularArrayQueue<String> q = new CircularArrayQueue<>(3);
        assertTrue("Queue is empty on creation", q.isEmpty());
        assertTrue("Queue is not full on creation", !q.isFull());
        assertEquals("Size is 0 on creation", 0, q.size());
        assertEquals("Capacity is 3", 3, q.capacity());

        // 2. Exception on peek/dequeue when empty
        boolean caughtEmptyDequeue = false;
        try {
            q.dequeue();
        } catch (IllegalStateException e) {
            caughtEmptyDequeue = true;
        }
        assertTrue("dequeue() throws IllegalStateException on empty queue", caughtEmptyDequeue);

        boolean caughtEmptyPeek = false;
        try {
            q.peek();
        } catch (IllegalStateException e) {
            caughtEmptyPeek = true;
        }
        assertTrue("peek() throws IllegalStateException on empty queue", caughtEmptyPeek);
        assertEquals("poll() returns null when empty", null, q.poll());

        // 3. FIFO order verification
        q.enqueue("DRAW");
        q.enqueue("MOVE");
        assertEquals("Size is 2 after 2 enqueues", 2, q.size());
        assertEquals("peek() returns front ('DRAW') without removing", "DRAW", q.peek());
        assertEquals("Size remains 2 after peek()", 2, q.size());

        assertEquals("First dequeued item is 'DRAW'", "DRAW", q.dequeue());
        assertEquals("Second dequeued item is 'MOVE'", "MOVE", q.dequeue());
        assertTrue("Queue is empty after dequeuing all items", q.isEmpty());

        // 4. Full queue policy (no silent overwrite)
        q.enqueue("CMD_1");
        q.enqueue("CMD_2");
        q.enqueue("CMD_3");
        assertTrue("Queue is full when size reaches capacity", q.isFull());
        assertEquals("Size is 3 when full", 3, q.size());

        boolean caughtFullEnqueue = false;
        try {
            q.enqueue("CMD_OVERFLOW");
        } catch (IllegalStateException e) {
            caughtFullEnqueue = true;
        }
        assertTrue("enqueue() on full queue throws IllegalStateException", caughtFullEnqueue);
        assertTrue("offer() on full queue returns false", !q.offer("CMD_OVERFLOW"));
        assertEquals("Size remains unchanged after rejected enqueues", 3, q.size());

        // 5. Circular wraparound verification
        // Dequeue 2 items (frees front slots 0 and 1)
        assertEquals("Dequeued CMD_1", "CMD_1", q.dequeue());
        assertEquals("Dequeued CMD_2", "CMD_2", q.dequeue());
        assertEquals("Remaining item is CMD_3 at index 2", "CMD_3", q.peek());
        assertEquals("Size is 1", 1, q.size());

        // Enqueue 2 items to trigger wraparound: rear will wrap from index 2 to 0 and 1
        q.enqueue("CMD_4"); // wraps to index 0
        q.enqueue("CMD_5"); // goes to index 1
        assertTrue("Queue is full again after wrapped enqueue", q.isFull());
        assertEquals("Size is 3 after wrapped enqueue", 3, q.size());

        // Verify elements come out in strict FIFO order despite wraparound
        assertEquals("Wrapped dequeue 1 is CMD_3", "CMD_3", q.dequeue());
        assertEquals("Wrapped dequeue 2 is CMD_4", "CMD_4", q.dequeue());
        assertEquals("Wrapped dequeue 3 is CMD_5", "CMD_5", q.dequeue());
        assertTrue("Queue is empty after draining wrapped items", q.isEmpty());

        // 6. Clear and reuse
        q.enqueue("EVENT_A");
        q.enqueue("EVENT_B");
        assertEquals("Size is 2 before clear", 2, q.size());
        q.clear();
        assertTrue("isEmpty is true after clear()", q.isEmpty());
        assertEquals("Size is 0 after clear()", 0, q.size());

        q.enqueue("EVENT_REUSED");
        assertEquals("Can enqueue after clear", "EVENT_REUSED", q.dequeue());
        assertTrue("Queue empty after dequeuing reused item", q.isEmpty());

        // 7. Null safety check
        boolean caughtNull = false;
        try {
            q.enqueue(null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue("Null items rejected with IllegalArgumentException", caughtNull);

        System.out.println("=== Result: " + testsPassed + " / " + testsRun + " passed! ===");
    }
}

package aircanvas.ds;

/**
 * Custom Bounded Circular-Array Stack for Undo History.
 *
 * Stores references to StrokeList.StrokeNode up to a strict capacity limit
 * (default 3 undoable strokes).
 *
 * Behavior on Overflow:
 * When a stroke is pushed while full, the OLDEST history entry is evicted
 * to make room for the newest stroke. The evicted stroke remains visible on
 * the canvas (in StrokeList) but can no longer be undone.
 *
 * Zero Java Collections Framework classes used.
 *
 * Time Complexities:
 *  - push(node): O(1)
 *  - pop():      O(1)
 *  - peek():     O(1)
 *  - isEmpty():  O(1)
 *  - isFull():   O(1)
 *  - size():     O(1)
 *  - clear():    O(capacity)
 */
public class UndoStack {
    public static final int DEFAULT_CAPACITY = 3;

    private final StrokeList.StrokeNode[] data;
    private final int capacity;
    private int bottom; // Points to the oldest element in the stack
    private int size;   // Current number of elements in the stack

    public UndoStack() {
        this(DEFAULT_CAPACITY);
    }

    public UndoStack(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0: " + capacity);
        }
        this.capacity = capacity;
        this.data = new StrokeList.StrokeNode[capacity];
        this.bottom = 0;
        this.size = 0;
    }

    /**
     * Pushes a stroke node onto the undo stack in O(1) time.
     *
     * If the stack is full, the oldest history entry is discarded
     * (advancing the bottom pointer) and overwritten with the new entry.
     * The discarded stroke is NOT removed from the visible StrokeList.
     *
     * @param node the StrokeNode to record in undo history
     * @return the evicted StrokeNode if overflow occurred, or null otherwise
     */
    public StrokeList.StrokeNode push(StrokeList.StrokeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot push null StrokeNode onto UndoStack");
        }
        StrokeList.StrokeNode evicted = null;
        if (size < capacity) {
            int topIndex = (bottom + size) % capacity;
            data[topIndex] = node;
            size++;
        } else {
            // Full: evict oldest entry at bottom
            evicted = data[bottom];
            data[bottom] = node;
            bottom = (bottom + 1) % capacity;
        }
        return evicted;
    }

    /**
     * Pops and returns the most recently pushed stroke node in O(1) time.
     *
     * @return the top StrokeNode
     * @throws IllegalStateException if the stack is empty
     */
    public StrokeList.StrokeNode pop() {
        if (isEmpty()) {
            throw new IllegalStateException("Undo stack is empty");
        }
        int topIndex = (bottom + size - 1) % capacity;
        StrokeList.StrokeNode topNode = data[topIndex];
        data[topIndex] = null; // Clean reference
        size--;
        return topNode;
    }

    /**
     * Inspects the top stroke node without removing it in O(1) time.
     *
     * @return the top StrokeNode
     * @throws IllegalStateException if the stack is empty
     */
    public StrokeList.StrokeNode peek() {
        if (isEmpty()) {
            throw new IllegalStateException("Undo stack is empty");
        }
        int topIndex = (bottom + size - 1) % capacity;
        return data[topIndex];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == capacity;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    /**
     * Clears undo history and cleans object references.
     */
    public void clear() {
        for (int i = 0; i < capacity; i++) {
            data[i] = null;
        }
        bottom = 0;
        size = 0;
    }
}

package aircanvas.ds;

/**
 * Custom Fixed-Capacity Circular-Array Queue.
 *
 * Implements a strict First-In, First-Out (FIFO) queue using a fixed-size array
 * and modulo arithmetic for pointer wraparound.
 *
 * Zero Java Collections Framework classes used.
 *
 * Invariants:
 *  1. 0 <= size <= capacity
 *  2. elements[front] is the oldest element when size > 0
 *  3. elements[rear] is the slot where the next element will be enqueued
 *  4. Both front and rear wrap around: (index + 1) % capacity
 *  5. Removed slots are cleared to null to prevent memory retention
 *
 * Time Complexity:
 *  - enqueue(item): O(1)
 *  - dequeue():     O(1)
 *  - peek():        O(1)
 *  - isEmpty():     O(1)
 *  - isFull():      O(1)
 *  - size():        O(1)
 *  - clear():       O(capacity)
 */
public class CircularArrayQueue<T> {
    private final Object[] data;
    private final int capacity;
    private int front;
    private int rear;
    private int size;

    public CircularArrayQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0: " + capacity);
        }
        this.capacity = capacity;
        this.data = new Object[capacity];
        this.front = 0;
        this.rear = 0;
        this.size = 0;
    }

    /**
     * Enqueues an item at the rear of the queue.
     *
     * Policy on full queue:
     * Throws IllegalStateException rather than silently overwriting.
     * Silent overwrite is acceptable in a rolling window stabilizer (like CircularBuffer),
     * but unacceptable in an event queue where discrete commands (e.g. UNDO, CLEAR)
     * must never be dropped or corrupted silently.
     *
     * @param item the item to insert (must not be null)
     * @throws IllegalStateException if the queue is full
     * @throws IllegalArgumentException if item is null
     */
    public void enqueue(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Null items are not allowed in CircularArrayQueue");
        }
        if (isFull()) {
            throw new IllegalStateException("Queue is full (capacity: " + capacity + ")");
        }
        data[rear] = item;
        rear = (rear + 1) % capacity;
        size++;
    }

    /**
     * Non-throwing alternative to enqueue: returns false if full.
     *
     * @param item the item to insert
     * @return true if successfully added, false if the queue is full
     */
    public boolean offer(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Null items are not allowed in CircularArrayQueue");
        }
        if (isFull()) {
            return false;
        }
        data[rear] = item;
        rear = (rear + 1) % capacity;
        size++;
        return true;
    }

    /**
     * Removes and returns the oldest item from the front of the queue.
     *
     * @return the dequeued item
     * @throws IllegalStateException if the queue is empty
     */
    @SuppressWarnings("unchecked")
    public T dequeue() {
        if (isEmpty()) {
            throw new IllegalStateException("Queue is empty");
        }
        T item = (T) data[front];
        data[front] = null; // Prevent memory retention
        front = (front + 1) % capacity;
        size--;
        return item;
    }

    /**
     * Non-throwing alternative to dequeue: returns null if empty.
     *
     * @return the dequeued item, or null if empty
     */
    @SuppressWarnings("unchecked")
    public T poll() {
        if (isEmpty()) {
            return null;
        }
        T item = (T) data[front];
        data[front] = null;
        front = (front + 1) % capacity;
        size--;
        return item;
    }

    /**
     * Inspects the oldest item without removing it.
     *
     * @return the front item
     * @throws IllegalStateException if the queue is empty
     */
    @SuppressWarnings("unchecked")
    public T peek() {
        if (isEmpty()) {
            throw new IllegalStateException("Queue is empty");
        }
        return (T) data[front];
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
     * Clears all elements, frees object references, and resets pointers.
     */
    public void clear() {
        for (int i = 0; i < capacity; i++) {
            data[i] = null;
        }
        front = 0;
        rear = 0;
        size = 0;
    }
}

package aircanvas.ds;


public class CircularBuffer<T> {
private final Object[] data;
private final int capacity;
private int head;
private int size;
// Array-based Circular Buffer for gesture confirmation & flicker reduction.
// Zero Java Collections Framework classes used.
  public CircularBuffer(int capacity) {
            if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0: " + capacity);
        }
        this.capacity = capacity;
        this.data = new Object[capacity];
        this.head = 0;
        this.size = 0;
  }
//    O(1) insertion. Overwrites oldest element if full.
    public void add(T item) {
        if (size < capacity) {
            int writeIndex = (head + size) % capacity;
            data[writeIndex] = item;
            size++;
        } else {
            data[head] = item;
            head = (head + 1) % capacity;
        }
    }
     @SuppressWarnings("unchecked")
    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size);
        }
        int physicalIndex = (head + index) % capacity;
        return (T) data[physicalIndex];
    }
        public T getNewest() {
        if (size == 0) return null;
        return get(size - 1);
    }
    public T getOldest() {
        if (size == 0) return null;
        return get(0);
    }
    public int size() {
        return size;
    }
    public int capacity() {
        return capacity;
    }
    public boolean isFull() {
        return size == capacity;
    }
    public boolean isEmpty() {
        return size == 0;
    }
    public void clear() {
        for (int i = 0; i < capacity; i++) {
            data[i] = null;
        }
        head = 0;
        size = 0;
    }


    @SuppressWarnings("unchecked")
    public T getStableElement(int minThreshold) {
        if (size < minThreshold || size == 0) {
            return null;
        }
        Object[] uniqueItems = new Object[size];
        int[] counts = new int[size];
        int uniqueCount = 0;
        for (int i = 0; i < size; i++) {
            T item = get(i);
            if (item == null) continue;
            int foundIdx = -1;
            for (int u = 0; u < uniqueCount; u++) {
                if (item.equals(uniqueItems[u])) {
                    foundIdx = u;
                    break;
                }
            }
            if (foundIdx != -1) {
                counts[foundIdx]++;
            } else {
                uniqueItems[uniqueCount] = item;
                counts[uniqueCount] = 1;
                uniqueCount++;
            }
        }
int maxCount = 0;
        T dominant = null;
        for (int u = 0; u < uniqueCount; u++) {
            if (counts[u] >= minThreshold && counts[u] > maxCount) {
                maxCount = counts[u];
                dominant = (T) uniqueItems[u];
            }
        }
        return dominant;
    }
}
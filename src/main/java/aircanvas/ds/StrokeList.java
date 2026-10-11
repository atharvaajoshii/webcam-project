package aircanvas.ds;

/**
 * Custom Doubly Linked List of Visible Canvas Strokes.
 *
 * Manages all visible strokes on the authoritative canvas.
 * Maintains head, tail, and size pointers to enable:
 *  - O(1) appending of newly finished strokes
 *  - O(1) removal of strokes (e.g. on UNDO) when the node is known
 *  - O(1) restoration of removed strokes (e.g. on REDO) without copying point lists
 *  - O(N) forward rendering traversal in drawing order
 *  - O(N) backward traversal
 *
 * Zero Java Collections Framework classes or arrays used.
 *
 * Invariants:
 *  1. When size == 0: head == null, tail == null.
 *  2. When size == 1: head == tail != null, head.prev == null, head.next == null.
 *  3. When size >= 2:
 *     - head.prev == null, tail.next == null
 *     - for any node between head and tail: node.next.prev == node and node.prev.next == node.
 *  4. Removal and restoration do not duplicate or reallocate Stroke objects or points.
 */
public class StrokeList {

    /**
     * Doubly linked node wrapping a Stroke.
     * Keeps the structural list references (prev, next) separate from the Stroke data model.
     */
    public static class StrokeNode {
        public final Stroke stroke;
        public StrokeNode prev;
        public StrokeNode next;

        // Position memory to allow O(1) restoration to exact position
        private StrokeNode savedPrev;
        private StrokeNode savedNext;

        public StrokeNode(Stroke stroke) {
            if (stroke == null) {
                throw new IllegalArgumentException("Stroke cannot be null");
            }
            this.stroke = stroke;
            this.prev = null;
            this.next = null;
            this.savedPrev = null;
            this.savedNext = null;
        }

        public Stroke getStroke() {
            return stroke;
        }

        public StrokeNode getPrev() {
            return prev;
        }

        public StrokeNode getNext() {
            return next;
        }
    }

    private StrokeNode head;
    private StrokeNode tail;
    private int size;

    public StrokeList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Appends a new stroke to the end of the visible stroke list in O(1) time.
     *
     * @param stroke the Stroke to append
     * @return the created StrokeNode wrapping the stroke
     */
    public StrokeNode append(Stroke stroke) {
        if (stroke == null) {
            throw new IllegalArgumentException("Cannot append null stroke");
        }
        StrokeNode node = new StrokeNode(stroke);
        appendNode(node);
        return node;
    }

    /**
     * Appends an existing StrokeNode to the tail in O(1) time.
     * Reuses the existing node and its point list without copying.
     *
     * @param node the StrokeNode to append
     */
    public void appendNode(StrokeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot append null node");
        }
        node.prev = tail;
        node.next = null;

        if (tail == null) {
            head = node;
            tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /**
     * Removes the specified StrokeNode from the doubly linked list in O(1) time.
     * Remembers its previous and next links to enable O(1) restoration.
     *
     * @param node the StrokeNode to remove
     */
    public void remove(StrokeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot remove null node");
        }
        // Save link context for O(1) restoration
        node.savedPrev = node.prev;
        node.savedNext = node.next;

        if (node == head) {
            head = node.next;
        } else if (node.prev != null) {
            node.prev.next = node.next;
        }

        if (node == tail) {
            tail = node.prev;
        } else if (node.next != null) {
            node.next.prev = node.prev;
        }

        node.prev = null;
        node.next = null;
        size--;
    }

    /**
     * Removes and returns the tail (most recent) stroke node in O(1) time.
     *
     * @return the removed tail node, or null if list is empty
     */
    public StrokeNode removeLast() {
        if (tail == null) {
            return null;
        }
        StrokeNode last = tail;
        remove(last);
        return last;
    }

    /**
     * Restores a previously removed StrokeNode back into the list in O(1) time.
     * Reattaches the node at its recorded position without copying any points.
     *
     * @param node the previously removed StrokeNode
     */
    public void restore(StrokeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot restore null node");
        }
        if (node.savedPrev == null) {
            // It was previously at head
            node.prev = null;
            node.next = head;
            if (head != null) {
                head.prev = node;
            } else {
                tail = node;
            }
            head = node;
            size++;
        } else {
            // Reinsert right after savedPrev
            StrokeNode pred = node.savedPrev;
            node.prev = pred;
            node.next = pred.next;
            if (pred.next != null) {
                pred.next.prev = node;
            } else {
                tail = node;
            }
            pred.next = node;
            size++;
        }
    }

    public StrokeNode getHead() {
        return head;
    }

    public StrokeNode getTail() {
        return tail;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears all strokes, unlinking all nodes and resetting size.
     */
    public void clear() {
        StrokeNode current = head;
        while (current != null) {
            StrokeNode next = current.next;
            current.prev = null;
            current.next = null;
            current = next;
        }
        head = null;
        tail = null;
        size = 0;
    }
}

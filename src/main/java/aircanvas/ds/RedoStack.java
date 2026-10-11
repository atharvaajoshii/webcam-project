package aircanvas.ds;

/**
 * Custom Linked-Node Stack for Redo History.
 *
 * Implements an unbounded LIFO stack storing references to previously
 * undone StrokeList.StrokeNode instances.
 *
 * Zero Java Collections Framework classes or arrays used.
 *
 * Time Complexities:
 *  - push(node): O(1)
 *  - pop():      O(1)
 *  - peek():     O(1)
 *  - isEmpty():  O(1)
 *  - size():     O(1)
 *  - clear():    O(1)
 */
public class RedoStack {

    /**
     * Singly linked node representing a frame on the redo stack.
     */
    private static class Node {
        final StrokeList.StrokeNode strokeNode;
        final Node next;

        Node(StrokeList.StrokeNode strokeNode, Node next) {
            this.strokeNode = strokeNode;
            this.next = next;
        }
    }

    private Node top;
    private int size;

    public RedoStack() {
        this.top = null;
        this.size = 0;
    }

    /**
     * Pushes an undone StrokeNode onto the redo stack in O(1) time.
     *
     * @param node the StrokeNode to record
     */
    public void push(StrokeList.StrokeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot push null StrokeNode onto RedoStack");
        }
        top = new Node(node, top);
        size++;
    }

    /**
     * Pops and returns the most recently undone stroke node in O(1) time.
     *
     * @return the top StrokeNode
     * @throws IllegalStateException if the stack is empty
     */
    public StrokeList.StrokeNode pop() {
        if (isEmpty()) {
            throw new IllegalStateException("Redo stack is empty");
        }
        StrokeList.StrokeNode result = top.strokeNode;
        top = top.next;
        size--;
        return result;
    }

    /**
     * Inspects the top stroke node without removing it in O(1) time.
     *
     * @return the top StrokeNode
     * @throws IllegalStateException if the stack is empty
     */
    public StrokeList.StrokeNode peek() {
        if (isEmpty()) {
            throw new IllegalStateException("Redo stack is empty");
        }
        return top.strokeNode;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    /**
     * Clears all redo history in O(1) time.
     * In the drawing engine, this must be invoked whenever a new stroke is drawn.
     */
    public void clear() {
        top = null;
        size = 0;
    }
}

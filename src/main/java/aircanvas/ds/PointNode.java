package aircanvas.ds;

/**
 * Node in a singly linked list representing a single 2D point (x, y)
 * along a continuous brush stroke.
 *
 * Zero Java Collections Framework classes used.
 */
public class PointNode {
    public int x;
    public int y;
    public PointNode next;

    public PointNode(int x, int y) {
        this.x = x;
        this.y = y;
        this.next = null;
    }

    public PointNode(int x, int y, PointNode next) {
        this.x = x;
        this.y = y;
        this.next = next;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public PointNode getNext() {
        return next;
    }

    public void setNext(PointNode next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}

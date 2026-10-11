package aircanvas.ds;

import java.awt.Color;

/**
 * Represents an individual drawing stroke.
 *
 * Manages an internal singly linked list of PointNode objects with both
 * head and tail pointers to ensure O(1) appending of incoming trajectory points.
 *
 * Zero Java Collections Framework classes used.
 *
 * Invariants:
 *  1. When empty: head == null, tail == null, pointCount == 0.
 *  2. When single-point: head == tail != null, head.next == null, pointCount == 1.
 *  3. When pointCount >= 2: head != null, tail != null, tail.next == null,
 *     and traversing 'next' references from head reaches tail in exactly (pointCount - 1) steps.
 *  4. Adding a point always appends to tail in O(1) time.
 */
public class Stroke {
    private PointNode head;
    private PointNode tail;
    private int pointCount;

    private Color color;
    private int thickness;

    public Stroke() {
        this(Color.BLACK, 4);
    }

    public Stroke(Color color, int thickness) {
        this.color = (color != null) ? color : Color.BLACK;
        this.thickness = Math.max(1, thickness);
        this.head = null;
        this.tail = null;
        this.pointCount = 0;
    }

    /**
     * Appends a new point (x, y) to the end of the stroke.
     * Guaranteed O(1) time complexity via the tail pointer.
     *
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void addPoint(int x, int y) {
        PointNode newNode = new PointNode(x, y);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;
            tail = newNode;
        }
        pointCount++;
    }

    /**
     * Appends an existing PointNode to the end of the stroke.
     * Guaranteed O(1) time complexity via the tail pointer.
     *
     * @param node PointNode to append
     */
    public void addPoint(PointNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot add null PointNode to Stroke");
        }
        node.next = null; // Ensure node does not drag external links
        if (head == null) {
            head = node;
            tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        pointCount++;
    }

    public PointNode getHead() {
        return head;
    }

    public PointNode getTail() {
        return tail;
    }

    public int getPointCount() {
        return pointCount;
    }

    public boolean isEmpty() {
        return pointCount == 0;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = (color != null) ? color : Color.BLACK;
    }

    public int getThickness() {
        return thickness;
    }

    public void setThickness(int thickness) {
        this.thickness = Math.max(1, thickness);
    }

    /**
     * Resets the stroke's linked list of points.
     */
    public void clear() {
        head = null;
        tail = null;
        pointCount = 0;
    }
}

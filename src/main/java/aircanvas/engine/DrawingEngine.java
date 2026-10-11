package aircanvas.engine;

import aircanvas.ds.Stroke;
import aircanvas.ds.StrokeList;
import aircanvas.ds.UndoStack;
import aircanvas.ds.RedoStack;

import java.awt.Color;

/**
 * Authoritative Drawing Engine Coordinator.
 *
 * Coordinates stroke creation, point streaming, canvas state (StrokeList),
 * and history management (UndoStack, RedoStack).
 *
 * Zero Java Collections Framework classes used.
 * UI- and network-agnostic core logic.
 */
public class DrawingEngine {

    private final StrokeList strokeList;
    private final UndoStack undoStack;
    private final RedoStack redoStack;

    private Stroke activeStroke;
    private Color currentColor;
    private int currentThickness;

    public DrawingEngine() {
        this.strokeList = new StrokeList();
        this.undoStack = new UndoStack();
        this.redoStack = new RedoStack();
        this.activeStroke = null;
        this.currentColor = Color.BLACK;
        this.currentThickness = 4;
    }

    /**
     * Begins a new stroke with current color and thickness.
     * If an active stroke with points already exists, it is finished first
     * to prevent orphaned points.
     */
    public void beginStroke() {
        beginStroke(this.currentColor, this.currentThickness);
    }

    /**
     * Begins a new stroke with specified color and thickness.
     */
    public void beginStroke(Color color, int thickness) {
        if (activeStroke != null && !activeStroke.isEmpty()) {
            finishStroke();
        }
        this.activeStroke = new Stroke(color, thickness);
    }

    /**
     * Appends a coordinate point (x, y) to the currently active stroke.
     * Safely ignored if no stroke is currently active.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @return true if point was added, false if no active stroke existed
     */
    public boolean addPoint(int x, int y) {
        if (activeStroke == null) {
            return false;
        }
        activeStroke.addPoint(x, y);
        return true;
    }

    /**
     * Completes the active stroke:
     *  1. Appends it to StrokeList (canvas visibility)
     *  2. Pushes its node onto UndoStack (history, max 3)
     *  3. Clears RedoStack (new drawing invalidates redo history)
     *  4. Clears active stroke reference to prevent duplicate commits.
     *
     * Safely ignored if no active stroke exists or if the stroke has no points.
     *
     * @return true if a stroke was committed, false if no-op
     */
    public boolean finishStroke() {
        if (activeStroke == null) {
            return false;
        }
        if (activeStroke.isEmpty()) {
            activeStroke = null;
            return false;
        }

        Stroke finished = activeStroke;
        activeStroke = null; // Prevent accidental duplicate finish calls

        StrokeList.StrokeNode node = strokeList.append(finished);
        undoStack.push(node);
        redoStack.clear();
        return true;
    }

    /**
     * Reverts the most recent undoable stroke in O(1) time:
     *  - Pops from UndoStack
     *  - Removes from visible StrokeList
     *  - Pushes onto RedoStack
     *
     * Safe no-op if UndoStack is empty.
     *
     * @return true if an undo occurred, false if history was empty
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        StrokeList.StrokeNode node = undoStack.pop();
        strokeList.remove(node);
        redoStack.push(node);
        return true;
    }

    /**
     * Restores the most recently undone stroke in O(1) time:
     *  - Pops from RedoStack
     *  - Restores to visible StrokeList (without copying points)
     *  - Pushes back onto UndoStack
     *
     * Safe no-op if RedoStack is empty.
     *
     * @return true if a redo occurred, false if history was empty
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        StrokeList.StrokeNode node = redoStack.pop();
        strokeList.restore(node);
        undoStack.push(node);
        return true;
    }

    /**
     * Clears all visible strokes from the canvas and resets both undo and redo histories.
     */
    public void clear() {
        strokeList.clear();
        undoStack.clear();
        redoStack.clear();
        activeStroke = null;
    }

    // --- Accessors for Canvas Rendering & State Inspection ---

    /**
     * Provides access to the head node of visible strokes for canvas painting.
     *
     * @return head StrokeNode of StrokeList
     */
    public StrokeList.StrokeNode getVisibleStrokesHead() {
        return strokeList.getHead();
    }

    /**
     * Provides access to the stroke currently being drawn (in-flight before finish).
     *
     * @return active Stroke, or null if no stroke is in progress
     */
    public Stroke getActiveStroke() {
        return activeStroke;
    }

    public boolean hasActiveStroke() {
        return activeStroke != null;
    }

    public int getVisibleStrokeCount() {
        return strokeList.size();
    }

    public int getUndoCount() {
        return undoStack.size();
    }

    public int getRedoCount() {
        return redoStack.size();
    }

    public Color getCurrentColor() {
        return currentColor;
    }

    public void setCurrentColor(Color currentColor) {
        this.currentColor = (currentColor != null) ? currentColor : Color.BLACK;
    }

    public int getCurrentThickness() {
        return currentThickness;
    }

    public void setCurrentThickness(int currentThickness) {
        this.currentThickness = Math.max(1, currentThickness);
    }
}

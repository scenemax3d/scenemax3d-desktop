package com.scenemax.designer.ui.layout;

/**
 * The computed rectangle for a widget after layout resolution.
 * All values are in pixels relative to the parent container's origin (top-left).
 */
public class LayoutRect {

    public float x;      // left edge
    public float y;      // top edge
    public float width;
    public float height;

    public LayoutRect() {}

    public LayoutRect(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getRight() { return x + width; }
    public float getBottom() { return y + height; }
    public float getCenterX() { return x + width / 2; }
    public float getCenterY() { return y + height / 2; }

    @Override
    public String toString() {
        return "LayoutRect[x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + "]";
    }
}

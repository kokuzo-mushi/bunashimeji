package com.group_finity.mascot.type;

/**
 * Platform-agnostic rectangle record.
 * Replaces java.awt.Rectangle in Core Logic.
 */
public record NeoRect(int x, int y, int width, int height) {
    public int left() {
        return x;
    }

    public int top() {
        return y;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }
}

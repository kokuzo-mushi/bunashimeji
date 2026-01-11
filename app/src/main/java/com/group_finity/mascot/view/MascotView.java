package com.group_finity.mascot.view;

import java.awt.Point;

/**
 * マスコットの表示（View）に関するインターフェース。
 * Swing (AWT Window) と Compose (Headless/Adapter) の両方に対応するために導入。
 */
public interface MascotView {
    void setVisible(boolean b);

    void draw();

    int getMascotWidth();

    int getMascotHeight();

    boolean isVisible();

    Point getAnchor();
}
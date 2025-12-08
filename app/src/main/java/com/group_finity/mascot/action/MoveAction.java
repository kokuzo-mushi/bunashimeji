package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.config.xml.XmlPoint;
import java.awt.Point;

/**
 * マスコットを指定された座標まで移動させるアクション。
 * このアクションは、MascotクラスにgetAnchor()とsetAnchor(Point)メソッドが存在することを前提とします。
 */
public class MoveAction implements Action {

    private final Point target;
    private final int duration;
    private long startTime = -1;
    private Point startPoint;

    public MoveAction(XmlPoint target, int duration) {
        this.target = new Point(target.getX(), target.getY());
        this.duration = Math.max(0, duration); // 負のdurationを防ぐ
    }

    @Override
    public void execute(Mascot mascot) {
        if (startTime == -1) {
            startTime = System.currentTimeMillis();
            startPoint = mascot.getAnchor();
        }

        if (duration == 0) {
            mascot.setAnchor(target);
            return;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        double progress = Math.min((double) elapsedTime / duration, 1.0);

        int newX = (int) (startPoint.x + (target.x - startPoint.x) * progress);
        int newY = (int) (startPoint.y + (target.y - startPoint.y) * progress);

        mascot.setAnchor(new Point(newX, newY));
    }

    @Override
    public boolean hasNext() {
        if (duration == 0) return false;
        if (startTime == -1) return true;
        long elapsedTime = System.currentTimeMillis() - startTime;
        return elapsedTime < duration;
    }
}
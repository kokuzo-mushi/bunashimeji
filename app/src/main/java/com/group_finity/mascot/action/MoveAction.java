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

    // アクション開始時の座標
    private Point startPoint;
    // アクションの内部的な経過時間
    private int timeElapsed;
    // アクションが完了したかどうかを示すフラグ
    private boolean isFinished;

    public MoveAction(XmlPoint target, int duration) {
        this.target = new Point(target.getX(), target.getY());
        this.duration = Math.max(0, duration); // 負のdurationを防ぐ
        this.timeElapsed = 0;
        this.isFinished = false;
    }

    @Override
    public void execute(Mascot mascot) {
        if (this.isFinished) {
            return;
        }

        // 初回実行時に開始座標を記録
        if (this.startPoint == null) {
            startPoint = mascot.getAnchor();
        }

        // durationが0の場合は、即座に移動して終了
        if (duration == 0) {
            mascot.setAnchor(target);
            this.isFinished = true;
            return;
        }

        // 1フレームの時間を40msと仮定 (25 FPS)
        final int FRAME_DURATION_MS = 40;
        this.timeElapsed += FRAME_DURATION_MS;

        // 進捗率を計算 (0.0 ~ 1.0)
        double progress = Math.min((double) this.timeElapsed / this.duration, 1.0);

        int newX = (int) (startPoint.x + (target.x - startPoint.x) * progress);
        int newY = (int) (startPoint.y + (target.y - startPoint.y) * progress);
        mascot.setAnchor(new Point(newX, newY));

        if (progress >= 1.0) {
            this.isFinished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !this.isFinished;
    }
}
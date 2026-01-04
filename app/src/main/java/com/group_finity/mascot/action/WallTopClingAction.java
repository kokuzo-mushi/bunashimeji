package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * 壁の頂上にしがみつくアクション。
 * 物理演算を無視して、壁の上端に座標を固定します。
 */
public class WallTopClingAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private boolean initialized = false;
    private Point clingPosition;

    public WallTopClingAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!initialized) {
            initialize(mascot);
        }

        if (!hasNext()) return;

        // 物理演算を無効化して、Main.javaによる天井吸着を防ぐ
        mascot.setIgnoreWalls(true);

        // 計算したしがみつき位置に座標を固定
        mascot.setX(clingPosition.x);
        mascot.setY(clingPosition.y);

        // 向きを壁側に向ける
        if (mascot.isHittingLeftWall()) {
            mascot.setLookRight(false);
        } else if (mascot.isHittingRightWall()) {
            mascot.setLookRight(true);
        }

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        this.timeRemaining -= FRAME_DURATION_MS;

        if (this.timeRemaining <= 0) {
            // 終了時に物理演算を再度有効化
            mascot.setIgnoreWalls(false);
        }
    }

    private void initialize(Mascot mascot) {
        // 壁の上端に頭が接するY座標を計算
        int clingY = mascot.getY(); // 現在のY座標を基準とする
        this.clingPosition = new Point(mascot.getX(), clingY);
        initialized = true;
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        if (animation != null) animation.reset();
        this.initialized = false;
    }
}
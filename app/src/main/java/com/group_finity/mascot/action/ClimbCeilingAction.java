package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

/**
 * 天井（Y=45）まで登るアクション。
 * 壁登りから天井アクションへ移行する際に使用します。
 */
public class ClimbCeilingAction implements Action {

    private final Animation animation;
    private final int speed;
    private final int duration;
    private int timeRemaining;
    private static final int TARGET_Y = ActionConstants.WALL_ANCHOR.y(); // 壁の上端で頭が接する位置（足元基準）

    public ClimbCeilingAction(Animation animation, int speed, int duration) {
        this.animation = animation;
        this.speed = speed;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext())
            return;

        // Main.javaによる天井吸着（Y=10への強制移動）を防ぐため、壁判定を無視する
        mascot.setIgnoreWalls(true);

        // 壁の方向を向く
        if (mascot.isHittingRightWall()) {
            mascot.setLookRight(true);
        } else if (mascot.isHittingLeftWall()) {
            mascot.setLookRight(false);
        }

        final int FRAME_DURATION_MS = 16;
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 目標Y座標に向かって移動
        // 物理演算を切っているので、現在地に関わらず強制的に近づける
        if (mascot.getY() != TARGET_Y) {
            int diff = TARGET_Y - mascot.getY();
            int move = (Math.abs(diff) > speed) ? (int) Math.signum(diff) * speed : diff;
            mascot.setY(mascot.getY() + move);
        }

        this.timeRemaining -= FRAME_DURATION_MS;

        // 目標Y座標に到達したら終了
        if (mascot.getY() == TARGET_Y) {
            mascot.setY(TARGET_Y);
            // ここでは setIgnoreWalls(false) を呼ばない！
            // 次の CeilingEnterAction が始まるまでの1フレームの隙間で吸着されるのを防ぐため
            timeRemaining = 0;
        }
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        if (animation != null)
            animation.reset();
    }
}

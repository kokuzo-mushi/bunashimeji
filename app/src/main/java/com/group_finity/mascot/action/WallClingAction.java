package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

/**
 * 壁にしがみつくアクション。
 * 速度をゼロにしてその場に留まります。
 */
public class WallClingAction implements Action {
    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private boolean initialized = false;

    public WallClingAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!initialized) {
            mascot.setVelocityX(0);
            mascot.setVelocityY(0);

            // 壁の方向に向き直る
            if (mascot.isHittingLeftWall()) {
                mascot.setLookRight(false);
            } else if (mascot.isHittingRightWall()) {
                mascot.setLookRight(true);
            }
            initialized = true;
        }

        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        this.animation.reset();
        this.initialized = false;
    }
}
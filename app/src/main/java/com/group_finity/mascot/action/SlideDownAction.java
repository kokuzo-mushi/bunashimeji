package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

/**
 * 壁に沿って滑り落ちるアクション。
 * 指定された速度で下方向に移動します。
 */
public class SlideDownAction implements Action {

    private final Animation animation;
    private final int speed;
    private int timeRemaining;

    public SlideDownAction(Animation animation, int speed) {
        this.animation = animation;
        this.speed = speed;
        this.timeRemaining = this.animation.getTotalDuration();
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;
        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        mascot.setY(mascot.getY() + speed);
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.animation.getTotalDuration();
        this.animation.reset();
    }
}
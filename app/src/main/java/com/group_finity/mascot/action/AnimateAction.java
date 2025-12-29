package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

public class AnimateAction implements Action {
    private final Animation animation;
    private int timeRemaining;

    public AnimateAction(Animation animation) {
        this.animation = animation;
        this.timeRemaining = this.animation.getTotalDuration();
    }

    @Override
    public void execute(Mascot mascot) {
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
        this.timeRemaining = this.animation.getTotalDuration();
        this.animation.reset();
    }

    public Animation getAnimation() {
        return animation;
    }
}
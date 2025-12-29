package com.group_finity.mascot.action;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

/**
 * 地面を掘って消えるアクション。
 * アニメーション終了時にマスコット自身を削除します。
 */
public class DigAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;

    public DigAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        this.timeRemaining -= FRAME_DURATION_MS;

        if (this.timeRemaining <= 0) {
            Main.getInstance().removeMascot(mascot);
        }
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        this.animation.reset();
    }
}
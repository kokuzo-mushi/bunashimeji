package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import java.util.Random;

/**
 * 寝転がるアクション。
 * 指定されたDurationを最大値として、ランダムな時間だけ継続します。
 */
public class LieDownAction implements Action {

    private final Animation animation;
    private final int maxDuration;
    private int timeRemaining;
    private final Random random = new Random();

    public LieDownAction(Animation animation, int duration) {
        this.animation = animation;
        this.maxDuration = duration > 0 ? duration : 2000; // デフォルト2秒
        reset();
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 16;
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
        // 最小でも1秒、最大でmaxDurationの間でランダムに決定
        int minDuration = 1000;
        int range = Math.max(1, maxDuration - minDuration);
        this.timeRemaining = minDuration + random.nextInt(range);
        
        this.animation.reset();
    }
}
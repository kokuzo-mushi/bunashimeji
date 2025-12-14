package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 寝転がるアクション。
 * 指定されたDurationを最大値として、ランダムな時間だけ継続します。
 */
public class LieDownAction implements Action {

    private final Animation animation;
    private final int maxDuration;
    private int timeRemaining;
    private final Random random = new Random();

    public LieDownAction(XmlAnimation xmlAnimation, int duration) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                        .collect(Collectors.toList())
        );
        this.maxDuration = duration > 0 ? duration : 2000; // デフォルト2秒
        reset();
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
        // 最小でも1秒、最大でmaxDurationの間でランダムに決定
        int minDuration = 1000;
        int range = Math.max(1, maxDuration - minDuration);
        this.timeRemaining = minDuration + random.nextInt(range);
        
        this.animation.reset();
    }
}
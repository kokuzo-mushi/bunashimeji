package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * 壁に沿って滑り落ちるアクション。
 * 指定された速度で下方向に移動します。
 */
public class SlideDownAction implements Action {

    private final Animation animation;
    private final int speed;
    private int timeRemaining;

    public SlideDownAction(XmlAnimation xmlAnimation, int speed) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                        .collect(Collectors.toList())
        );
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
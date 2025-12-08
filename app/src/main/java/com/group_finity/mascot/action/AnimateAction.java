package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * マスコットにアニメーションを適用するアクション。
 * このアクションは、MascotクラスにsetAnimation(Animation)メソッドが存在することを前提とします。
 */
public class AnimateAction implements Action {

    private final Animation animation;
    private long startTime = -1;

    public AnimateAction(XmlAnimation xmlAnimation) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void execute(Mascot mascot) {
        if (startTime == -1) {
            startTime = System.currentTimeMillis();
        }

        mascot.setAnimation(animation);

        long elapsedTime = System.currentTimeMillis() - startTime;
        animation.tick(elapsedTime);
    }

    @Override
    public boolean hasNext() {
        if (startTime == -1) return true;
        long elapsedTime = System.currentTimeMillis() - startTime;
        return elapsedTime < animation.getTotalDuration();
    }
}
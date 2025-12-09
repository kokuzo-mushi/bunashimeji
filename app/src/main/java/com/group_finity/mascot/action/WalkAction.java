package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * アニメーションを再生しながら、水平方向に一定時間歩き続けるアクション。
 * 移動方向はマスコットの向き(isLookRight)に依存します。
 */
public class WalkAction implements Action {

    private final Animation animation;
    private final int speed;
    private long startTime = -1;

    public WalkAction(XmlAnimation xmlAnimation, int speed) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration()))
                        .collect(Collectors.toList())
        );
        this.speed = speed;
    }

    @Override
    public boolean hasNext() {
        if (startTime == -1) return true;
        long elapsedTime = System.currentTimeMillis() - startTime;
        // アニメーションが終了するまで継続
        return elapsedTime < animation.getTotalDuration();
    }

    @Override
    public void execute(Mascot mascot) {
        if (startTime == -1) {
            startTime = System.currentTimeMillis();
        }

        mascot.setAnimation(animation);
        long elapsedTime = System.currentTimeMillis() - startTime;
        animation.tick(elapsedTime);

        int direction = mascot.isLookRight() ? 1 : -1;
        mascot.setX(mascot.getX() + speed * direction);
    }
}
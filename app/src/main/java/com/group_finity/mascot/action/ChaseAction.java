package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.stream.Collectors;

/**
 * マウスカーソルを追いかけるアクション。
 */
public class ChaseAction implements Action {

    private final Animation animation;
    private final int speed;
    private final int duration;
    private int timeRemaining;
    private final int targetDistance = 5; // 追いついたとみなす距離

    public ChaseAction(XmlAnimation xmlAnimation, int speed, int duration) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                        .collect(Collectors.toList())
        );
        this.speed = speed;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);

        // マウス位置の取得
        Point mousePos = MouseInfo.getPointerInfo().getLocation();
        int distance = mousePos.x - mascot.getX();

        // 向きの更新と移動
        if (Math.abs(distance) >= targetDistance) {
            mascot.setLookRight(distance > 0);
            int move = (distance > 0) ? speed : -speed;
            mascot.setX(mascot.getX() + move);
        } else {
            // 追いついたら終了
            timeRemaining = 0;
        }

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
    }
}
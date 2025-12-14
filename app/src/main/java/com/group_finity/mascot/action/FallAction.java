package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

public class FallAction implements Action {
    private final Animation animation;
    private int velocityY = 0;
    private boolean finished = false;

    public FallAction(XmlAnimation xmlAnimation) {
        if (xmlAnimation != null) {
            this.animation = new Animation(
                    xmlAnimation.getPoses().stream()
                            .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration()))
                            .collect(Collectors.toList())
            );
        } else {
            this.animation = null;
        }
    }

    @Override
    public void execute(Mascot mascot) {
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(40);
        }

        // 接地していたら終了フラグを立てる
        if (mascot.isGrounded()) {
            finished = true;
            return;
        }

        // 加速しながら落下 (簡易物理)
        if (velocityY < 20) velocityY += 2; // 重力加速度
        mascot.setY(mascot.getY() + velocityY);
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public void reset() {
        velocityY = 0;
        finished = false;
        if (animation != null) animation.reset();
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

public class JumpAction implements Action {
    private final Animation animation;
    private final int initialVelocityY;
    private final int velocityX;
    private int currentVelocityY;
    private boolean finished = false;

    public JumpAction(XmlAnimation xmlAnimation, int velocityY, int velocityX) {
        if (xmlAnimation != null) {
            this.animation = new Animation(
                    xmlAnimation.getPoses().stream()
                            .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                            .collect(Collectors.toList())
            );
        } else {
            this.animation = null;
        }
        // XMLのVelocityYは正の値で指定されることが多いが、画面座標系では上方向はマイナス
        this.initialVelocityY = -Math.abs(velocityY);
        this.velocityX = velocityX;
        this.currentVelocityY = this.initialVelocityY;
    }

    @Override
    public void execute(Mascot mascot) {
        // 壁に衝突したらアクションを終了する
        if (mascot.isHittingLeftWall() || mascot.isHittingRightWall() || mascot.isHittingCeiling()) {
            finished = true;
            return;
        }

        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(40);
        }

        // 終了判定: 上昇中(currentVelocityY < 0)は接地していても継続。
        // 落下中(currentVelocityY >= 0)かつ接地していたら終了。
        if (currentVelocityY >= 0 && mascot.isGrounded()) {
            finished = true;
            return;
        }

        // 接地判定はMainループの補正で行われるため、ここでは座標更新を行う
        // Y軸の更新（重力適用）
        mascot.setY(mascot.getY() + currentVelocityY);
        if (currentVelocityY < 20) currentVelocityY += 2; // 重力

        // X軸の更新（向きに合わせて移動）
        int direction = mascot.isLookRight() ? 1 : -1;
        mascot.setX(mascot.getX() + (velocityX * direction));
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public void reset() {
        this.currentVelocityY = this.initialVelocityY;
        this.finished = false;
        if (animation != null) animation.reset();
    }
}
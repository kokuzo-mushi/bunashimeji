package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * 壁を蹴って反対方向にジャンプするアクション。
 */
public class WallJumpAction implements Action {
    private final Animation animation;
    private final int initialVelocityY;
    private final int velocityX;
    private int currentVelocityY;
    private boolean finished = false;
    private int direction = 0;
    private boolean initialized = false;

    public WallJumpAction(XmlAnimation xmlAnimation, int velocityY, int velocityX) {
        if (xmlAnimation != null) {
            this.animation = new Animation(
                    xmlAnimation.getPoses().stream()
                            .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                            .collect(Collectors.toList())
            );
        } else {
            this.animation = null;
        }
        // 上方向への初速
        this.initialVelocityY = -Math.abs(velocityY);
        this.velocityX = Math.abs(velocityX);
        this.currentVelocityY = this.initialVelocityY;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!initialized) {
            // 初回実行時にジャンプ方向を決定（左壁なら右へ、それ以外なら左へ）
            this.direction = mascot.isHittingLeftWall() ? 1 : -1;
            // 飛ぶ方向に向きを変える
            mascot.setLookRight(this.direction > 0);
            initialized = true;
        }

        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(40);
        }

        // 接地したら終了
        if (currentVelocityY >= 0 && mascot.isGrounded()) {
            finished = true;
            return;
        }

        // 進んでいる方向の壁にぶつかったら終了（張り付きアクションへ移行するため）
        if ((direction > 0 && mascot.isHittingRightWall()) || 
            (direction < 0 && mascot.isHittingLeftWall())) {
            finished = true;
            return;
        }

        // Y軸の更新（重力適用）
        mascot.setY(mascot.getY() + currentVelocityY);
        if (currentVelocityY < 20) currentVelocityY += 2;

        // X軸の更新（決定した方向へ移動）
        mascot.setX(mascot.getX() + (velocityX * direction));
    }

    @Override
    public boolean hasNext() { return !finished; }

    @Override
    public void reset() {
        this.currentVelocityY = this.initialVelocityY;
        this.finished = false;
        this.initialized = false;
        if (animation != null) animation.reset();
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * マスコットが分裂して増えるアクション。
 * アニメーション終了時に新しいマスコットを生成します。
 */
public class BreedAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private final int bornX;
    private final int bornY;
    private final int bornVelocityX;
    private final int bornVelocityY;
    private boolean born = false;

    public BreedAction(XmlAnimation xmlAnimation, int duration, int bornX, int bornY, int bornVelocityX, int bornVelocityY) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                        .collect(Collectors.toList())
        );
        this.duration = duration;
        this.timeRemaining = duration;
        this.bornX = bornX;
        this.bornY = bornY;
        this.bornVelocityX = bornVelocityX;
        this.bornVelocityY = bornVelocityY;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        this.timeRemaining -= FRAME_DURATION_MS;

        // アクション終了時に分裂
        if (this.timeRemaining <= 0 && !born) {
            Main main = Main.getInstance();
            if (main != null) {
                // 指定されたオフセット位置と初速で新しいマスコットを生成
                // 左右の向きに合わせてX方向のオフセットと速度を反転させる
                int direction = mascot.isLookRight() ? 1 : -1;
                int x = mascot.getX() + (bornX * direction);
                int y = mascot.getY() + bornY;
                int vx = bornVelocityX * direction;
                int vy = bornVelocityY; // Y速度（上方向）は向きに関係なくそのまま
                
                main.createMascot(x, y, vx, vy);
            }
            born = true;
        }
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        this.animation.reset();
        this.born = false;
    }
}

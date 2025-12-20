package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 天井を伝って歩くアクション。
 */
public class CeilingCrawlAction implements Action {

    private final Animation animation;
    private final int speed;
    private final Random random = new Random();
    private final int duration;
    private int timeRemaining;
    private int elapsedFrames = 0;
    private boolean finished = false;

    public CeilingCrawlAction(XmlAnimation xmlAnimation, int speed, int duration) {
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
        if (finished) return;

        elapsedFrames++;

        // 1. 壁にぶつかったら終了（WallActionへ移行させるため）
        if (mascot.isHittingLeftWall() || mascot.isHittingRightWall()) {
            finished = true;
            return;
        }

        // 2. 一定時間経過後にランダムで落下
        // 最初の100フレーム（約4秒）は落下しない
        if (elapsedFrames > 100) {
            // 1/200の確率で落下
            if (random.nextInt(200) == 0) {
                mascot.setY(mascot.getY() + 1); // 天井判定を外す
                finished = true;
                return;
            }
        }

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        int move = mascot.isLookRight() ? speed : -speed;
        mascot.setX(mascot.getX() + move);

        // 時間経過で終了
        this.timeRemaining -= FRAME_DURATION_MS;
        if (this.timeRemaining <= 0) {
            finished = true;
        }
    }

    @Override
    public boolean hasNext() { return !finished; }

    @Override
    public void reset() {
        this.elapsedFrames = 0;
        this.finished = false;
        this.timeRemaining = this.duration;
        this.animation.reset();
    }
}
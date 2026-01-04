package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import java.util.Random;

/**
 * その場に留まるアクション。
 * 地上ではマウスを見たりし、天井ではぶら下がって向きを調整する。
 */
public class StayAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private final Random random = new Random();

    public StayAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = this.duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 16;
        
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 天井にいる場合の特別処理 (CeilingStayActionのロジックを統合)
        if (mascot.isHittingCeiling()) {
            // 壁にぶつかったら向きを変える
            if (mascot.isHittingRightWall() && mascot.isLookRight()) {
                mascot.setLookRight(false);
            } else if (mascot.isHittingLeftWall() && !mascot.isLookRight()) {
                mascot.setLookRight(true);
            }

            // 低確率で落下する (約10秒に1回)
            if (random.nextInt(250) == 0) {
                mascot.setY(mascot.getY() + 1); // 天井判定を外す
                timeRemaining = 0;
            }
        }

        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        if (this.animation != null) {
            this.animation.reset();
        }
    }

    public Animation getAnimation() {
        return animation;
    }
}
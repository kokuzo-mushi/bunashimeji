package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import java.util.Random;

/**
 * 壁の下端などで、次にどうするか迷って停止するアクション。
 * 一定時間経過後、ランダムに「登る」か「落ちる」ための状態を作って終了する。
 */
public class WallHesitationAction implements Action {

    private final Animation animation;
    private final int duration;
    private int time;

    public WallHesitationAction(Animation animation, int duration) {
        this.animation = animation;
        // durationが未設定(0)の場合、アニメーションの長さを採用する安全装置
        this.duration = duration > 0 ? duration : (animation != null ? animation.getTotalDuration() : 2000);
    }

    @Override
    public void execute(Mascot mascot) {
        final int FRAME_DURATION_MS = 16;

        if (time == 0) {
            mascot.setAnimation(animation);
        }

        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }
        time += FRAME_DURATION_MS;

        if (time >= duration) {
            // 迷い時間が終了したら、次の行動をランダムに決定するための「きっかけ」を作る
            Random random = new Random();
            
            if (random.nextBoolean()) {
                // 登る: 下端の安全装置を確実に抜け、登り直せる位置まで一気に上へ移動
                mascot.setY(mascot.getY() - 50);
            } else {
                // 落ちる: ウィンドウ壁の強力な吸着判定を完全に抜け、空中に放り出すため大きく横へ移動
                if (mascot.isHittingLeftWall()) {
                    mascot.setX(mascot.getX() + 150);
                    mascot.setHittingLeftWall(false);
                } else {
                    // 右壁または壁不明の場合（安全のため左方向へ移動）
                    mascot.setX(mascot.getX() - 150);
                    mascot.setHittingRightWall(false);
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        return time < duration;
    }

    @Override
    public void reset() {
        time = 0;
        if (animation != null) {
            animation.reset();
        }
    }
}
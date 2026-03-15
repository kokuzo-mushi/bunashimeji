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
        this.duration = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        final int FRAME_DURATION_MS = 40;

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
                // 登る: 少し上に移動させて、次のフレームで ClimbAction の条件にヒットさせる
                mascot.setY(mascot.getY() - 4);
            } else {
                // 落ちる: 壁判定を解除し、少し壁から離して FallAction の条件にヒットさせる
                if (mascot.isHittingLeftWall()) {
                    mascot.setX(mascot.getX() + 4);
                    mascot.setHittingLeftWall(false);
                } else if (mascot.isHittingRightWall()) {
                    mascot.setX(mascot.getX() - 4);
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
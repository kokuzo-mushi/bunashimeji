package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;

/**
 * ウィンドウの端でバランスをとるアクション。
 */
public class TeeterAction implements Action {
    private final Animation animation;
    private final int duration;
    private int time = 0;
    private final double fallProbability;

    public TeeterAction(Animation animation, int duration, double fallProbability) {
        this.animation = animation;
        this.duration = duration;
        this.fallProbability = fallProbability;
    }

    @Override
    public void execute(Mascot mascot) {
        if (time == 0) {
            // 初回実行時、近い方の端（崖下）を向くように調整
            if (mascot.getFloorWindow() != null) {
                NeoRect rect = NativeWindowUtil.getWindowRect(mascot.getFloorWindow());

                int distLeft = Math.abs(mascot.getX() - rect.left());
                int distRight = Math.abs(mascot.getX() - rect.right());

                if (distLeft < distRight) {
                    // 左端に近い -> 左を向く
                    mascot.setLookRight(false);
                } else {
                    // 右端に近い -> 右を向く
                    mascot.setLookRight(true);
                }
            }
        }

        mascot.setAnimation(animation);
        animation.tick(40);
        time += 40;

        // アクション終了時に確率で落下させる
        if (time >= duration) {
            // バランスを崩して落ちる確率 (0.0 〜 1.0)
            if (Math.random() < this.fallProbability) {
                // 向いている方向（崖側）に少しずらして、床から落とす
                int shift = mascot.isLookRight() ? 10 : -10;
                mascot.setX(mascot.getX() + shift);
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
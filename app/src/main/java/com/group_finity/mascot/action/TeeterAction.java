package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.util.stream.Collectors;

/**
 * ウィンドウの端でバランスをとるアクション。
 */
public class TeeterAction implements Action {
    private final Animation animation;
    private final int duration;
    private int time = 0;

    public TeeterAction(XmlAnimation xmlAnimation, int duration) {
        this.animation = new Animation(xmlAnimation.getPoses().stream()
                .map(p -> new Pose(p.getImage(), p.getDuration(), p.getImageAnchorPoint()))
                .collect(Collectors.toList()));
        this.duration = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (time == 0) {
            // 初回実行時、近い方の端（崖下）を向くように調整
            if (mascot.getFloorWindow() != null) {
                RECT rect = new RECT();
                if (Win32.INSTANCE.GetWindowRect(mascot.getFloorWindow(), rect) != 0) {
                    int distLeft = Math.abs(mascot.getX() - rect.left);
                    int distRight = Math.abs(mascot.getX() - rect.right);
                    
                    if (distLeft < distRight) {
                        // 左端に近い -> 左を向く
                        mascot.setLookRight(false);
                    } else {
                        // 右端に近い -> 右を向く
                        mascot.setLookRight(true);
                    }
                }
            }
        }
        
        mascot.setAnimation(animation);
        animation.tick(40);
        time += 40;

        // アクション終了時に確率で落下させる
        if (time >= duration) {
            // 20%の確率でバランスを崩して落ちる
            if (Math.random() < 0.2) {
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
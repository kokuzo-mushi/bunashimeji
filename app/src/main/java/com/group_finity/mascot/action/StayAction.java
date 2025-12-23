package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 待機アクション。
 * 指定された時間だけアニメーションを再生しながら待機し、時々方向転換します。
 */
public class StayAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private final Random random = new Random();
    private int lookingAtMouseTime = 0;

    public StayAction(XmlAnimation xmlAnimation, int duration) {
        if (xmlAnimation != null) {
            this.animation = new Animation(
                    xmlAnimation.getPoses().stream()
                            .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                            .collect(Collectors.toList())
            );
        } else {
            this.animation = null;
        }
        this.duration = duration;
        reset(); // 初期化時にもランダム時間を設定
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        final int FRAME_DURATION_MS = 40;
        
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 天井に張り付いている場合、低確率で落下する
        if (mascot.isHittingCeiling()) {
            // 約10秒に1回 (250フレームに1回) の確率で落下
            if (random.nextInt(250) == 0) {
                mascot.setY(mascot.getY() + 1); // 天井判定を外すためにY座標を少し下げる
                timeRemaining = 0; // アクションを強制終了して、次のフレームでFallビヘイビアへ移行させる
                return;
            }
        }

        // マウス追従モードの処理
        if (lookingAtMouseTime > 0) {
            Point mousePos = MouseInfo.getPointerInfo().getLocation();
            if (mousePos != null) {
                // マウスが自分より右にあれば右を向く
                mascot.setLookRight(mousePos.x > mascot.getX());
            }
            lookingAtMouseTime -= FRAME_DURATION_MS;
        } else {
            // 通常時のランダム方向転換: 約8秒に1回 (200フレームに1回)
            if (random.nextInt(200) == 0) {
                // 検証のため一時的に無効化
                // mascot.setLookRight(!mascot.isLookRight());
            }
            // 低確率でマウス追従モードに入る: 約10秒に1回 (250フレームに1回)
            if (random.nextInt(250) == 0) {
                lookingAtMouseTime = 2000; // 2秒間見つめる
            }
        }

        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.lookingAtMouseTime = 0;
        // 設定時間の ±20% の範囲でランダム化
        int variance = this.duration / 5;
        if (variance > 0) {
            this.timeRemaining = this.duration - variance + random.nextInt(variance * 2);
        } else {
            this.timeRemaining = this.duration;
        }

        if (this.animation != null) {
            this.animation.reset();
        }
    }
}
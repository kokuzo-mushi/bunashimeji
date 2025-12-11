package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * マスコットにアニメーションを適用するアクション。
 * このアクションは、MascotクラスにsetAnimation(Animation)メソッドが存在することを前提とします。
 */
public class AnimateAction implements Action {

    private final Animation animation;
    // startTimeへの依存をなくし、アクションの残り時間を内部で管理します。
    private int timeRemaining;

    public AnimateAction(XmlAnimation xmlAnimation) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration()))
                        .collect(Collectors.toList())
        );
        // アクション生成時に、アニメーションの総再生時間を残り時間として初期設定します。
        this.timeRemaining = this.animation.getTotalDuration();
    }

    @Override
    public void execute(Mascot mascot) {
        // アクションが既に終了している場合は何もしない
        if (!hasNext()) {
            return;
        }

        // 1フレームの時間を40msと仮定 (25 FPS)
        final int FRAME_DURATION_MS = 40;

        mascot.setAnimation(animation);

        // Animationクラスに経過時間を渡してアニメーションを進行させます。
        animation.tick(FRAME_DURATION_MS);

        // 1フレーム分の時間を減算します。
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() {
        // 残り時間がある限り、アクションは継続します。
        return this.timeRemaining > 0;
    }
}
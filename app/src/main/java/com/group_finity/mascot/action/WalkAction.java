package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * アニメーションを再生しながら、水平方向に一定時間歩き続けるアクション。
 * 移動方向はマスコットの向き(isLookRight)に依存します。
 */
public class WalkAction implements Action {

    private final Animation animation;
    private final int speed;

    // テスト容易性を向上させるため、System.currentTimeMillis()への依存をなくし、
    // アクションの残り時間を内部で管理します。
    private int timeRemaining;

    public WalkAction(XmlAnimation xmlAnimation, int speed) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration()))
                        .collect(Collectors.toList())
        );
        this.speed = speed;
        // アクション生成時に、アニメーションの総再生時間を残り時間として初期設定します。
        this.timeRemaining = this.animation.getTotalDuration();
    }

    @Override
    public boolean hasNext() {
        // 残り時間がある限り、アクションは継続します。
        return this.timeRemaining > 0;
    }

    @Override
    public void execute(Mascot mascot) {
        // このメソッドはメインループから毎フレーム呼び出される「tick」として機能します。
        if (!hasNext()) {
            return;
        }

        // テストコード(WalkActionTest)やメインループ(Main)の待機時間と合わせるのが理想です。
        // ここではテストで仮定されている40msを1フレームの時間とします。
        final int FRAME_DURATION_MS = 40;

        mascot.setAnimation(animation);

        // Animationクラスに経過時間を渡してアニメーションを進行させます。
        animation.tick(FRAME_DURATION_MS);

        int direction = mascot.isLookRight() ? 1 : -1;
        mascot.setX(mascot.getX() + speed * direction);

        // 1フレーム分の時間を減算します。
        this.timeRemaining -= FRAME_DURATION_MS;
    }
}
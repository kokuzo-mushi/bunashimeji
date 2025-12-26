package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
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
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
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

        // 接地していない場合はアクションを終了する（落下させるため）
        if (!mascot.isGrounded()) {
            this.timeRemaining = 0;
            return;
        }

        // 壁にぶつかっている、かつその壁に向かって歩いている場合はアクションを終了する
        // これにより、次のフレームでWallAction（壁しがみつき等）へ遷移できるようになる
        if ((mascot.isHittingLeftWall() && !mascot.isLookRight()) ||
            (mascot.isHittingRightWall() && mascot.isLookRight())) {
            this.timeRemaining = 0;
            return;
        }

        // テストコード(WalkActionTest)やメインループ(Main)の待機時間と合わせるのが理想です。
        // ここではテストで仮定されている40msを1フレームの時間とします。
        final int FRAME_DURATION_MS = 40;

        mascot.setAnimation(animation);

        // Animationクラスに経過時間を渡してアニメーションを進行させます。
        animation.tick(FRAME_DURATION_MS);

        int direction = mascot.isLookRight() ? 1 : -1;
        int nextX = mascot.getX() + speed * direction;

        // 床の端チェック: 次の一歩で床から落ちるなら止まる
        if (mascot.isGrounded()) {
            HWND floor = mascot.getFloorWindow();
            if (floor != null && Win32.INSTANCE.IsWindow(floor)) {
                RECT rect = new RECT();
                Win32.INSTANCE.GetWindowRect(floor, rect);
                if (nextX <= rect.left || nextX >= rect.right) {
                    // 端ギリギリまで移動して止まる（確実にisOnEdge判定させるため）
                    int edgeX = (direction > 0) ? rect.right - 5 : rect.left + 5;
                    mascot.setX(edgeX);
                    
                    this.timeRemaining = 0;
                    return;
                }
            }
        }

        mascot.setX(nextX);

        // 1フレーム分の時間を減算します。
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.animation.getTotalDuration();
        this.animation.reset();
    }
}
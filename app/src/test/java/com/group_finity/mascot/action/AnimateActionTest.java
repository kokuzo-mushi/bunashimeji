package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.config.xml.XmlPose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * AnimateActionクラスのユニットテスト。
 * 時間管理が内部で行われ、規定時間後にアクションが終了することを検証します。
 */
class AnimateActionTest {

    private Mascot mockMascot;
    private XmlAnimation mockXmlAnimation;

    @BeforeEach
    void setUp() {
        // Mascotオブジェクトのモックを作成
        mockMascot = mock(Mascot.class);

        // テスト用のアニメーション定義を作成
        mockXmlAnimation = mock(XmlAnimation.class);
        XmlPose pose1 = mock(XmlPose.class);
        when(pose1.getDuration()).thenReturn(100); // 100ms
        when(pose1.getImage()).thenReturn("test1.png");

        XmlPose pose2 = mock(XmlPose.class);
        when(pose2.getDuration()).thenReturn(150); // 150ms
        when(pose2.getImage()).thenReturn("test2.png");

        // AnimateActionのコンストラクタが必要とするPoseのリストを返すように設定
        when(mockXmlAnimation.getPoses()).thenReturn(List.of(pose1, pose2));
    }

    @Test
    void execute_shouldSetAnimationOnMascot() {
        // Arrange
        AnimateAction animateAction = new AnimateAction(mockXmlAnimation);

        // Act
        animateAction.execute(mockMascot);

        // Assert
        // MascotのsetAnimationメソッドが、Animationクラスのインスタンスを引数として呼び出されたことを確認
        verify(mockMascot).setAnimation(any(Animation.class));
    }

    @Test
    void hasNext_shouldReturnFalse_afterAnimationDuration() {
        // Arrange
        AnimateAction animateAction = new AnimateAction(mockXmlAnimation);
        // アニメーションの合計時間: 100ms + 150ms = 250ms
        int totalDuration = 250;
        // 1フレームの時間(ms)
        int frameDuration = 40;
        // アニメーションが終了するのに必要なフレーム数: (250 / 40) + 1 = 6 + 1 = 7 フレーム
        int requiredFrames = (totalDuration / frameDuration) + 1;

        // Act & Assert
        assertTrue(animateAction.hasNext(), "アクションは開始直後は継続しているはず");

        // 規定フレーム数分、executeを呼び出して時間を進める
        for (int i = 0; i < requiredFrames; i++) {
            animateAction.execute(mockMascot);
        }

        assertFalse(animateAction.hasNext(), "アクションは規定時間経過後に終了しているはず");
    }
}
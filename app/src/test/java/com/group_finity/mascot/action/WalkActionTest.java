package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.config.xml.XmlPose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WalkActionTest {

    private Mascot mockMascot;
    private XmlAnimation mockXmlAnimation;

    @BeforeEach
    void setUp() {
        // Mascotオブジェクトのモック（偽物）を作成
        mockMascot = mock(Mascot.class);

        // テスト用のアニメーション定義を作成
        mockXmlAnimation = mock(XmlAnimation.class);
        XmlPose pose1 = mock(XmlPose.class);
        when(pose1.getDuration()).thenReturn(300);
        when(pose1.getImage()).thenReturn("test1.png");

        XmlPose pose2 = mock(XmlPose.class);
        when(pose2.getDuration()).thenReturn(300);
        when(pose2.getImage()).thenReturn("test2.png");

        // WalkActionのコンストラクタが必要とするPoseのリストを返すように設定
        when(mockXmlAnimation.getPoses()).thenReturn(List.of(pose1, pose2));
    }

    @Test
    void execute_shouldMoveMascotToTheRight_whenLookingRight() {
        // Arrange: 準備
        WalkAction walkAction = new WalkAction(mockXmlAnimation, 2); // Speed = 2
        when(mockMascot.isLookRight()).thenReturn(true); // 右を向いている
        when(mockMascot.getX()).thenReturn(100); // 現在のX座標は100

        // Act: 実行
        walkAction.execute(mockMascot);

        // Assert: 検証
        // X座標が 100 + 2 = 102 に設定されるはず
        verify(mockMascot).setX(102);
        // アニメーションが設定されるはず
        verify(mockMascot).setAnimation(any(Animation.class));
    }

    @Test
    void execute_shouldMoveMascotToTheLeft_whenLookingLeft() {
        // Arrange
        WalkAction walkAction = new WalkAction(mockXmlAnimation, 2);
        when(mockMascot.isLookRight()).thenReturn(false); // 左を向いている
        when(mockMascot.getX()).thenReturn(100);

        // Act
        walkAction.execute(mockMascot);

        // Assert
        // X座標が 100 - 2 = 98 に設定されるはず
        verify(mockMascot).setX(98);
    }

    @Test
    @Timeout(1) // テストが1秒以上かかったら失敗させる
    void hasNext_shouldReturnFalse_afterAnimationDuration() throws InterruptedException {
        // Arrange
        WalkAction walkAction = new WalkAction(mockXmlAnimation, 2);

        // Act & Assert
        walkAction.execute(mockMascot); // 1回目の実行でアクションが開始される
        assertTrue(walkAction.hasNext(), "アクションは開始直後は継続しているはず");

        Thread.sleep(650); // アニメーションの合計時間 (300 + 300 = 600ms) より長く待機

        assertFalse(walkAction.hasNext(), "アクションは規定時間経過後に終了しているはず");
    }
}
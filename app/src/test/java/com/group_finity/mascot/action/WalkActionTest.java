package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.config.xml.XmlPose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest(name = "向き={0}, 初期X座標={1}, 速度={2} のとき、期待されるX座標は {3}")
    @CsvSource({
            "true, 100, 2, 102", // 右向きの場合: 初期座標100, 速度2 -> 期待値102
            "false, 100, 2, 98"  // 左向きの場合: 初期座標100, 速度2 -> 期待値98
    })
    void execute_shouldMoveMascotInCorrectDirection(boolean isLookingRight, int initialX, int speed, int expectedX) {
        // Arrange: 準備
        WalkAction walkAction = new WalkAction(mockXmlAnimation, speed);
        when(mockMascot.isLookRight()).thenReturn(isLookingRight);
        when(mockMascot.getX()).thenReturn(initialX);

        // Act: 実行
        walkAction.execute(mockMascot);

        // Assert: 検証
        verify(mockMascot).setX(expectedX);
        verify(mockMascot).setAnimation(any(Animation.class));
    }

    @Test
    void hasNext_shouldReturnFalse_afterAnimationDuration() {
        // Arrange
        WalkAction walkAction = new WalkAction(mockXmlAnimation, 2);
        // アニメーションの合計時間(ms)を取得します。
        // このテストでは 300ms + 300ms = 600ms となります。
        int totalDuration = mockXmlAnimation.getPoses().stream().mapToInt(XmlPose::getDuration).sum();
        // 1フレームの時間(ms)を仮定します。多くのアプリケーションでは40ms(25fps)が使われます。
        int frameDuration = 40;
        // アニメーションが終了するのに必要なフレーム数を計算します（念のため+1します）。
        int requiredFrames = (totalDuration / frameDuration) + 1;

        // Act & Assert
        walkAction.execute(mockMascot); // 1回目の実行でアクションが開始される
        assertTrue(walkAction.hasNext(), "アクションは開始直後は継続しているはず");

        // Thread.sleep()の代わりに、Actionインターフェースの規約に従いexecute()を呼び出してアクションの内部時間を進めます。
        // これにより、テストが外部のタイミングに依存しなくなり、安定します。
        for (int i = 0; i < requiredFrames && walkAction.hasNext(); i++) {
            walkAction.execute(mockMascot);
        }

        assertFalse(walkAction.hasNext(), "アクションは規定時間経過後に終了しているはず");
    }
}
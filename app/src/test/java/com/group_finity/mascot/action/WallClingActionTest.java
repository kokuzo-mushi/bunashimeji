package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/**
 * WallClingAction のユニットテスト。
 * 壁に接触した際の向き（LookRight）の変更ロジックを検証します。
 */
class WallClingActionTest {

    private Mascot mockMascot;
    private Animation mockAnimation;
    private WallClingAction action;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
        mockAnimation = mock(Animation.class);
        // 継続時間 100ms でアクションを作成
        action = new WallClingAction(mockAnimation, 100);
    }

    @Test
    void execute_shouldLookLeft_whenHittingLeftWall() {
        // Arrange: 左壁に接触している状態をシミュレート
        when(mockMascot.isHittingLeftWall()).thenReturn(true);
        when(mockMascot.isHittingRightWall()).thenReturn(false);

        // Act: アクション実行
        action.execute(mockMascot);

        // Assert: 左向き (LookRight = false) に設定されることを検証
        verify(mockMascot).setLookRight(false);
    }

    @Test
    void execute_shouldLookRight_whenHittingRightWall() {
        // Arrange: 右壁に接触している状態をシミュレート
        when(mockMascot.isHittingLeftWall()).thenReturn(false);
        when(mockMascot.isHittingRightWall()).thenReturn(true);

        // Act: アクション実行
        action.execute(mockMascot);

        // Assert: 右向き (LookRight = true) に設定されることを検証
        verify(mockMascot).setLookRight(true);
    }

    @Test
    void execute_shouldStopVelocity() {
        // Arrange
        when(mockMascot.isHittingLeftWall()).thenReturn(true);

        // Act
        action.execute(mockMascot);

        // Assert: 速度が0にリセットされることを検証
        verify(mockMascot).setVelocityX(0);
        verify(mockMascot).setVelocityY(0);
    }
}

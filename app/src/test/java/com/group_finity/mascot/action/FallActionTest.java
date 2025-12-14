package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for FallAction.
 */
class FallActionTest {

    private Mascot mockMascot;
    private FallAction action;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
        action = new FallAction(null);
    }

    @Test
    void execute_shouldIncreaseY_whenInAir() {
        // Arrange
        when(mockMascot.isGrounded()).thenReturn(false);
        when(mockMascot.getY()).thenReturn(100);

        // Act
        action.execute(mockMascot);

        // Assert
        // Initial velocity 0 -> adds gravity (2) -> velocity 2. Y becomes 100 + 2 = 102.
        verify(mockMascot).setY(102);
        assertTrue(action.hasNext(), "Action should continue while in air");
    }

    @Test
    void execute_shouldFinish_whenOnGround() {
        // Arrange
        when(mockMascot.isGrounded()).thenReturn(true);

        // Act
        action.execute(mockMascot);

        // Assert
        assertFalse(action.hasNext(), "Action should finish when on ground");
    }

    @Test
    void execute_shouldInheritVelocity_andResetMascotVelocity() {
        // Arrange
        when(mockMascot.isGrounded()).thenReturn(false);
        when(mockMascot.getX()).thenReturn(100);
        when(mockMascot.getY()).thenReturn(100);
        
        // マスコットに初速(慣性)が設定されている状態をシミュレート
        // 例: 右下方向に強く投げられた状態
        when(mockMascot.getVelocityX()).thenReturn(50);
        when(mockMascot.getVelocityY()).thenReturn(10);

        // Act
        action.execute(mockMascot);

        // Assert
        // 1. マスコットの速度がリセットされたか検証 (次回以降の誤爆防止)
        verify(mockMascot).setVelocityX(0);
        verify(mockMascot).setVelocityY(0);

        // 2. 慣性が適用された座標更新が行われたか検証
        // X: 100 + 50 = 150
        // Y: 100 + 10 + 2(重力) = 112
        verify(mockMascot).setX(150);
        verify(mockMascot).setY(112);
    }

    @Test
    void execute_shouldDecelerateVelocityX() {
        // Arrange
        when(mockMascot.isGrounded()).thenReturn(false);
        when(mockMascot.getX()).thenReturn(100);
        when(mockMascot.getY()).thenReturn(100);
        
        // 初速 X=10
        when(mockMascot.getVelocityX()).thenReturn(10);
        when(mockMascot.getVelocityY()).thenReturn(0);

        // モックの座標更新をシミュレート (setXされた値を次回のgetXで返す)
        doAnswer(invocation -> {
            int newX = invocation.getArgument(0);
            when(mockMascot.getX()).thenReturn(newX);
            return null;
        }).when(mockMascot).setX(anyInt());

        // Act 1: 1フレーム目
        action.execute(mockMascot);
        // X移動: 100 + 10 = 110, 内部速度減衰: 10 -> 9
        verify(mockMascot).setX(110);

        // Act 2: 2フレーム目
        action.execute(mockMascot);
        // X移動: 110 + (int)(10 * 0.95) = 110 + 9 = 119
        verify(mockMascot).setX(119);
    }

    @ParameterizedTest(name = "Init(VX={0}, VY={1}) -> {2} frames later -> Pos({3}, {4})")
    @CsvSource({
        // initVX, initVY, frames, expectedX, expectedY
        "0, 0, 1, 0, 2",       // 自由落下 1F: VY=0->2, Y=0+2=2
        "0, 0, 2, 0, 6",       // 自由落下 2F: VY=2->4, Y=2+4=6
        "0, 0, 3, 0, 12",      // 自由落下 3F: VY=4->6, Y=6+6=12
        
        "10, 0, 1, 10, 2",     // 右へ慣性 1F: VX=10, X=10. VY=2, Y=2
        "10, 0, 2, 19, 6",     // 右へ慣性 2F: VX=9.5, X=10+9=19. VY=4, Y=6
        // 右へ慣性 5F: 
        // 1F: X+=10, VX=9.5
        // 2F: X+=9, VX=9.025
        // 3F: X+=9, VX=8.57...
        // 4F: X+=8, VX=8.14...
        // 5F: X+=8, VX=7.73...
        // Total X = 10+9+9+8+8 = 44
        "10, 0, 5, 44, 30",    
        
        "-10, 0, 1, -10, 2",   // 左へ慣性 1F: VX=-10, X=-10
        "-10, 0, 2, -19, 6",   // 左へ慣性 2F: VX=-9.5, X=-10-9=-19
        
        "0, -10, 1, 0, -8",    // 上へ慣性 1F: VY=-10->-8, Y=-8 (上昇)
        "0, -10, 5, 0, -20",   // 上へ慣性 5F: Y=0-8-6-4-2-0 = -20 (頂点到達)
        
        "25, 0, 1, 25, 2",     // X減衰チェック: 25 -> 23.75. X+=25.
        "0, 25, 1, 0, 25",     // Y終端速度チェック: VY=25 >= 20 なので加速なし. Y+=25.
    })
    void testTrajectorySimulation(int initVX, int initVY, int frames, int expectedX, int expectedY) {
        // Arrange
        // 座標と接地状態のシミュレーション用変数
        final int[] currentX = {0};
        final int[] currentY = {0};
        
        // getX/getY は現在の値を返す
        when(mockMascot.getX()).thenAnswer(i -> currentX[0]);
        when(mockMascot.getY()).thenAnswer(i -> currentY[0]);
        
        // setX/setY は変数を更新する
        doAnswer(i -> {
            currentX[0] = i.getArgument(0);
            return null;
        }).when(mockMascot).setX(anyInt());
        
        doAnswer(i -> {
            currentY[0] = i.getArgument(0);
            return null;
        }).when(mockMascot).setY(anyInt());

        // 常に空中にあるとする
        when(mockMascot.isGrounded()).thenReturn(false);

        // 初速の設定
        when(mockMascot.getVelocityX()).thenReturn(initVX);
        when(mockMascot.getVelocityY()).thenReturn(initVY);

        // Act: 指定フレーム数だけ実行
        for (int i = 0; i < frames; i++) {
            action.execute(mockMascot);
        }

        // Assert: 最終的な座標が期待通りか
        assertEquals(expectedX, currentX[0], "X coordinate mismatch after " + frames + " frames");
        assertEquals(expectedY, currentY[0], "Y coordinate mismatch after " + frames + " frames");
    }
}
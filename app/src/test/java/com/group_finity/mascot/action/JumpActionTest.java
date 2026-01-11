package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for JumpAction.
 */
class JumpActionTest {

    private Mascot mockMascot;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
    }

    @Test
    void execute_shouldMoveUpInitially() {
        // Arrange
        // Initial velocity -10 (Upward)
        JumpAction action = new JumpAction(null, -10, 0);
        when(mockMascot.getY()).thenReturn(100);
        when(mockMascot.isGrounded()).thenReturn(true); // Start on ground

        // Act
        action.execute(mockMascot);

        // Assert
        verify(mockMascot).setY(90); // 100 + (-10) = 90
        assertTrue(action.hasNext(), "Action should continue while jumping up");
    }

    @Test
    void execute_shouldApplyGravity() {
        // Arrange
        JumpAction action = new JumpAction(null, 0, 0); // Start at apex (velocity 0)
        when(mockMascot.getY()).thenReturn(100);
        when(mockMascot.isGrounded()).thenReturn(false);

        // Act 1 (Velocity 0 applied, then becomes 2)
        action.execute(mockMascot);
        verify(mockMascot).setY(100); // 100 + 0

        // Act 2 (Velocity 2 applied, then becomes 4)
        action.execute(mockMascot);
        verify(mockMascot).setY(102); // 100 + 2
    }

    @Test
    void execute_shouldFinish_whenLanding() throws Exception {
        // Arrange
        JumpAction action = new JumpAction(null, 10, 0); // Falling down
        
        // リフレクションを使って強制的に落下状態(速度正)にする
        java.lang.reflect.Field velocityField = JumpAction.class.getDeclaredField("currentVelocityY");
        velocityField.setAccessible(true);
        velocityField.setInt(action, 10); // 落下速度 10

        when(mockMascot.isGrounded()).thenReturn(true);

        // Act
        action.execute(mockMascot);

        // Assert
        assertFalse(action.hasNext(), "Action should finish when landing");
    }
}
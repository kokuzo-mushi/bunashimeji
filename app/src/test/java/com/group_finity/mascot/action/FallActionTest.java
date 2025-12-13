package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        action = new FallAction();
    }

    @Test
    void execute_shouldIncreaseY_whenInAir() {
        // Arrange
        when(mockMascot.isOnGround()).thenReturn(false);
        when(mockMascot.getY()).thenReturn(100);

        // Act
        action.execute(mockMascot);

        // Assert
        // Should fall by FALL_SPEED (4)
        verify(mockMascot).setY(104);
        assertTrue(action.hasNext(), "Action should continue while in air");
    }

    @Test
    void execute_shouldFinish_whenOnGround() {
        // Arrange
        when(mockMascot.isOnGround()).thenReturn(true);

        // Act
        action.execute(mockMascot);

        // Assert
        assertFalse(action.hasNext(), "Action should finish when on ground");
    }
}
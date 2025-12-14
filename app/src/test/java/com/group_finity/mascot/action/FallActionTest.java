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
}
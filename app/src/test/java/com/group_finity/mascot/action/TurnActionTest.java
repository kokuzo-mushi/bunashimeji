package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * Unit test for TurnAction.
 * Verifies that the action is executed once and correctly toggles the mascot's direction.
 */
class TurnActionTest {

    private Mascot mockMascot;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void execute_shouldToggleLookRight(boolean initialLookRight) {
        // Arrange
        when(mockMascot.isLookRight()).thenReturn(initialLookRight);
        TurnAction turnAction = new TurnAction();

        // Act
        turnAction.execute(mockMascot);

        // Assert
        // Verify that setLookRight() was called with the opposite value of isLookRight()
        verify(mockMascot).setLookRight(!initialLookRight);
    }

    @Test
    void hasNext_shouldReturnFalse_afterExecution() {
        // Arrange
        TurnAction turnAction = new TurnAction();
        turnAction.execute(mockMascot);

        // Act & Assert
        assertFalse(turnAction.hasNext(), "hasNext() should return false after execution");
    }
}
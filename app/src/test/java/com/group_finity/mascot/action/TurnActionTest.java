package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * TurnActionクラスのユニットテスト。
 * アクションが一度だけ実行され、マスコットの向きを正しく反転させることを検証します。
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
        // isLookRight()の返り値と逆の値でsetLookRight()が呼ばれたことを確認
        verify(mockMascot).setLookRight(!initialLookRight);
    }

    @Test
    void hasNext_shouldReturnFalse_afterExecution() {
        // Arrange
        TurnAction turnAction = new TurnAction();
        turnAction.execute(mockMascot);

        // Act & Assert
        assertFalse(turnAction.hasNext(), "execute実行後はhasNext()はfalseを返すはず");
    }
}
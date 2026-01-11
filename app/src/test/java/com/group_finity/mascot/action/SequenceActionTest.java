package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SequenceActionクラスのユニットテスト。
 * アクションが正しく順番に実行され、遅延なく切り替わることを検証します。
 */
class SequenceActionTest {

    private Mascot mockMascot;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
    }

    @Test
    void hasNext_shouldReturnFalse_forEmptySequence() {
        // Arrange
        SequenceAction sequenceAction = new SequenceAction(Collections.emptyList());

        // Act & Assert
        assertFalse(sequenceAction.hasNext(), "空のシーケンスは即座に終了するはず");
    }

    @Test
    void execute_shouldExecuteActionsInOrder() {
        // Arrange
        Action mockAction1 = mock(Action.class);
        Action mockAction2 = mock(Action.class);

        // action1は2回実行されたら終了する
        when(mockAction1.hasNext()).thenReturn(true, true, false);
        // action2は1回実行されたら終了する
        when(mockAction2.hasNext()).thenReturn(true, false);

        SequenceAction sequenceAction = new SequenceAction(List.of(mockAction1, mockAction2));
        InOrder inOrder = inOrder(mockAction1, mockAction2);

        // Act & Assert
        assertTrue(sequenceAction.hasNext(), "シーケンス開始時は継続中のはず");

        // 1回目の実行 -> action1が実行される
        sequenceAction.execute(mockMascot);
        inOrder.verify(mockAction1).execute(mockMascot);
        inOrder.verify(mockAction2, never()).execute(mockMascot);
        assertTrue(sequenceAction.hasNext());

        // 2回目の実行 -> action1が再度実行され、継続する
        sequenceAction.execute(mockMascot);
        inOrder.verify(mockAction1).execute(mockMascot);
        assertTrue(sequenceAction.hasNext());

        // 3回目の実行 -> action1が実行されて終了し、続けてaction2が実行され、継続する
        sequenceAction.execute(mockMascot);
        // この1回のexecute呼び出しで、action1の3回目とaction2の1回目が順に呼ばれる
        inOrder.verify(mockAction1).execute(mockMascot);
        inOrder.verify(mockAction2).execute(mockMascot);
        assertTrue(sequenceAction.hasNext());

        // 4回目の実行 -> action2が実行されて終了し、シーケンスも終了する
        sequenceAction.execute(mockMascot);
        inOrder.verify(mockAction2).execute(mockMascot);
        assertFalse(sequenceAction.hasNext(), "すべての内部アクションが終了したらシーケンスも終了するはず");
    }

    @Test
    void execute_shouldSwitchToActionImmediately_whenPreviousActionFinishesInstantly() {
        // Arrange
        Action instantAction1 = mock(Action.class);
        Action finalAction = mock(Action.class);

        // 即時終了するアクション
        when(instantAction1.hasNext()).thenReturn(false);
        // 最後の継続するアクション
        when(finalAction.hasNext()).thenReturn(true);

        SequenceAction sequenceAction = new SequenceAction(List.of(instantAction1, finalAction));

        // Act: 1回だけ実行する
        sequenceAction.execute(mockMascot);

        // Assert
        // 1回のexecute呼び出しの中で、即時終了アクションが処理され、
        // 次の継続アクションまで実行されていることを確認
        verify(instantAction1).execute(mockMascot);
        verify(finalAction).execute(mockMascot);
        assertTrue(sequenceAction.hasNext(), "最後の継続アクションが残っているのでシーケンスは継続中のはず");
    }
}
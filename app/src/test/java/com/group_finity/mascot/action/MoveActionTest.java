package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.config.xml.XmlPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MoveActionクラスのユニットテスト。
 * 時間管理が内部で行われ、指定されたdurationで正しく移動・終了することを検証します。
 */
class MoveActionTest {

    private Mascot mockMascot;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
        // マスコットの初期位置を設定
        when(mockMascot.getAnchor()).thenReturn(new Point(100, 100));
    }

    @Test
    void execute_shouldMoveToTargetImmediately_whenDurationIsZero() {
        // Arrange
        XmlPoint mockTargetXml = mock(XmlPoint.class);
        when(mockTargetXml.getX()).thenReturn(200);
        when(mockTargetXml.getY()).thenReturn(200);
        MoveAction moveAction = new MoveAction(mockTargetXml, 0);

        // Act
        moveAction.execute(mockMascot);

        // Assert
        // ターゲット座標に直接setAnchorが呼ばれたことを確認
        verify(mockMascot).setAnchor(new Point(200, 200));
        // アクションが即座に終了することを確認
        assertFalse(moveAction.hasNext(), "durationが0の場合、アクションは即座に終了するはず");
    }

    @Test
    void execute_shouldMoveIncrementally_whenDurationIsPositive() {
        // Arrange
        // (100, 100) -> (200, 200)
        XmlPoint mockTargetXml = mock(XmlPoint.class);
        when(mockTargetXml.getX()).thenReturn(200);
        when(mockTargetXml.getY()).thenReturn(200);
        MoveAction moveAction = new MoveAction(mockTargetXml, 100); // duration 100ms
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);

        // Act (Frame 1: 40ms elapsed)
        moveAction.execute(mockMascot);

        // Assert (Frame 1)
        // 40%進んだ位置 (100 + 100*0.4, 100 + 100*0.4) = (140, 140)
        verify(mockMascot, times(1)).setAnchor(pointCaptor.capture());
        assertEquals(new Point(140, 140), pointCaptor.getValue());
        assertTrue(moveAction.hasNext(), "アクションはまだ継続中のはず");

        // Act (Frame 2: 80ms elapsed)
        moveAction.execute(mockMascot);

        // Assert (Frame 2)
        // 80%進んだ位置 (100 + 100*0.8, 100 + 100*0.8) = (180, 180)
        verify(mockMascot, times(2)).setAnchor(pointCaptor.capture());
        assertEquals(new Point(180, 180), pointCaptor.getValue());
        assertTrue(moveAction.hasNext(), "アクションはまだ継続中のはず");
    }

    @Test
    void hasNext_shouldReturnFalse_afterDuration() {
        // Arrange
        XmlPoint mockTargetXml = mock(XmlPoint.class);
        when(mockTargetXml.getX()).thenReturn(200);
        when(mockTargetXml.getY()).thenReturn(200);
        MoveAction moveAction = new MoveAction(mockTargetXml, 100); // duration 100ms
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);

        // Act
        // Frame 1 (40ms)
        moveAction.execute(mockMascot);
        assertTrue(moveAction.hasNext());

        // Frame 2 (80ms)
        moveAction.execute(mockMascot);
        assertTrue(moveAction.hasNext());

        // Frame 3 (120ms) - durationを超過
        moveAction.execute(mockMascot);

        // Assert
        // 最終的にターゲット座標に到達していることを確認
        verify(mockMascot, atLeast(1)).setAnchor(pointCaptor.capture());
        assertEquals(new Point(200, 200), pointCaptor.getAllValues().get(pointCaptor.getAllValues().size() - 1));
        assertFalse(moveAction.hasNext(), "durationを超えたらアクションは終了するはず");
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StayActionTest {

    @Test
    void hasNext_shouldReturnTrue_withinDuration() {
        // Arrange
        int duration = 500; // 長めに設定して即時終了を防ぐ
        StayAction action = new StayAction(null, duration);
        Mascot mascot = new Mascot();

        // Act
        action.execute(mascot); // タイマー開始

        // Assert
        assertTrue(action.hasNext(), "指定時間内は true を返すべき");
    }

    @Test
    void hasNext_shouldReturnFalse_afterDuration() throws InterruptedException {
        // Arrange
        int duration = 50;
        StayAction action = new StayAction(null, duration);
        Mascot mascot = new Mascot();

        // Act
        // Act
        // Simulate frames instead of sleeping, as StayAction uses frame-based timing
        // (tick)
        for (int i = 0; i < 5; i++) {
            action.execute(mascot);
        }

        // Assert
        assertFalse(action.hasNext(), "指定時間経過後は false を返すべき");
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.config.xml.XmlAnimation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for DraggedAction.
 */
class DraggedActionTest {

    private Mascot mockMascot;
    private DraggedAction action;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);

        XmlAnimation mockAnimation = mock(XmlAnimation.class);
        when(mockAnimation.getPoses()).thenReturn(Collections.emptyList());
        action = new DraggedAction(mockAnimation);
    }

    @Test
    void execute_shouldContinue_whenBeingDragged() {
        // Arrange
        when(mockMascot.isBeingDragged()).thenReturn(true);

        // Act
        action.execute(mockMascot);

        // Assert
        assertTrue(action.hasNext(), "Action should continue while being dragged");
    }

    @Test
    void execute_shouldFinish_whenDragEnds() {
        // Arrange
        when(mockMascot.isBeingDragged()).thenReturn(false);

        // Act
        action.execute(mockMascot);

        // Assert
        assertFalse(action.hasNext(), "Action should finish when drag ends");
    }
}
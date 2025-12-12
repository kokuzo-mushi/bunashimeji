package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.config.xml.XmlPose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WalkActionTest {

    private Mascot mockMascot;
    private XmlAnimation mockXmlAnimation;

    @BeforeEach
    void setUp() {
        // Create a mock of the Mascot object
        mockMascot = mock(Mascot.class);

        // Create animation definition for testing
        mockXmlAnimation = mock(XmlAnimation.class);
        XmlPose pose1 = mock(XmlPose.class);
        when(pose1.getDuration()).thenReturn(300);
        when(pose1.getImage()).thenReturn("test1.png");

        XmlPose pose2 = mock(XmlPose.class);
        when(pose2.getDuration()).thenReturn(300);
        when(pose2.getImage()).thenReturn("test2.png");

        // Set up the mock to return the list of poses required by WalkAction constructor
        when(mockXmlAnimation.getPoses()).thenReturn(List.of(pose1, pose2));
    }

    @ParameterizedTest
    @CsvSource({
            "true, 100, 2, 102",
            "false, 100, 2, 98"
    })
    void execute_shouldMoveMascotInCorrectDirection(boolean isLookingRight, int initialX, int speed, int expectedX) {
        // Arrange: 準備
        WalkAction walkAction = new WalkAction(mockXmlAnimation, speed);
        when(mockMascot.isLookRight()).thenReturn(isLookingRight);
        when(mockMascot.getX()).thenReturn(initialX);

        // Act: 実行
        walkAction.execute(mockMascot);

        // Assert: 検証
        verify(mockMascot).setX(expectedX);
        verify(mockMascot).setAnimation(any(Animation.class));
    }

    @Test
    void hasNext_shouldReturnFalse_afterAnimationDuration() {
        // Arrange
        WalkAction walkAction = new WalkAction(mockXmlAnimation, 2);
        // Calculate total animation duration (300ms + 300ms = 600ms)
        int totalDuration = mockXmlAnimation.getPoses().stream().mapToInt(XmlPose::getDuration).sum();
        // Assume 1 frame duration (40ms)
        int frameDuration = 40;
        // Calculate required frames to finish animation
        int requiredFrames = (totalDuration / frameDuration) + 1;

        // Act & Assert
        walkAction.execute(mockMascot);
        assertTrue(walkAction.hasNext(), "Action should continue immediately after start");

        for (int i = 0; i < requiredFrames && walkAction.hasNext(); i++) {
            walkAction.execute(mockMascot);
        }
        
        assertFalse(walkAction.hasNext(), "Action should finish after duration");
    }
}
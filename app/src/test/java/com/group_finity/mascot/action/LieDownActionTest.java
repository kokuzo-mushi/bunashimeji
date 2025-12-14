package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.config.xml.XmlPose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LieDownActionTest {

    private Mascot mockMascot;
    private XmlAnimation mockXmlAnimation;

    @BeforeEach
    void setUp() {
        mockMascot = mock(Mascot.class);
        mockXmlAnimation = mock(XmlAnimation.class);
        
        // ダミーのアニメーション設定
        XmlPose pose = mock(XmlPose.class);
        when(pose.getImage()).thenReturn("dummy.png");
        when(pose.getDuration()).thenReturn(100);
        when(mockXmlAnimation.getPoses()).thenReturn(List.of(pose));
    }

    @Test
    void execute_shouldSetAnimation() {
        // Arrange
        LieDownAction action = new LieDownAction(mockXmlAnimation, 2000);

        // Act
        action.execute(mockMascot);

        // Assert
        verify(mockMascot).setAnimation(any(Animation.class));
    }

    @Test
    void duration_shouldBeWithinRange() {
        // Arrange
        int maxDuration = 5000;
        
        // ランダム性の検証のため複数回実行
        for (int i = 0; i < 100; i++) {
            LieDownAction action = new LieDownAction(mockXmlAnimation, maxDuration);
            int timeRemaining = getTimeRemaining(action);
            
            // 仕様: 最低1000ms、最大maxDuration未満
            assertTrue(timeRemaining >= 1000, "Duration should be at least 1000ms. Actual: " + timeRemaining);
            assertTrue(timeRemaining < maxDuration, "Duration should be less than maxDuration. Actual: " + timeRemaining);
        }
    }

    @Test
    void hasNext_shouldDecreaseOverTime() {
        // Arrange
        LieDownAction action = new LieDownAction(mockXmlAnimation, 2000);
        int initialTime = getTimeRemaining(action);
        
        // Act
        action.execute(mockMascot);
        
        // Assert
        int timeAfter = getTimeRemaining(action);
        assertEquals(initialTime - 40, timeAfter, "Time remaining should decrease by frame duration (40ms)");
    }
    
    @Test
    void reset_shouldRandomizeDuration() {
        LieDownAction action = new LieDownAction(mockXmlAnimation, 5000);
        int firstDuration = getTimeRemaining(action);
        
        boolean changed = false;
        for (int i = 0; i < 20; i++) {
            action.reset();
            if (getTimeRemaining(action) != firstDuration) {
                changed = true;
                break;
            }
        }
        assertTrue(changed, "Reset should randomize the duration");
    }

    // リフレクションを使用してprivateフィールドの値を取得するヘルパーメソッド
    private int getTimeRemaining(LieDownAction action) {
        try {
            java.lang.reflect.Field field = LieDownAction.class.getDeclaredField("timeRemaining");
            field.setAccessible(true);
            return field.getInt(action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ClimbActionTest {

    @Test
    void testFaceRightWall() {
        // 準備: 右壁に接触している状態のマスコット
        Animation animation = mock(Animation.class);
        when(animation.getTotalDuration()).thenReturn(100);
        ClimbAction action = new ClimbAction(animation, 1);
        
        Mascot mascot = mock(Mascot.class);
        when(mascot.isHittingRightWall()).thenReturn(true);
        when(mascot.isHittingLeftWall()).thenReturn(false);
        // Win32呼び出しを回避するためにnullを返す
        when(mascot.getRightWallWindow()).thenReturn(null); 

        // 実行
        action.execute(mascot);

        // 検証: 右を向くようにセットされたか？
        verify(mascot).setLookRight(true);
    }

    @Test
    void testFaceLeftWall() {
        // 準備: 左壁に接触している状態のマスコット
        Animation animation = mock(Animation.class);
        when(animation.getTotalDuration()).thenReturn(100);
        ClimbAction action = new ClimbAction(animation, 1);
        
        Mascot mascot = mock(Mascot.class);
        when(mascot.isHittingRightWall()).thenReturn(false);
        when(mascot.isHittingLeftWall()).thenReturn(true);
        // Win32呼び出しを回避するためにnullを返す
        when(mascot.getLeftWallWindow()).thenReturn(null);

        // 実行
        action.execute(mascot);

        // 検証: 左を向くようにセットされたか？
        verify(mascot).setLookRight(false);
    }
}
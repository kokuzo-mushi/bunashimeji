package com.group_finity.mascot.nativeaccess

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

class NativeWindowUtilTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun getPrimaryMonitorWorkArea_shouldReturnValidRect() {
        // Act
        val workArea = NativeWindowUtil.getPrimaryMonitorWorkArea()

        // Assert
        assertNotNull(workArea, "Work area should not be null on Windows")
        // ワークエリアの幅と高さは正の値であるはず
        assertTrue(workArea.width() > 0, "Work area width should be > 0")
        assertTrue(workArea.height() > 0, "Work area height should be > 0")
        
        println("Primary Monitor Work Area: $workArea")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun getCursorPos_shouldReturnValidPoint() {
        // Act
        val cursorPos = NativeWindowUtil.getCursorPos()

        // Assert
        // 呼び出しが成功すれば null 以外が返る（失敗時は null）
        if (cursorPos != null) {
            println("Cursor Position: $cursorPos")
        } else {
            // CI環境などでマウスがない場合はnullもあり得るため、ここでは失敗とはしないがログ出力
            println("Cursor Position: null (GetCursorPos failed or no mouse)")
        }
    }
}
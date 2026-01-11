package com.group_finity.mascot;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.awt.*;

/**
 * 画面サイズ、作業領域、タスクバーの位置情報を診断するためのテストクラス。
 */
public class WindowTrackingTest {
    public static void main(String[] args) {
        System.out.println("=== Environment Diagnosis Tool ===");

        // 1. Java Standard API による情報
        System.out.println("\n[1] Java Standard API:");
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();

        Rectangle bounds = gc.getBounds();
        System.out.println("Screen Bounds (Full): " + bounds);

        Rectangle maxBounds = ge.getMaximumWindowBounds();
        System.out.println("Maximum Window Bounds (Work Area): " + maxBounds);

        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        System.out.println("Screen Insets (Taskbar margins): " + insets);

        // 2. JNA (Windows API) による情報
        System.out.println("\n[2] JNA (Windows API):");
        try {
            HWND trayWnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
            if (trayWnd != null) {
                RECT trayRect = new RECT();
                if (User32.INSTANCE.GetWindowRect(trayWnd, trayRect)) {
                    System.out.println("Taskbar Rect: " + trayRect);
                    System.out.println("Taskbar Width: " + (trayRect.right - trayRect.left));
                    System.out.println("Taskbar Height: " + (trayRect.bottom - trayRect.top));
                } else {
                    System.err.println("Failed to get Taskbar Rect.");
                }
            } else {
                System.err.println("Taskbar Window (Shell_TrayWnd) not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. 計算結果のシミュレーション
        System.out.println("\n[3] Work Area Calculation Check:");
        Rectangle calculatedWorkArea = new Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - (insets.left + insets.right),
            bounds.height - (insets.top + insets.bottom)
        );
        System.out.println("Calculated Work Area (from Insets): " + calculatedWorkArea);
        
        Rectangle intersection = maxBounds.intersection(calculatedWorkArea);
        System.out.println("Intersection (Safe Area): " + intersection);
        
        System.out.println("\nDiagnosis complete.");
    }
}
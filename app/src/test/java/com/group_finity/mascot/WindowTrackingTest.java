package com.group_finity.mascot;

import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

/**
 * ウィンドウ追従機能の検証用テストクラス。
 * 起動後3秒待機し、その時点で一番手前にあるウィンドウをターゲットにして、
 * そのウィンドウの位置情報をコンソールに出力し続けます。
 */
public class WindowTrackingTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ウィンドウ追従テスト開始 ===");
        System.out.println("3秒後に、現在一番手前にあるウィンドウ（このコンソールやエディタなど）をターゲットとしてロックします。");
        System.out.println("テストしたいウィンドウをアクティブにしてお待ちください...");
        
        Thread.sleep(3000);

        // ターゲットとなるウィンドウを探す
        final HWND[] targetHwnd = {null};
        final String[] targetTitle = {null};

        Win32.INSTANCE.EnumWindows(new Win32.WNDENUMPROC() {
            boolean found = false;
            @Override
            public boolean callback(HWND hWnd, com.sun.jna.Pointer arg) {
                if (found) return false; // すでに見つかったら終了
                
                if (Win32.INSTANCE.IsWindowVisible(hWnd) && !Win32.INSTANCE.IsIconic(hWnd)) {
                    byte[] buffer = new byte[1024];
                    Win32.INSTANCE.GetWindowTextA(hWnd, buffer, buffer.length);
                    String title = new String(buffer).trim();
                    
                    // システム系のウィンドウを除外して、最初に見つかったウィンドウを採用
                    if (!title.isEmpty() && !title.equals("Default IME") && !title.equals("MSCTFIME UI")) {
                        targetHwnd[0] = hWnd;
                        targetTitle[0] = title;
                        found = true;
                        return false; // 列挙終了
                    }
                }
                return true; // 続行
            }
        }, null);

        if (targetHwnd[0] == null) {
            System.err.println("エラー: 追跡可能なウィンドウが見つかりませんでした。");
            return;
        }

        System.out.println("ターゲット捕捉: [" + targetTitle[0] + "]");
        System.out.println("このウィンドウをマウスで動かしてください。座標の変化をログに出力します。");
        System.out.println("終了するには Ctrl+C を押してください。");

        RECT lastRect = new RECT();
        if (Win32.INSTANCE.GetWindowRect(targetHwnd[0], lastRect) == 0) {
            System.err.println("エラー: 初期座標の取得に失敗しました。");
            return;
        }
        System.out.printf("初期位置: (%d, %d)%n", lastRect.left, lastRect.top);

        // 監視ループ
        while (true) {
            RECT currentRect = new RECT();
            if (Win32.INSTANCE.GetWindowRect(targetHwnd[0], currentRect) == 0) {
                System.out.println("ウィンドウが閉じられたか、取得に失敗しました。終了します。");
                break;
            }

            // 位置が変わった場合のみログ出力
            if (currentRect.left != lastRect.left || currentRect.top != lastRect.top) {
                int dx = currentRect.left - lastRect.left;
                int dy = currentRect.top - lastRect.top;
                System.out.printf("移動検知! dx=%d, dy=%d | 新位置: (%d, %d)%n", dx, dy, currentRect.left, currentRect.top);
                
                // 座標更新
                lastRect.left = currentRect.left;
                lastRect.top = currentRect.top;
                lastRect.right = currentRect.right;
                lastRect.bottom = currentRect.bottom;
            }

            Thread.sleep(16); // 約60FPS
        }
    }
}
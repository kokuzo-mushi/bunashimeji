package com.group_finity.mascot;

import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;

import java.lang.foreign.MemorySegment;

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
        final long[] targetHwndAddr = {0};
        final String[] targetTitle = {null};

        NativeWindowUtil.enumWindows(new NativeWindowUtil.EnumWindowsProc() {
            boolean found = false;
            @Override
            public boolean callback(MemorySegment hWnd, long lParam) {
                if (found) return false; // すでに見つかったら終了
                
                if (NativeWindowUtil.isWindowVisible(hWnd) && !NativeWindowUtil.isIconic(hWnd)) {
                    String title = NativeWindowUtil.getWindowText(hWnd).trim();
                    
                    // システム系のウィンドウを除外して、最初に見つかったウィンドウを採用
                    if (!title.isEmpty() && !title.equals("Default IME") && !title.equals("MSCTFIME UI")) {
                        targetHwndAddr[0] = hWnd.address();
                        targetTitle[0] = title;
                        found = true;
                        return false; // 列挙終了
                    }
                }
                return true; // 続行
            }
        }, 0L);

        if (targetHwndAddr[0] == 0) {
            System.err.println("エラー: 追跡可能なウィンドウが見つかりませんでした。");
            return;
        }

        System.out.println("ターゲット捕捉: [" + targetTitle[0] + "]");
        System.out.println("このウィンドウをマウスで動かしてください。座標の変化をログに出力します。");
        System.out.println("終了するには Ctrl+C を押してください。");

        // アドレスからMemorySegmentを再構築（スコープはGlobalとみなすか、呼び出し毎にラップする）
        MemorySegment targetHwnd = MemorySegment.ofAddress(targetHwndAddr[0]);

        NeoRect lastRect = NativeWindowUtil.getWindowRect(targetHwnd);
        if (lastRect == null) {
            System.err.println("エラー: 初期座標の取得に失敗しました。");
            return;
        }
        System.out.printf("初期位置: (%d, %d)%n", lastRect.x(), lastRect.y());

        // 監視ループ
        while (true) {
            // NativeWindowUtilのメソッドはMemorySegmentを要求する
            NeoRect currentRect = NativeWindowUtil.getWindowRect(targetHwnd);
            
            if (currentRect == null) {
                System.out.println("ウィンドウが閉じられたか、取得に失敗しました。終了します。");
                break;
            }

            // 位置が変わった場合のみログ出力
            if (currentRect.x() != lastRect.x() || currentRect.y() != lastRect.y()) {
                int dx = currentRect.x() - lastRect.x();
                int dy = currentRect.y() - lastRect.y();
                System.out.printf("移動検知! dx=%d, dy=%d | 新位置: (%d, %d)%n", dx, dy, currentRect.x(), currentRect.y());
                
                // 座標更新
                lastRect = currentRect;
            }

            Thread.sleep(16); // 約60FPS
        }
    }
}
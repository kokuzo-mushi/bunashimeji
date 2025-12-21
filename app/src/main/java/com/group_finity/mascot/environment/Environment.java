package com.group_finity.mascot.environment;

import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;

import java.awt.Rectangle;
import java.nio.charset.Charset;

/**
 * マスコットを取り巻く環境（ウィンドウや画面端）を認識するクラス。
 */
public class Environment {

    private static final Environment INSTANCE = new Environment();

    public static Environment getInstance() {
        return INSTANCE;
    }

    private Environment() {}

    public static class EnvironmentInfo {
        public int floorY;
        public int ceilingY;
        public int leftWallX;
        public int rightWallX;
        public HWND floorWindow;
        public RECT floorRect;
        public HWND ceilingWindow;
        public RECT ceilingRect;
        public HWND leftWallWindow;
        public RECT leftWallRect;
        public HWND rightWallWindow;
        public RECT rightWallRect;
    }

    /**
     * 指定されたマスコットの位置とサイズに基づいて、周囲の環境情報（床、天井、壁の位置）を返します。
     *
     * @param x マスコットの中心X座標
     * @param y マスコットの足元Y座標
     * @param width マスコットの幅
     * @param height マスコットの高さ
     * @param workArea 画面の作業領域
     * @param previousFloor 前回乗っていた床ウィンドウ（追従判定の緩和用）
     * @param previousCeiling 前回張り付いていた天井ウィンドウ
     * @param previousLeftWall 前回張り付いていた左壁ウィンドウ
     * @param previousRightWall 前回張り付いていた右壁ウィンドウ
     * @return 環境情報
     */
    public EnvironmentInfo getEnvironmentInfo(int x, int y, int width, int height, Rectangle workArea, HWND previousFloor, HWND previousCeiling, HWND previousLeftWall, HWND previousRightWall) {
        final EnvironmentInfo info = new EnvironmentInfo();
        // 初期値は画面の端
        info.floorY = workArea.y + workArea.height;
        info.ceilingY = workArea.y;
        info.leftWallX = workArea.x;
        info.rightWallX = workArea.x + workArea.width;
        info.floorWindow = null;
        info.floorRect = null;
        info.ceilingWindow = null;
        info.ceilingRect = null;
        info.leftWallWindow = null;
        info.leftWallRect = null;
        info.rightWallWindow = null;
        info.rightWallRect = null;

        final int DEFAULT_SEARCH_THRESHOLD = 20; // 通常時の許容範囲
        
        // 現在のプロセスIDを取得（自分自身を除外するため）
        final int currentPid = Kernel32.INSTANCE.GetCurrentProcessId();
        
        // 床・天井の探索において、手前のウィンドウに遮蔽されたかを判定するフラグ
        final boolean[] isFloorBlocked = { false };

        // 壁の探索において、手前のウィンドウに遮蔽されたかを判定するフラグ
        final boolean[] isWallBlocked = { false };

        Win32.INSTANCE.EnumWindows(new Win32.WNDENUMPROC() {
            @Override
            public boolean callback(HWND hWnd, com.sun.jna.Pointer arg) {
                if (!Win32.INSTANCE.IsWindowVisible(hWnd)) {
                    return true;
                }

                // 最小化されているウィンドウは無視
                if (Win32.INSTANCE.IsIconic(hWnd)) {
                    return true;
                }

                // タイトルがないウィンドウ（システム用など）は無視
                byte[] titleBuffer = new byte[1024];
                int titleLength = Win32.INSTANCE.GetWindowTextA(hWnd, titleBuffer, titleBuffer.length);
                if (titleLength == 0) {
                    return true;
                }
                
                // 特定のシステムウィンドウは無視する（これらが手前にあると遮蔽判定で床が見えなくなるため）
                String title = new String(titleBuffer, 0, titleLength, Charset.forName("MS932")).trim();
                if (title.equals("Default IME") || title.equals("MSCTFIME UI") || title.equals("Program Manager")) {
                    return true;
                }

                // 自分自身のウィンドウは床として認識しない
                IntByReference pid = new IntByReference();
                Win32.INSTANCE.GetWindowThreadProcessId(hWnd, pid);
                if (pid.getValue() == currentPid) {
                    return true;
                }

                RECT rect = new RECT();
                Win32.INSTANCE.GetWindowRect(hWnd, rect);

                // ウィンドウの幅が極端に小さい、または画面外にあるものは無視
                if (rect.right - rect.left <= 0 || rect.bottom - rect.top <= 0) {
                    return true;
                }

                // 前回乗っていたウィンドウなら、判定を甘くする（粘着させる）
                boolean isPrevious = previousFloor != null && hWnd.equals(previousFloor);
                boolean isPreviousCeiling = previousCeiling != null && hWnd.equals(previousCeiling);
                boolean isPreviousLeft = previousLeftWall != null && hWnd.equals(previousLeftWall);
                boolean isPreviousRight = previousRightWall != null && hWnd.equals(previousRightWall);

                int searchThreshold = isPrevious ? 500 : DEFAULT_SEARCH_THRESHOLD; // 追従中は縦方向の許容範囲を大幅に広げる
                int ceilingThreshold = isPreviousCeiling ? 500 : DEFAULT_SEARCH_THRESHOLD; // 天井の許容範囲
                int horizontalMargin = isPrevious ? 30 : 0; // 追従中の横方向のはみ出し許容範囲を適正値に調整
                int ceilingHorizontalMargin = isPreviousCeiling ? 30 : 0; // 天井の横方向許容範囲

                // --- 床と天井の判定 ---
                // マスコットのX中心がウィンドウの左右範囲内にあるか
                // 床と天井でそれぞれマージンを適用して判定
                boolean inRangeForFloor = x >= rect.left - horizontalMargin && x <= rect.right + horizontalMargin;
                boolean inRangeForCeiling = x >= rect.left - ceilingHorizontalMargin && x <= rect.right + ceilingHorizontalMargin;

                if (inRangeForFloor || inRangeForCeiling) {
                    // まだ手前のウィンドウに遮蔽されていない場合のみ、床・天井を探索する
                    if (!isFloorBlocked[0]) {
                        // 床判定: ウィンドウの上端がマスコットの足元付近、または下にある
                        // ただし、画面上端にあるウィンドウ（rect.top <= workArea.y）は床とみなさない
                        if (inRangeForFloor && rect.top > workArea.y && rect.top >= y - searchThreshold && rect.top < info.floorY) {
                            info.floorY = rect.top;
                            info.floorWindow = hWnd;
                            // RECTを参照ではなく値コピーで保存する（JNAのメモリ管理の影響を避けるため）
                            info.floorRect = new RECT();
                            info.floorRect.left = rect.left;
                            info.floorRect.top = rect.top;
                            info.floorRect.right = rect.right;
                            info.floorRect.bottom = rect.bottom;
                            // デバッグログ: 床として認識したウィンドウを表示
                            System.out.println("[Env] Floor found: " + title);
                        }

                        // 天井判定: ウィンドウの下端がマスコットの頭上付近、または上にある
                        // マスコットの頭上Y座標 = y - height
                        int mascotTop = y - height;
                        if (inRangeForCeiling && rect.bottom <= mascotTop + ceilingThreshold && rect.bottom > info.ceilingY) {
                            info.ceilingY = rect.bottom;
                            info.ceilingWindow = hWnd;
                            info.ceilingRect = new RECT();
                            info.ceilingRect.left = rect.left;
                            info.ceilingRect.top = rect.top;
                            info.ceilingRect.right = rect.right;
                            info.ceilingRect.bottom = rect.bottom;
                        }

                        // 遮蔽判定: ウィンドウがマスコットの足元の高さを覆っている場合
                        // このウィンドウはマスコットの手前にあり、背後の床・天井を隠しているとみなす
                        if (rect.top < y - searchThreshold && rect.bottom >= y) {
                            isFloorBlocked[0] = true;
                            // デバッグログ: 遮蔽物として判定されたウィンドウを表示
                            System.out.println("[Env] Blocked by: " + title);
                        }
                    }
                }

                // --- 壁の判定 ---
                // マスコットのY中心がウィンドウの上下範囲内にあるか
                int mascotCenterY = y - height / 2;
                // 壁追従中は縦方向（Y軸）のズレをある程度許容する
                int verticalMargin = (isPreviousLeft || isPreviousRight) ? 100 : 0;

                if (mascotCenterY >= rect.top - verticalMargin && mascotCenterY <= rect.bottom + verticalMargin) {

                    // まだ手前のウィンドウに遮蔽されていない場合のみ、壁を探索する
                    if (!isWallBlocked[0]) {
                        int leftThreshold = isPreviousLeft ? 500 : DEFAULT_SEARCH_THRESHOLD;
                        int rightThreshold = isPreviousRight ? 500 : DEFAULT_SEARCH_THRESHOLD;

                        // 左壁判定: ウィンドウの右端がマスコットの左側面付近にある
                        // マスコットの左側面X座標 = x - width / 2
                        int mascotLeft = x - width / 2;
                        if (rect.right <= mascotLeft + leftThreshold && rect.right > info.leftWallX) {
                            info.leftWallX = rect.right;
                            info.leftWallWindow = hWnd;
                            info.leftWallRect = new RECT();
                            info.leftWallRect.left = rect.left;
                            info.leftWallRect.top = rect.top;
                            info.leftWallRect.right = rect.right;
                            info.leftWallRect.bottom = rect.bottom;
                        }


                        // 右壁判定: ウィンドウの左端がマスコットの右側面付近にある
                        // マスコットの右側面X座標 = x + width / 2
                        int mascotRight = x + width / 2;
                        if (rect.left >= mascotRight - rightThreshold && rect.left < info.rightWallX) {
                            info.rightWallX = rect.left;
                            info.rightWallWindow = hWnd;
                            info.rightWallRect = new RECT();
                            info.rightWallRect.left = rect.left;
                            info.rightWallRect.top = rect.top;
                            info.rightWallRect.right = rect.right;
                            info.rightWallRect.bottom = rect.bottom;
                        }

                        // 遮蔽判定: ウィンドウがマスコットの左右を完全に覆っている場合
                        // このウィンドウはマスコットの手前にあり、背後の壁を隠しているとみなす
                        if (rect.left < x - width / 2 && rect.right > x + width / 2) {
                            isWallBlocked[0] = true;
                        }
                    }
                }
                return true;
            }
        }, null);

        return info;
    }

    /**
     * マスコットの近くにある操作可能なウィンドウ（ターゲット）を探します。
     *
     * @param x マスコットのX座標
     * @param y マスコットのY座標
     * @param height マスコットの高さ（中心座標計算用）
     * @param searchRadius 探索半径（ピクセル）
     * @return 最も近いターゲットウィンドウのハンドル。見つからない場合はnull。
     */
    public HWND findTargetWindow(int x, int y, int height, int searchRadius) {
        final int currentPid = Kernel32.INSTANCE.GetCurrentProcessId();
        final HWND[] target = { null };
        final double[] minDistanceSq = { Double.MAX_VALUE };
        final int mascotCenterX = x;
        final int mascotCenterY = y - height / 2;
        final double searchRadiusSq = (double) searchRadius * searchRadius;

        Win32.INSTANCE.EnumWindows(new Win32.WNDENUMPROC() {
            @Override
            public boolean callback(HWND hWnd, com.sun.jna.Pointer arg) {
                if (!Win32.INSTANCE.IsWindowVisible(hWnd) || Win32.INSTANCE.IsIconic(hWnd)) {
                    return true;
                }

                // 最大化されているウィンドウは無視
                if (Win32.INSTANCE.IsZoomed(hWnd)) {
                    return true;
                }

                // タイトルチェック
                byte[] titleBuffer = new byte[1024];
                int titleLength = Win32.INSTANCE.GetWindowTextA(hWnd, titleBuffer, titleBuffer.length);
                if (titleLength == 0) return true;

                String title = new String(titleBuffer, 0, titleLength, Charset.forName("MS932")).trim();
                if (title.equals("Default IME") || title.equals("MSCTFIME UI") || title.equals("Program Manager") || title.equals("Task Manager")) {
                    return true;
                }

                // 自分自身を除外
                IntByReference pid = new IntByReference();
                Win32.INSTANCE.GetWindowThreadProcessId(hWnd, pid);
                if (pid.getValue() == currentPid) return true;

                RECT rect = new RECT();
                Win32.INSTANCE.GetWindowRect(hWnd, rect);
                if (rect.right - rect.left <= 0 || rect.bottom - rect.top <= 0) return true;

                // マスコット中心からウィンドウ矩形への最短距離を計算
                int dx = Math.max(rect.left - mascotCenterX, Math.max(0, mascotCenterX - rect.right));
                int dy = Math.max(rect.top - mascotCenterY, Math.max(0, mascotCenterY - rect.bottom));
                double distSq = dx * dx + dy * dy;

                if (distSq <= searchRadiusSq && distSq < minDistanceSq[0]) {
                    minDistanceSq[0] = distSq;
                    target[0] = hWnd;
                }

                return true;
            }
        }, null);

        return target[0];
    }
}

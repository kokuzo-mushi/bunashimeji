package com.group_finity.mascot.environment;

import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.awt.Rectangle;

/**
 * マスコットを取り巻く環境（ウィンドウや画面端）を認識するクラス。
 */
public class Environment {

    private static final Environment INSTANCE = new Environment();

    public static Environment getInstance() {
        return INSTANCE;
    }

    private Environment() {
    }

    public static class EnvironmentInfo {
        public int floorY;
        public int ceilingY;
        public int leftWallX;
        public int rightWallX;
        public MemorySegment floorWindow;
        public NeoRect floorRect;
        public MemorySegment ceilingWindow;
        public NeoRect ceilingRect;
        public MemorySegment leftWallWindow;
        public NeoRect leftWallRect;
        public MemorySegment rightWallWindow;
        public NeoRect rightWallRect;
    }

    /**
     * 指定されたマスコットの位置とサイズに基づいて、周囲の環境情報（床、天井、壁の位置）を返します。
     *
     * @param x                 マスコットの中心X座標
     * @param y                 マスコットの足元Y座標
     * @param width             マスコットの幅
     * @param height            マスコットの高さ
     * @param workArea          画面の作業領域 (Logical)
     * @param previousFloor     前回乗っていた床ウィンドウ（追従判定の緩和用）
     * @param previousCeiling   前回張り付いていた天井ウィンドウ
     * @param previousLeftWall  前回張り付いていた左壁ウィンドウ
     * @param previousRightWall 前回張り付いていた右壁ウィンドウ
     * @param holdingWindow     現在マスコットが掴んでいるウィンドウ（除外用）
     * @param targetWindow      現在マスコットがターゲットにしているウィンドウ（除外用）
     * @return 環境情報
     */
    public EnvironmentInfo getEnvironmentInfo(int x, int y, int width, int height, NeoRect workArea,
            MemorySegment previousFloor, MemorySegment previousCeiling, MemorySegment previousLeftWall,
            MemorySegment previousRightWall, MemorySegment holdingWindow, MemorySegment targetWindow) {
        final EnvironmentInfo info = new EnvironmentInfo();
        // 初期値は画面の端
        info.floorY = workArea.top() + workArea.height();
        info.ceilingY = workArea.top();
        info.leftWallX = workArea.left();
        info.rightWallX = workArea.left() + workArea.width();
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
        // Note: ProcessHandle is cleaner in Java 9+
        final long currentPid = ProcessHandle.current().pid();

        // 床・天井の探索において、手前のウィンドウに遮蔽されたかを判定するフラグ
        final boolean[] isFloorBlocked = { false };

        // 壁の探索において、手前のウィンドウに遮蔽されたかを判定するフラグ
        final boolean[] isWallBlocked = { false };

        NativeWindowUtil.enumWindows((hWnd, lParam) -> {
            if (!NativeWindowUtil.isWindowVisible(hWnd)) {
                return true;
            }

            // 最小化されているウィンドウは無視
            if (NativeWindowUtil.isIconic(hWnd)) {
                return true;
            }

            // タイトルがないウィンドウ（システム用など）は無視
            String title = NativeWindowUtil.getWindowText(hWnd).trim();
            if (title.isEmpty()) {
                return true;
            }

            // 特定のシステムウィンドウは無視する（これらが手前にあると遮蔽判定で床が見えなくなるため）
            if (title.equals("Default IME") || title.equals("MSCTFIME UI") || title.equals("Program Manager")) {
                return true;
            }

            // 自分自身のウィンドウは床として認識しない
            int pid = NativeWindowUtil.getWindowThreadProcessId(hWnd);
            if (pid == currentPid) {
                return true;
            }

            // マスコットが掴んでいるウィンドウは環境（床・壁・天井）として認識しない
            if (isSameWindow(hWnd, holdingWindow)) {
                return true;
            }

            // マスコットがターゲットにしているウィンドウも環境として認識しない（めり込んで掴むため）
            if (isSameWindow(hWnd, targetWindow)) {
                return true;
            }

            NeoRect rect = NativeWindowUtil.getWindowRect(hWnd);

            // ウィンドウの幅が極端に小さい、または画面外にあるものは無視
            if (rect.width() <= 0 || rect.height() <= 0) {
                return true;
            }

            // 前回乗っていたウィンドウなら、判定を甘くする（粘着させる）
            boolean isPrevious = isSameWindow(hWnd, previousFloor);
            boolean isPreviousCeiling = isSameWindow(hWnd, previousCeiling);
            boolean isPreviousLeft = isSameWindow(hWnd, previousLeftWall);
            boolean isPreviousRight = isSameWindow(hWnd, previousRightWall);

            int searchThreshold = isPrevious ? 500 : DEFAULT_SEARCH_THRESHOLD; // 追従中は縦方向の許容範囲を大幅に広げる
            int ceilingThreshold = isPreviousCeiling ? 500 : DEFAULT_SEARCH_THRESHOLD; // 天井の許容範囲
            int horizontalMargin = isPrevious ? 30 : 0; // 追従中の横方向のはみ出し許容範囲を適正値に調整
            int ceilingHorizontalMargin = isPreviousCeiling ? 30 : 0; // 天井の横方向許容範囲

            // --- 床と天井の判定 ---
            // マスコットのX中心がウィンドウの左右範囲内にあるか
            // 床と天井でそれぞれマージンを適用して判定
            boolean inRangeForFloor = x >= rect.left() - horizontalMargin && x <= rect.right() + horizontalMargin;
            boolean inRangeForCeiling = x >= rect.left() - ceilingHorizontalMargin
                    && x <= rect.right() + ceilingHorizontalMargin;

            if (inRangeForFloor || inRangeForCeiling) {
                // まだ手前のウィンドウに遮蔽されていない場合のみ、床・天井を探索する
                if (!isFloorBlocked[0]) {
                    // 床判定: ウィンドウの上端がマスコットの足元付近、または下にある
                    // ただし、画面上端にあるウィンドウ（rect.top <= workArea.top）は床とみなさない
                    if (inRangeForFloor && rect.top() > workArea.top() && rect.top() >= y - searchThreshold
                            && rect.top() < info.floorY) {
                        info.floorY = rect.top();
                        info.floorWindow = hWnd;
                        info.floorRect = rect;
                    }

                    // 天井判定: ウィンドウの下端がマスコットの頭上付近、または上にある
                    // マスコットの頭上Y座標 = y - height
                    int mascotTop = y - height;
                    if (inRangeForCeiling && rect.bottom() <= mascotTop + ceilingThreshold
                            && rect.bottom() > info.ceilingY) {
                        info.ceilingY = rect.bottom();
                        info.ceilingWindow = hWnd;
                        info.ceilingRect = rect;
                    }

                    // 遮蔽判定: ウィンドウがマスコットの足元の高さを覆っている場合
                    // このウィンドウはマスコットの手前にあり、背後の床・天井を隠しているとみなす
                    if (rect.top() < y - searchThreshold && rect.bottom() >= y) {
                        isFloorBlocked[0] = true;
                    }
                }
            }

            // --- 壁の判定 ---
            // マスコットのY中心がウィンドウの上下範囲内にあるか
            int mascotCenterY = y - height / 2;
            // 壁追従中は縦方向（Y軸）のズレをある程度許容する
            int verticalMargin = (isPreviousLeft || isPreviousRight) ? 100 : 0;

            if (mascotCenterY >= rect.top() - verticalMargin && mascotCenterY <= rect.bottom() + verticalMargin) {

                // まだ手前のウィンドウに遮蔽されていない場合のみ、壁を探索する
                if (!isWallBlocked[0]) {
                    int leftThreshold = isPreviousLeft ? 500 : DEFAULT_SEARCH_THRESHOLD;
                    int rightThreshold = isPreviousRight ? 500 : DEFAULT_SEARCH_THRESHOLD;

                    // 左壁判定: ウィンドウの右端がマスコットの左側面付近にある
                    // マスコットの左側面X座標 = x - width / 2
                    int mascotLeft = x - width / 2;
                    if (rect.right() <= mascotLeft + leftThreshold && rect.right() > info.leftWallX) {
                        info.leftWallX = rect.right();
                        info.leftWallWindow = hWnd;
                        info.leftWallRect = rect;
                    }

                    // 右壁判定: ウィンドウの左端がマスコットの右側面付近にある
                    // マスコットの右側面X座標 = x + width / 2
                    int mascotRight = x + width / 2;
                    if (rect.left() >= mascotRight - rightThreshold && rect.left() < info.rightWallX) {
                        info.rightWallX = rect.left();
                        info.rightWallWindow = hWnd;
                        info.rightWallRect = rect;
                    }

                    // 遮蔽判定: ウィンドウがマスコットの左右を完全に覆っている場合
                    // このウィンドウはマスコットの手前にあり、背後の壁を隠しているとみなす
                    if (rect.left() < x - width / 2 && rect.right() > x + width / 2) {
                        isWallBlocked[0] = true;
                    }
                }
            }
            return true;
        }, 0);

        return info;
    }

    /**
     * マスコットの近くにある操作可能なウィンドウ（ターゲット）を探します。
     *
     * @param x            マスコットのX座標
     * @param y            マスコットのY座標
     * @param height       マスコットの高さ（中心座標計算用）
     * @param searchRadius 探索半径（ピクセル）
     * @return 最も近いターゲットウィンドウのハンドル。見つからない場合はnull。
     */
    public MemorySegment findTargetWindow(int x, int y, int height, int searchRadius, MemorySegment excludeWindow) {
        final long currentPid = ProcessHandle.current().pid();
        final MemorySegment[] target = { null };
        final double[] minDistanceSq = { Double.MAX_VALUE };
        final int mascotCenterX = x;
        final int mascotCenterY = y - height / 2;
        final double searchRadiusSq = (double) searchRadius * searchRadius;

        NativeWindowUtil.enumWindows((hWnd, lParam) -> {
            if (!NativeWindowUtil.isWindowVisible(hWnd) || NativeWindowUtil.isIconic(hWnd)) {
                return true;
            }

            // 最大化されているウィンドウは無視
            if (NativeWindowUtil.isZoomed(hWnd)) {
                return true;
            }

            // タイトルチェック
            String title = NativeWindowUtil.getWindowText(hWnd).trim();
            if (title.isEmpty())
                return true;

            if (title.equals("Default IME") || title.equals("MSCTFIME UI") || title.equals("Program Manager")
                    || title.equals("Task Manager")) {
                return true;
            }

            // 自分自身を除外
            int pid = NativeWindowUtil.getWindowThreadProcessId(hWnd);
            if (pid == currentPid)
                return true;

            // 除外対象のウィンドウ（足元のウィンドウなど）は無視
            if (isSameWindow(hWnd, excludeWindow)) {
                return true;
            }

            NeoRect rect = NativeWindowUtil.getWindowRect(hWnd);
            if (rect.width() <= 0 || rect.height() <= 0)
                return true;

            // マスコット中心からウィンドウ矩形への最短距離を計算
            int dx = Math.max(rect.left() - mascotCenterX, Math.max(0, mascotCenterX - rect.right()));
            int dy = Math.max(rect.top() - mascotCenterY, Math.max(0, mascotCenterY - rect.bottom()));
            double distSq = dx * dx + dy * dy;

            if (distSq <= searchRadiusSq && distSq < minDistanceSq[0]) {
                minDistanceSq[0] = distSq;
                target[0] = hWnd;
            }

            return true;
        }, 0);

        return target[0];
    }

    private boolean isSameWindow(MemorySegment w1, MemorySegment w2) {
        if (w1 == null || w2 == null)
            return false;
        // Compare native addresses
        return w1.address() == w2.address();
    }

    /**
     * 投げる対象として適切な「到達可能な」ウィンドウを探します。
     * - 最小化/不可視を除外
     * - 巨大なウィンドウ（背景等）を除外
     * - 自分自身や足元のウィンドウを除外
     *
     * @param mascotX マスコットの中心X
     * @param mascotY マスコットのY
     * @param range   探索範囲
     * @return ターゲットウィンドウのハンドル
     */
    public MemorySegment findReachableTargetWindow(int mascotX, int mascotY, int range) {
        final long currentPid = ProcessHandle.current().pid();
        final MemorySegment[] target = { null };
        final double[] minDistanceSq = { Double.MAX_VALUE };
        final double rangeSq = (double) range * range;

        // 画面サイズ取得 (簡易的)
        Rectangle screenRect = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int screenWidth = screenRect.width;
        int screenHeight = screenRect.height;

        NativeWindowUtil.enumWindows((hWnd, lParam) -> {
            if (!NativeWindowUtil.isWindowVisible(hWnd) || NativeWindowUtil.isIconic(hWnd)) {
                return true;
            }
            if (NativeWindowUtil.isZoomed(hWnd)) {
                return true;
            }

            String title = NativeWindowUtil.getWindowText(hWnd).trim();
            if (title.isEmpty())
                return true;
            if (title.equals("Default IME") || title.equals("MSCTFIME UI") || title.equals("Program Manager")
                    || title.equals("Task Manager")) {
                return true;
            }

            int pid = NativeWindowUtil.getWindowThreadProcessId(hWnd);
            if (pid == currentPid)
                return true;

            NeoRect rect = NativeWindowUtil.getWindowRect(hWnd);
            if (rect.width() <= 0 || rect.height() <= 0)
                return true;

            // 巨大ウィンドウ除外 (画面サイズの90%以上は除外)
            if (rect.width() >= screenWidth * 0.9 && rect.height() >= screenHeight * 0.9) {
                return true;
            }

            // 距離計算
            // マスコットの座標がウィンドウの内部にある、または近い
            // X軸距離
            int dx = 0;
            if (mascotX < rect.left())
                dx = rect.left() - mascotX;
            else if (mascotX > rect.right())
                dx = mascotX - rect.right();

            // Y軸距離 (マスコットは上にいるウィンドウもターゲットにできる)
            // ただし、ThrowActionのためには「掴める位置」にあることが望ましい
            // ここでは単純な最短距離を使う
            int dy = 0;
            if (mascotY < rect.top())
                dy = rect.top() - mascotY;
            else if (mascotY > rect.bottom())
                dy = mascotY - rect.bottom();

            double distSq = dx * dx + dy * dy;

            if (distSq <= rangeSq && distSq < minDistanceSq[0]) {
                minDistanceSq[0] = distSq;
                target[0] = hWnd;
            }

            return true;
        }, 0);

        return target[0];
    }
}

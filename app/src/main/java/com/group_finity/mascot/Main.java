package com.group_finity.mascot;

import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.Configuration;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.nativeaccess.Win32;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.StateChangeEvent;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.image.ImageCache;
import com.group_finity.mascot.view.MascotWindow;
import com.group_finity.mascot.script.ScriptEngineManager;
import org.graalvm.polyglot.Context;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import java.lang.foreign.MemorySegment;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.PopupMenu;
import java.awt.MenuItem;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import javax.swing.SwingUtilities;

/**
 * アプリケーションのメインエントリーポイント。
 * 設定を読み込み、マスコットを生成し、メインループを開始します。
 */
@SuppressWarnings("preview")
public class Main {

    // マスコット1体分の管理情報をまとめるクラス
    private static class MascotInstance {
        Mascot mascot;
        MascotWindow view;
        EventDispatcher dispatcher;
        EvaluationContext context;
        HWND currentFloorWindow;
        RECT currentFloorRect;
        HWND currentCeilingWindow;
        RECT currentCeilingRect;
        HWND currentLeftWallWindow;
        RECT currentLeftWallRect;
        HWND currentRightWallWindow;
        RECT currentRightWallRect;
        long bornTime;
    }

    // 投げられたウィンドウの情報を保持するクラス
    private static class ThrownWindowInfo {
        HWND hwnd;
        int originalX, originalY, width, height;
        long thrownTime;
        
        // 復帰アニメーション用
        boolean isRestoring = false;
        long restoreStartTime;
        int startX, startY;

        ThrownWindowInfo(HWND hwnd, int x, int y, int w, int h) {
            this.hwnd = hwnd;
            this.originalX = x;
            this.originalY = y;
            this.width = w;
            this.height = h;
            this.thrownTime = System.currentTimeMillis();
        }
    }

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    private static final int MAX_MASCOTS = 8;
    private final List<MascotInstance> mascotInstances = new ArrayList<>();
    private Configuration config;
    private ImageCache imageCache;
    private final List<ThrownWindowInfo> thrownWindows = new ArrayList<>();
    private Rectangle workArea;
    private volatile int gravity = 1;
    private volatile double timeScale = 1.0;
    private volatile HWND limitWindow = null;

    // JNAのUser32でSystemParametersInfoのシグネチャ不一致が起きる場合の回避用インターフェース
    public interface User32SPI extends com.sun.jna.win32.StdCallLibrary {
        User32SPI INSTANCE = com.sun.jna.Native.load("user32", User32SPI.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);
        boolean SystemParametersInfoW(int uiAction, int uiParam, RECT pvParam, int fWinIni);
    }

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("An unexpected error occurred. Exiting.");
        }
    }

    public int getGravity() { return gravity; }
    public void setGravity(int gravity) { this.gravity = gravity; }

    public double getTimeScale() { return timeScale; }
    public void setTimeScale(double timeScale) { this.timeScale = timeScale; }

    public void setLimitWindow(HWND hwnd) {
        this.limitWindow = hwnd;
        if (hwnd == null) {
            System.out.println("[Main] Limit window cleared.");
        } else {
            char[] buffer = new char[512];
            User32.INSTANCE.GetWindowText(hwnd, buffer, 512);
            System.out.println("[Main] Limit window set to: " + Native.toString(buffer));
        }
    }

    public void setLimitToActiveWindowDelayed(int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                HWND foreground = User32.INSTANCE.GetForegroundWindow();
                if (foreground != null) {
                    setLimitWindow(foreground);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void run() throws InterruptedException {
        instance = this;
        System.out.println("=== Shimeji Neo Start (Deep Debug Mode) ===");
        System.out.println("[Main] Current working directory: " + System.getProperty("user.dir"));

        try { ensureConfigurationExists(); } catch (IOException e) { e.printStackTrace(); }

        // --- 1️⃣ 設定の読み込み ---
        // actions.xml と behaviors.xml からアクションとビヘイビアの定義を読み込みます。
        config = new Configuration(Path.of("conf/actions.xml"), Path.of("conf/behaviors.xml"));
        List<Behavior> behaviors = config.getBehaviors();

        if (behaviors == null || behaviors.isEmpty()) {
            System.err.println("No behaviors found in configuration. The mascot will not do anything.");
            return;
        }

        // --- 2️⃣ 環境情報の初期化 ---
        // 初期ワークエリアはJava APIで仮設定しておく（後でメインループ内で正確な値に更新される）
        workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        System.out.printf("[Main] Initial work area (Java API): %s%n", workArea);

        // 画像キャッシュの初期化
        imageCache = new ImageCache(Path.of("img"));

        // システムトレイの初期化
        setupSystemTray();

        // --- 3️⃣ マスコットの生成 ---
        // 起動時はまずキャラクター選択画面を表示する
        openSkinSelection();

        // --- 4️⃣ メインループ ---
        System.out.println("[Main] Starting main loop... (Press Ctrl+C to exit)");
        long tickCount = 0;
        
        while (!Thread.currentThread().isInterrupted()) {
            long startTime = System.nanoTime();
            int currentGravity = this.gravity;
            double currentScale = this.timeScale;
            long optimalTime = (long) (1000000000 / (60.0 * currentScale));

            // マウス座標の更新
            java.awt.Point mousePos = java.awt.MouseInfo.getPointerInfo().getLocation();
            Map<String, Integer> mouseMap = new HashMap<>();
            mouseMap.put("x", mousePos.x);
            mouseMap.put("y", mousePos.y);

            // リストのコピーを作成してイテレーション（ループ中の追加削除に備える）
            List<MascotInstance> currentInstances = new ArrayList<>(mascotInstances);

            for (MascotInstance instance : currentInstances) {
                // 既に削除されている場合はスキップ（他から削除された場合など）
                if (!mascotInstances.contains(instance)) continue;

                Mascot mascot = instance.mascot;
                EventDispatcher dispatcher = instance.dispatcher;
                EvaluationContext context = instance.context;
                MascotWindow mascotView = instance.view;

                // 1. イベントをディスパッチして、条件に合うビヘイビアを探します。
                dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, tickCount, this));

                // 2. マスコットのtick()を呼び出し、現在のアクションを実行させます。
                mascot.tick();

                // tick()実行により削除された場合は以降の処理（描画など）をスキップ
                if (!mascotInstances.contains(instance)) continue;

                java.awt.Point floorMove = new java.awt.Point(0, 0);
                java.awt.Point ceilingMove = new java.awt.Point(0, 0);
                java.awt.Point leftWallMove = new java.awt.Point(0, 0);
                java.awt.Point rightWallMove = new java.awt.Point(0, 0);
                
                // 追従処理済みのウィンドウを記録（二重適用防止）
                HWND[] movedWindow = { null };

                // --- 2.5 ウィンドウ追従処理 ---
                // 床の追従
                if (mascot.isGrounded()) {
                    floorMove = applyWindowMove(mascot, instance.currentFloorWindow, instance.currentFloorRect, movedWindow);
                }
                
                // 天井の追従
                if (mascot.isHittingCeiling()) {
                    ceilingMove = applyWindowMove(mascot, instance.currentCeilingWindow, instance.currentCeilingRect, movedWindow);
                }
                
                // 壁の追従
                if (mascot.isHittingLeftWall()) {
                    leftWallMove = applyWindowMove(mascot, instance.currentLeftWallWindow, instance.currentLeftWallRect, movedWindow);
                }
                if (mascot.isHittingRightWall()) {
                    rightWallMove = applyWindowMove(mascot, instance.currentRightWallWindow, instance.currentRightWallRect, movedWindow);
                }

                // --- 3. 物理演算と座標補正 ---
                // マスコットのサイズを取得
                int mascotWidth = mascotView.getMascotWidth();
                int mascotHeight = mascotView.getMascotHeight();

                // ★修正: MascotViewから現在の画像の正確なアンカーポイントを取得する
                java.awt.Point anchor = mascotView.getAnchor();

                // デバッグ: アンカー情報の確認
                if (tickCount % 300 == 0) {
                    System.out.printf("[Main] Mascot Anchor: (%d, %d) Size: %dx%d%n", anchor.x, anchor.y, mascotWidth, mascotHeight);
                }

                double scale = 1.0; // デフォルトスケール
                boolean targetWindowMinimized = false;
                // --- ワークエリアの動的更新 (DPI & マルチモニタ対応) ---
                // マスコットがいるモニタの正確なワークエリアを取得する
                if (mascotView instanceof java.awt.Window) {
                    java.awt.Window window = (java.awt.Window) mascotView;
                    try {
                        Pointer hwndPointer = Native.getComponentPointer(window);
                        MemorySegment hwndSegment = MemorySegment.ofAddress(Pointer.nativeValue(hwndPointer));
                        
                        // 1. 物理ワークエリアとDPIを取得
                        Rectangle physicalWorkArea = NativeWindowUtil.getPhysicalWorkArea(hwndSegment);
                        int dpi = NativeWindowUtil.getDpiForWindow(hwndSegment);
                        scale = (dpi == 0) ? 1.0 : dpi / 96.0; // 0除算ガード

                        Rectangle logicalWorkArea = null;

                        // ★ウィンドウ限定モードの判定
                        if (limitWindow != null) {
                            if (Win32.INSTANCE.IsWindow(limitWindow)) {
                                if (Win32.INSTANCE.IsIconic(limitWindow)) {
                                    targetWindowMinimized = true;
                                } else {
                                    RECT rect = new RECT();
                                    Win32.INSTANCE.GetWindowRect(limitWindow, rect);
                                    // 物理座標 -> 論理座標
                                    int lx = (int) (rect.left / scale);
                                    int ly = (int) (rect.top / scale);
                                    int lw = (int) ((rect.right - rect.left) / scale);
                                    int lh = (int) ((rect.bottom - rect.top) / scale);
                                    logicalWorkArea = new Rectangle(lx, ly, lw, lh);
                                }
                            } else {
                                limitWindow = null; // 無効なウィンドウなら解除
                            }
                        }

                        // 限定モードでない、または解除された場合はモニタのワークエリアを使用
                        // ただし、限定モードで最小化されている場合はここに入らないようにする（デスクトップ全体に解放しないため）
                        if (logicalWorkArea == null && physicalWorkArea != null && (limitWindow == null || !targetWindowMinimized)) {
                            // 2. 論理ワークエリアに変換 (物理 / スケール)
                            int logicalLeft = (int) (physicalWorkArea.x / scale);
                            int logicalTop = (int) (physicalWorkArea.y / scale);
                            int logicalRight = (int) ((physicalWorkArea.x + physicalWorkArea.width) / scale);
                            int logicalBottom = (int) ((physicalWorkArea.y + physicalWorkArea.height) / scale);
                            logicalWorkArea = new Rectangle(logicalLeft, logicalTop, logicalRight - logicalLeft, logicalBottom - logicalTop);
                        }

                        // ワークエリアが変化した場合のみ更新
                        if (logicalWorkArea != null && !logicalWorkArea.equals(workArea)) {
                            System.out.printf("[Main] WorkArea updated (Logical): %s (Scale: %.2f)%n", logicalWorkArea, scale);
                            workArea = logicalWorkArea;
                        }

                        // 3. 物理座標によるウィンドウ配置 (AWTの自動スケーリング回避)
                        // マスコットの論理座標(Anchor位置)から、ウィンドウの左上座標を算出する
                        // 以前は (Width/2, Height) と仮定していたが、実際のAnchorを使用することでズレを防ぐ
                        int logicalX = mascot.getX() - anchor.x;
                        int logicalY = mascot.getY() - anchor.y;
                        
                        int physicalX = (int) (logicalX * scale);
                        int physicalY = (int) (logicalY * scale);
                        int physicalWidth = (int) (mascotWidth * scale);
                        int physicalHeight = (int) (mascotHeight * scale);

                        try {
                            NativeWindowUtil.setWindowPosPhysical(hwndSegment, physicalX, physicalY, physicalWidth, physicalHeight);
                        } catch (Throwable t) {
                            // ネイティブ呼び出し失敗時のフォールバック: AWTで配置（DPIズレのリスクはあるが表示はされる）
                            // System.err.println("[Main] Native setWindowPos failed, falling back to AWT: " + t.getMessage());
                            window.setBounds(logicalX, logicalY, mascotWidth, mascotHeight);
                        }

                    } catch (Exception e) {
                        // 取得失敗時は前回の値を維持
                    }
                }

                // 環境情報の取得（床、天井、壁の位置）
                // ウィンドウが動いている場合のみ、そのウィンドウを「前回乗っていたウィンドウ」として渡し、粘着力を高める
                HWND floorWindowForEnv = (floorMove.x != 0 || floorMove.y != 0) ? instance.currentFloorWindow : null;
                HWND ceilingWindowForEnv = (ceilingMove.x != 0 || ceilingMove.y != 0) ? instance.currentCeilingWindow : null;
                HWND leftWallWindowForEnv = (leftWallMove.x != 0 || leftWallMove.y != 0) ? instance.currentLeftWallWindow : null;
                HWND rightWallWindowForEnv = (rightWallMove.x != 0 || rightWallMove.y != 0) ? instance.currentRightWallWindow : null;

                Environment.EnvironmentInfo envInfo = Environment.getInstance().getEnvironmentInfo(
                        mascot.getX(), mascot.getY(), mascotWidth, mascotHeight, workArea,
                        floorWindowForEnv, ceilingWindowForEnv, leftWallWindowForEnv, rightWallWindowForEnv, mascot.getHoldingWindow(), mascot.getTargetWindow());

                // ★★★ 修正: Environmentが返す物理座標を論理座標に正規化する ★★★
                // EnvironmentはWin32 API(物理座標)でウィンドウ位置を取得しているが、
                // マスコットは論理座標で動作するため、ここでDPIスケール分だけ縮小して整合性を取る。
                // ただし、Windowがnullの場合(画面端など)は既にworkArea(論理)を使っているため変換しない。
                if (scale != 1.0) {
                    if (envInfo.floorWindow != null) {
                        envInfo.floorY = (int) (envInfo.floorY / scale);
                        if (envInfo.floorRect != null) scaleRect(envInfo.floorRect, scale);
                    }
                    if (envInfo.ceilingWindow != null) {
                        envInfo.ceilingY = (int) (envInfo.ceilingY / scale);
                        if (envInfo.ceilingRect != null) scaleRect(envInfo.ceilingRect, scale);
                    }
                    if (envInfo.leftWallWindow != null) {
                        envInfo.leftWallX = (int) (envInfo.leftWallX / scale);
                        if (envInfo.leftWallRect != null) scaleRect(envInfo.leftWallRect, scale);
                    }
                    if (envInfo.rightWallWindow != null) {
                        envInfo.rightWallX = (int) (envInfo.rightWallX / scale);
                        if (envInfo.rightWallRect != null) scaleRect(envInfo.rightWallRect, scale);
                    }
                    
                    // デバッグ: ウィンドウに乗っている場合の座標確認
                    if (envInfo.floorWindow != null && tickCount % 60 == 0) {
                        System.out.printf("[Main] On Window Floor (Logical): Y=%d, Scale=%.2f%n", envInfo.floorY, scale);
                    }
                }

                // ★★★ 修正: Environmentが返す床のY座標を、workAreaの底でクリップする ★★★
                // これにより、ウィンドウが見つからない場合にfloorYが意図せず大きな値になり、
                // タスクバーを貫通して落下し続ける問題を防止する。
                int effectiveFloorY = Math.min(envInfo.floorY, workArea.y + workArea.height);

                // ★デバッグ: マスコットが画面下部にいるときの座標情報を出力
                if (mascot.getY() > workArea.height - 200 && tickCount % 60 == 0) {
                    System.out.printf("[Debug] MascotY=%d, FloorY=%d, WorkAreaH=%d, Grounded=%b%n", 
                        mascot.getY(), effectiveFloorY, workArea.height, mascot.isGrounded());
                }

                // 現在の床情報を保存（次フレームの追従用）
                instance.currentFloorWindow = envInfo.floorWindow;
                instance.currentFloorRect = envInfo.floorRect;
                mascot.setFloorWindow(envInfo.floorWindow); // マスコットにも足元のウィンドウを伝える
                instance.currentCeilingWindow = envInfo.ceilingWindow;
                instance.currentCeilingRect = envInfo.ceilingRect;
                instance.currentLeftWallWindow = envInfo.leftWallWindow;
                instance.currentLeftWallRect = envInfo.leftWallRect;
                instance.currentRightWallWindow = envInfo.rightWallWindow;
                instance.currentRightWallRect = envInfo.rightWallRect;
                mascot.setLeftWallWindow(envInfo.leftWallWindow);
                mascot.setRightWallWindow(envInfo.rightWallWindow);

                // 接地判定と座標補正
                boolean wasGrounded = mascot.isGrounded();
                boolean isNowGrounded = false;

                // バウンド処理用の定数
                double bounceFactor = 0.6;
                int bounceThreshold = 10;

                if (mascot.getY() >= effectiveFloorY) {
                    // ドラッグ中でなく、かつ落下速度が閾値を超えている場合はバウンド
                    if (!mascot.isBeingDragged() && mascot.getVelocityY() > bounceThreshold) {
                        mascot.setY(effectiveFloorY);
                        mascot.setVelocityY((int)(-mascot.getVelocityY() * bounceFactor));
                        // 床摩擦
                        mascot.setVelocityX((int)(mascot.getVelocityX() * 0.8));
                        isNowGrounded = false; // バウンド中は接地ではない
                    } else {
                        isNowGrounded = true;
                    }
                }

                // 吸着処理強化: 落下中かつ床の直前(5px以内)にいる場合、DPI誤差を考慮して接地とみなす
                if (!isNowGrounded && mascot.getVelocityY() >= 0 && mascot.getY() >= effectiveFloorY - 5) {
                    // バウンドしない程度の速度なら接地
                    if (mascot.getVelocityY() <= bounceThreshold) {
                        isNowGrounded = true;
                    }
                }

                // ウィンドウが下に動いた場合、追従の遅れで一時的に浮いてしまうのを防ぐため、
                // 前回と同じ床に乗っていて、かつジャンプ中（VelocityY < 0）でなければ、
                // 移動量に応じた許容範囲内なら接地しているとみなす（吸着処理）
                if (!isNowGrounded && wasGrounded && envInfo.floorWindow != null && envInfo.floorWindow.equals(instance.currentFloorWindow)) {
                    int tolerance = (floorMove.y > 0) ? floorMove.y + 10 : 5; // ウィンドウが動いた分 + α を許容
                    if (mascot.getY() >= effectiveFloorY - tolerance && mascot.getVelocityY() >= 0) {
                        isNowGrounded = true;
                    }
                }

                mascot.setGrounded(isNowGrounded);

                if (isNowGrounded) {
                    mascot.setY(effectiveFloorY);
                    mascot.setVelocityY(0);
                    // 接地中は摩擦で減速させる（アクションがない場合のみ）
                    if (mascot.getCurrentAction() == null) {
                        mascot.setVelocityX((int)(mascot.getVelocityX() * 0.8));
                        if (Math.abs(mascot.getVelocityX()) < 1) mascot.setVelocityX(0);
                    }
                }

                // 接地していない、かつアクション中でない場合、重力を適用する
                // 環境認識の後に移動することで、移動直後のフレームで不当に落下して接地判定が外れるのを防ぐ
                if (!mascot.isGrounded() && mascot.getCurrentAction() == null && !mascot.isHittingCeiling() && !mascot.isBeingDragged()) {
                    mascot.setY(mascot.getY() + currentGravity);
                }

                // 接地状態が変化した場合、イベントを発行する
                if (isNowGrounded != wasGrounded) {
                    dispatcher.evaluateTriggers(new EventEnvelope<>(
                        EventType.MASCOT_STATE_CHANGED,
                        new StateChangeEvent("isGrounded", wasGrounded, isNowGrounded),
                        mascot));
                }

                // 壁衝突判定と座標補正
                // ★修正: アンカーポイントに基づいて左右の衝突境界を計算する
                
                // 左壁吸着: ウィンドウが左(dx < 0)に動いて壁が離れる場合、許容範囲を広げる
                int leftWallTolerance = 0;
                if (mascot.isHittingLeftWall() && envInfo.leftWallWindow != null && envInfo.leftWallWindow.equals(instance.currentLeftWallWindow)) {
                    leftWallTolerance = (leftWallMove.x < 0) ? -leftWallMove.x + 10 : 10;
                }
                boolean isHittingLeftWall = (mascot.getX() - anchor.x) <= envInfo.leftWallX + leftWallTolerance;

                // 右壁吸着: ウィンドウが右(dx > 0)に動いて壁が離れる場合、許容範囲を広げる
                int rightWallTolerance = 0;
                if (mascot.isHittingRightWall() && envInfo.rightWallWindow != null && envInfo.rightWallWindow.equals(instance.currentRightWallWindow)) {
                    rightWallTolerance = (rightWallMove.x > 0) ? rightWallMove.x + 10 : 10;
                }
                boolean isHittingRightWall = (mascot.getX() + (mascotWidth - anchor.x)) >= envInfo.rightWallX - rightWallTolerance;

                // 天井吸着: ウィンドウが上(dy < 0)に動いて天井が離れる場合、許容範囲を広げる
                int ceilingTolerance = 0;
                if (mascot.isHittingCeiling() && envInfo.ceilingWindow != null && envInfo.ceilingWindow.equals(instance.currentCeilingWindow)) {
                    ceilingTolerance = (ceilingMove.y < 0) ? -ceilingMove.y + 10 : 10;
                }
                // 足元が画面内にある場合のみ天井判定を行う（初期落下時に天井に張り付かないようにするため）
                // さらに、生成直後（約3秒間 = 3000ms）は天井判定を無効にする
                // ★修正: 生成直後の落下中に天井に張り付かないよう、猶予期間を10秒に延長
                boolean isHittingCeiling = (System.currentTimeMillis() - instance.bornTime > 10000) && (mascot.getY() - anchor.y) <= envInfo.ceilingY + ceilingTolerance;

                mascot.setHittingLeftWall(isHittingLeftWall);
                mascot.setHittingRightWall(isHittingRightWall);
                mascot.setHittingCeiling(isHittingCeiling);

                // ドラッグ中や壁無視フラグが立っていなければ、画面内に押し戻す（壁として機能させる）
                if (!mascot.isBeingDragged() && !mascot.isIgnoringWalls()) {
                    if (isHittingLeftWall) {
                        mascot.setX(envInfo.leftWallX + anchor.x);
                        // バウンド処理
                        if (mascot.getVelocityX() < -bounceThreshold) {
                            mascot.setVelocityX((int)(-mascot.getVelocityX() * bounceFactor));
                        } else {
                            mascot.setVelocityX(0);
                        }
                    }
                    if (isHittingRightWall) {
                        mascot.setX(envInfo.rightWallX - (mascotWidth - anchor.x));
                        // バウンド処理
                        if (mascot.getVelocityX() > bounceThreshold) {
                            mascot.setVelocityX((int)(-mascot.getVelocityX() * bounceFactor));
                        } else {
                            mascot.setVelocityX(0);
                        }
                    }
                    if (isHittingCeiling) {
                        mascot.setY(envInfo.ceilingY + anchor.y);
                        // バウンド処理
                        if (mascot.getVelocityY() < -bounceThreshold) {
                            mascot.setVelocityY((int)(-mascot.getVelocityY() * bounceFactor));
                        } else {
                            mascot.setVelocityY(0);
                        }
                    }
                }

                // 壁の上端までの距離を計算（PullUpアクション判定用）
                int distToWallTop = Integer.MAX_VALUE;
                int signedDistToWallTop = Integer.MAX_VALUE;
                if (mascot.isHittingLeftWall() && instance.currentLeftWallRect != null) {
                    // マスコットの頭上(Y - Anchor.y)と壁の上端の距離
                    signedDistToWallTop = (mascot.getY() - anchor.y) - instance.currentLeftWallRect.top;
                    distToWallTop = Math.abs(signedDistToWallTop);
                } else if (mascot.isHittingRightWall() && instance.currentRightWallRect != null) {
                    signedDistToWallTop = (mascot.getY() - anchor.y) - instance.currentRightWallRect.top;
                    distToWallTop = Math.abs(signedDistToWallTop);
                }

                // --- 相互作用判定 (Interaction) ---
                Mascot nearest = getNearestMascot(mascot);
                Map<String, Object> nearestMascotMap = new HashMap<>();
                nearestMascotMap.put("distance", 999999.0); // 十分大きい値
                nearestMascotMap.put("x", 0);
                
                if (nearest != null) {
                    double dx = mascot.getX() - nearest.getX();
                    double dy = mascot.getY() - nearest.getY();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    nearestMascotMap.put("distance", dist);
                    nearestMascotMap.put("x", nearest.getX());

                    // デバッグログ: 近づいたときに距離を表示 (1秒に1回程度)
                    if (dist < 300 && tickCount % 60 == 0) {
                        System.out.printf("[Main] Nearest distance: %.1f (Mascot@%d, Nearest@%d)%n", dist, mascot.getX(), nearest.getX());
                    }
                }

                // 床の端までの距離を計算（Teeterアクション判定用）
                int distToFloorLeft = Integer.MAX_VALUE;
                int distToFloorRight = Integer.MAX_VALUE;
                boolean isOnEdge = false;
                if (mascot.isGrounded() && instance.currentFloorRect != null) {
                    distToFloorLeft = Math.abs(mascot.getX() - instance.currentFloorRect.left);
                    distToFloorRight = Math.abs(mascot.getX() - instance.currentFloorRect.right);
                    
                    // 中心が端から20px以内まで近づいた場合のみ「端」とみなす（手前での発動防止）
                    if (distToFloorLeft < 20 || distToFloorRight < 20) {
                        isOnEdge = true;
                    }
                }

                // コンテキスト変数を更新します。
                context.getVariables().put("time", tickCount);
                context.getVariables().put("mouse", mouseMap);
                context.getVariables().put("distToWallTop", distToWallTop);
                context.getVariables().put("signedDistToWallTop", signedDistToWallTop);
                context.getVariables().put("mascot.distToFloorLeft", distToFloorLeft);
                context.getVariables().put("mascot.distToFloorRight", distToFloorRight);
                context.getVariables().put("isOnEdge", isOnEdge);
                context.getVariables().put("nearestMascot", nearestMascotMap);

                // デバッグ用ログ: 端にいるときの状態を確認
                if (isOnEdge) {
                    System.out.printf("[Debug] isOnEdge=true. isGrounded=%b, Action=%s, DistL=%d, DistR=%d%n",
                        mascot.isGrounded(),
                        (mascot.getCurrentAction() != null ? mascot.getCurrentAction().getClass().getSimpleName() : "null"),
                        distToFloorLeft,
                        distToFloorRight
                    );
                }

                // 4. 描画処理
                if (targetWindowMinimized) {
                    if (mascotView.isVisible()) mascotView.setVisible(false);
                } else {
                    if (!mascotView.isVisible()) {
                        mascotView.setVisible(true);
                    }
                    mascotView.draw();
                }
            }

            tickCount++;

            // 自動復帰チェック
            checkAutoRestoreWindows();

            // 5. FPS制御 (60FPS固定)
            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            long wait = optimalTime - elapsed;

            if (wait > 0) {
                // 残り時間がある場合はスリープしてCPUを休ませる
                Thread.sleep(wait / 1000000);
            } else {
                // 処理落ちしている場合はスリープせず即座に次フレームへ
                // System.out.println("[Main] Frame drop detected!");
            }
        }

        System.out.println("=== Shimeji Neo Shutdown ===");
    }

    /**
     * システムトレイアイコンとメニューを設定します。
     */
    private void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("[Main] SystemTray is not supported.");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            // アイコン画像の読み込み（shime1.pngを使用）
            // 本来は専用のアイコン画像(icon.png等)を用意するのが望ましい
            // ImageCacheを利用することで、外部ファイル優先・リソースフォールバックの恩恵を受ける
            Image image = imageCache.getImage("shime1.png");

            PopupMenu popup = new PopupMenu();

            MenuItem createItem = new MenuItem("増やす");
            createItem.addActionListener(e -> openSkinSelection());
            
            MenuItem settingsItem = new MenuItem("設定");
            settingsItem.addActionListener(e -> openSettings());
            
            MenuItem gatherItem = new MenuItem("あつまれ！");
            gatherItem.addActionListener(e -> gatherAllMascots());

            MenuItem oneItem = new MenuItem("一匹にする");
            oneItem.addActionListener(e -> restoreToOne());

            MenuItem restoreItem = new MenuItem("ウィンドウを戻す");
            restoreItem.addActionListener(e -> restoreWindows());

            MenuItem exitItem = new MenuItem("ばいばい");
            exitItem.addActionListener(e -> System.exit(0));

            popup.add(createItem);
            popup.add(settingsItem);
            popup.add(gatherItem);
            popup.add(oneItem);
            popup.add(restoreItem);
            popup.addSeparator();
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(image, "Shimeji Neo", popup);
            trayIcon.setImageAutoSize(true);

            tray.add(trayIcon);
            System.out.println("[Main] System tray icon added.");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[Main] Failed to setup system tray.");
        }
    }

    private void openSettings() {
        if (config == null || config.getBehaviors() == null) return;

        SwingUtilities.invokeLater(() -> {
            new SettingsWindow(
                config.getBehaviors(),
                this.gravity, this::setGravity,
                this.timeScale, this::setTimeScale,
                () -> setLimitToActiveWindowDelayed(3000), // 3秒後にアクティブなウィンドウに限定
                () -> setLimitWindow(null)                 // 解除
            ).setVisible(true);
        });
    }

    private void openSkinSelection() {
        List<String> skins = getSkins();
        SwingUtilities.invokeLater(() -> {
            new SkinSelectionWindow(skins, (selectedSkin) -> {
                Path newPath = "Default".equals(selectedSkin) ? Path.of("img") : Path.of("img", selectedSkin);
                imageCache.updateBaseDirectory(newPath);
                System.out.println("[Main] Skin selected: " + selectedSkin);
                createMascot();
            }).setVisible(true);
        });
    }

    private List<String> getSkins() {
        List<String> skins = new ArrayList<>();
        skins.add("Default"); // img直下
        File imgDir = new File("img");
        if (imgDir.exists() && imgDir.isDirectory()) {
            File[] files = imgDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        skins.add(f.getName());
                    }
                }
            }
        }
        return skins;
    }

    private void gatherAllMascots() {
        // マウス位置に全マスコットを集める
        java.awt.Point mousePos = java.awt.MouseInfo.getPointerInfo().getLocation();
        for (MascotInstance instance : mascotInstances) {
            instance.mascot.setX(mousePos.x);
            instance.mascot.setY(mousePos.y);
            instance.mascot.setGrounded(false); // 空中から落とす
            instance.mascot.setVelocityX(0);
            instance.mascot.setVelocityY(0);
        }
    }

    private void restoreToOne() {
        // リストのコピーを作成して操作（ループ中の削除回避のため）
        List<MascotInstance> currentInstances = new ArrayList<>(mascotInstances);
        // 1匹目以外を削除
        for (int i = 1; i < currentInstances.size(); i++) {
            removeMascot(currentInstances.get(i).mascot);
        }
    }

    /**
     * 投げられたウィンドウをリストに追加します。
     */
    public void addThrownWindow(HWND window, int x, int y, int width, int height) {
        if (window != null) {
            thrownWindows.add(new ThrownWindowInfo(window, x, y, width, height));
        }
    }

    private void restoreWindows() {
        int count = 0;
        // リストのコピーを作成してイテレーション
        for (ThrownWindowInfo info : new ArrayList<>(thrownWindows)) {
            if (Win32.INSTANCE.IsWindow(info.hwnd)) {
                // 最小化されている場合は復元する
                if (Win32.INSTANCE.IsIconic(info.hwnd)) {
                    User32.INSTANCE.ShowWindow(info.hwnd, WinUser.SW_RESTORE);
                }
                // 元の位置に戻す
                User32.INSTANCE.MoveWindow(info.hwnd, info.originalX, info.originalY, info.width, info.height, true);
                count++;
            }
        }
        thrownWindows.clear();
        System.out.println("[Main] Restored " + count + " windows.");
    }

    private void checkAutoRestoreWindows() {
        long now = System.currentTimeMillis();
        long RESTORE_DELAY = 5000; // 5秒後に復帰
        long ANIMATION_DURATION = 2000; // 2.0秒かけて戻る

        List<ThrownWindowInfo> toRemove = new ArrayList<>();

        for (ThrownWindowInfo info : thrownWindows) {
            // ウィンドウが無効になっていたら削除リストへ
            if (!Win32.INSTANCE.IsWindow(info.hwnd)) {
                toRemove.add(info);
                continue;
            }

            if (!info.isRestoring) {
                // 待機中: 時間が来たらアニメーション開始
                if (now - info.thrownTime >= RESTORE_DELAY) {
                    info.isRestoring = true;
                    info.restoreStartTime = now;

                    // 現在位置を取得して開始位置とする
                    RECT rect = new RECT();
                    Win32.INSTANCE.GetWindowRect(info.hwnd, rect);
                    info.startX = rect.left;
                    info.startY = rect.top;

                    // 最小化されている場合は復元する
                    if (Win32.INSTANCE.IsIconic(info.hwnd)) {
                        User32.INSTANCE.ShowWindow(info.hwnd, WinUser.SW_RESTORE);
                    }
                    System.out.println("[Main] Start restoring window animation: " + info.hwnd);
                }
            } else {
                // アニメーション中
                long elapsed = now - info.restoreStartTime;
                double progress = (double) elapsed / ANIMATION_DURATION;

                if (progress >= 1.0) {
                    // アニメーション完了
                    User32.INSTANCE.MoveWindow(info.hwnd, info.originalX, info.originalY, info.width, info.height, true);
                    System.out.println("[Main] Auto-restored window complete: " + info.hwnd);
                    toRemove.add(info);
                } else {
                    // イージング (EaseOutBack) で少し行き過ぎてから戻る動きに変更
                    double c1 = 1.70158;
                    double c3 = c1 + 1;
                    double ease = 1 + c3 * Math.pow(progress - 1, 3) + c1 * Math.pow(progress - 1, 2);

                    int currentX = (int) (info.startX + (info.originalX - info.startX) * ease);
                    int currentY = (int) (info.startY + (info.originalY - info.startY) * ease);
                    
                    User32.INSTANCE.MoveWindow(info.hwnd, currentX, currentY, info.width, info.height, true);
                }
            }
        }

        thrownWindows.removeAll(toRemove);
    }

    /**
     * ウィンドウの移動を検知し、マスコットを追従させます。
     * @return 移動量（吸着判定用）
     */
    private java.awt.Point applyWindowMove(Mascot mascot, HWND window, RECT previousRect, HWND[] processedWindow) {
        if (window == null || Win32.INSTANCE.IsIconic(window)) return new java.awt.Point(0, 0);
        // 既にこのフレームで追従処理済みのウィンドウならスキップ（床と壁が同じウィンドウの場合など）
        if (processedWindow[0] != null && window.equals(processedWindow[0])) return new java.awt.Point(0, 0);

        RECT rect = new RECT();
        if (Win32.INSTANCE.GetWindowRect(window, rect) != 0) {
            if (rect.right - rect.left > 0 && rect.bottom - rect.top > 0) {
                int dx = rect.left - previousRect.left;
                int dy = rect.top - previousRect.top;
                if (dx != 0 || dy != 0) {
                    mascot.setX(mascot.getX() + dx);
                    mascot.setY(mascot.getY() + dy);
                    processedWindow[0] = window;
                    return new java.awt.Point(dx, dy);
                }
            }
        }
        return new java.awt.Point(0, 0);
    }

    /**
     * 新しいマスコットを生成して管理リストに追加します。
     */
    public void createMascot() {
        // 初期位置をランダムに設定
        int minX = workArea.x + workArea.width / 2;
        int maxX = workArea.x + (int) (workArea.width * 2.0 / 3.0);
        int startX = minX + (int) (Math.random() * (maxX - minX));
        createMascot(startX, workArea.y - 256);
    }

    public void createMascot(int x, int y) {
        createMascot(x, y, 0, 0);
    }

    public void createMascot(int x, int y, int velocityX, int velocityY) {
        if (mascotInstances.size() >= MAX_MASCOTS) {
            System.out.println("[Main] Mascot limit reached (" + MAX_MASCOTS + "). Skipping creation.");
            return;
        }

        Mascot mascot = new Mascot();

        Map<String, Object> contextVariables = new HashMap<>();
        Map<String, Integer> workAreaMap = new HashMap<>();
        workAreaMap.put("x", workArea.x);
        workAreaMap.put("y", workArea.y);
        workAreaMap.put("width", workArea.width);
        workAreaMap.put("height", workArea.height);
        workAreaMap.put("right", workArea.x + workArea.width);
        workAreaMap.put("bottom", workArea.y + workArea.height);
        contextVariables.put("workArea", workAreaMap);

        mascot.setAnchor(new java.awt.Point(x, y));
        mascot.setVelocityX(velocityX);
        mascot.setVelocityY(velocityY);

        contextVariables.put("mascot", mascot);
        contextVariables.put("time", 0L);

        // 初期変数の注入 (ReferenceError回避)
        contextVariables.put("mouse", new HashMap<String, Integer>() {{ put("x", 0); put("y", 0); }});
        contextVariables.put("distToWallTop", 0);
        contextVariables.put("signedDistToWallTop", 0);
        contextVariables.put("mascot.distToFloorLeft", 0);
        contextVariables.put("mascot.distToFloorRight", 0);
        contextVariables.put("isOnEdge", false);
        // 相互作用判定用の初期値を設定
        Map<String, Object> nearestMascotMap = new HashMap<>();
        nearestMascotMap.put("distance", 999999.0);
        nearestMascotMap.put("x", 0);
        contextVariables.put("nearestMascot", nearestMascotMap);

        // GraalJS Context Init
        Context jsContext = ScriptEngineManager.getInstance().createMascotContext(contextVariables);
        mascot.setJsContext(jsContext);

        EvaluationContext context = new EvaluationContext(contextVariables);
        EventDispatcher dispatcher = new EventDispatcher(context, mascot);

        MascotWindow mascotView = new MascotWindow(mascot, imageCache);

        // ビヘイビアの登録
        for (Behavior behavior : config.getBehaviors()) {
            dispatcher.registerTrigger(behavior);
        }

        mascotView.setVisible(true);

        MascotInstance instance = new MascotInstance();
        instance.mascot = mascot;
        instance.view = mascotView;
        instance.dispatcher = dispatcher;
        instance.context = context;
        instance.bornTime = System.currentTimeMillis();

        mascotInstances.add(instance);
        System.out.println("[Main] Created a new mascot instance. Total: " + mascotInstances.size());
    }

    /**
     * 指定されたマスコットを削除します。
     */
    public void removeMascot(Mascot mascot) {
        MascotInstance target = null;
        for (MascotInstance instance : mascotInstances) {
            if (instance.mascot == mascot) {
                target = instance;
                break;
            }
        }
        if (target != null) {
            target.view.setVisible(false); // 画面から消す
            // リソース解放のためにdisposeを試みる
            if (target.view instanceof java.awt.Window) {
                ((java.awt.Window) target.view).dispose();
            }
            // JS Contextの解放
            if (target.mascot.getJsContext() != null) {
                target.mascot.getJsContext().close();
            }
            mascotInstances.remove(target);
            System.out.println("[Main] Removed a mascot. Total: " + mascotInstances.size());
        }
    }

    /**
     * 指定されたマスコットから最も近い別のマスコットを探して返します。
     * 他にマスコットがいない場合は null を返します。
     */
    public Mascot getNearestMascot(Mascot self) {
        Mascot nearest = null;
        double minDistanceSq = Double.MAX_VALUE;

        // リストのコピーを作成してイテレーション
        List<MascotInstance> currentInstances = new ArrayList<>(mascotInstances);

        for (MascotInstance instance : currentInstances) {
            Mascot other = instance.mascot;
            if (other == self) continue;

            double dx = self.getX() - other.getX();
            double dy = self.getY() - other.getY();
            double distanceSq = dx * dx + dy * dy;

            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                nearest = other;
            }
        }
        return nearest;
    }

    private void ensureConfigurationExists() throws IOException {
        Path confDir = Path.of("conf");
        if (!Files.exists(confDir)) {
            Files.createDirectories(confDir);
        }

        Path actionsPath = confDir.resolve("actions.xml");
        // ファイルが既に存在する場合は上書きしない（ユーザー設定を保持するため）
        // ★修正: 開発中は常に最新の設定で上書きする (古い設定による誤動作防止)
        {
        String actionsContent = """
                <Actions>
                    <Action Name="Stay" Type="Stay" Duration="1000">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="Sit" Type="Stay" Duration="8000">
                        <Animation>
                            <Pose Image="shime11.png" ImageAnchor="64,128" Duration="8000" />
                        </Animation>
                    </Action>
                    <Action Name="AfterSit" Type="RandomChoice">
                        <ActionReference Name="Stay" />
                        <ActionReference Name="Stay" />
                        <ActionReference Name="Stay" />
                        <ActionReference Name="LieDown" />
                    </Action>
                    <Action Name="SitSequence" Type="Sequence">
                        <ActionReference Name="Sit" />
                        <ActionReference Name="AfterSit" />
                    </Action>
                    <Action Name="Land" Type="Animate">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="LieDown" Type="LieDown" Duration="4000">
                        <Animation>
                            <Pose Image="shime18.png" ImageAnchor="64,128" Duration="4000" />
                        </Animation>
                    </Action>
                    <Action Name="Breed" Type="Breed" Duration="4000">
                        <Point X="0" Y="-100" />
                        <Animation>
                            <Pose Image="shime11.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="BreedJump" Type="Breed" Duration="4000" VelocityX="10" VelocityY="-25">
                        <Point X="0" Y="-100" />
                        <Animation>
                            <Pose Image="shime11.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="3600" />
                        </Animation>
                    </Action>
                    <Action Name="Dig" Type="Dig" Duration="2000">
                        <Animation>
                            <Pose Image="shime18.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime18.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="Gather" Type="Gather" Speed="1" Duration="4000">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="Fall" Type="Fall">
                        <Animation>
                            <Pose Image="shime4.png" ImageAnchor="64,128" Duration="100" />
                        </Animation>
                    </Action>
                    <Action Name="Walk" Type="Walk" Speed="1">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="Chase" Type="Chase" Speed="4" Duration="5000">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="50" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="50" />
                        </Animation>
                    </Action>
                    <Action Name="Dragged" Type="Dragged">
                        <Animation>
                            <!-- 順序: 左速, 左遅, 静止, 右遅, 右速 -->
                            <!-- 左・速 (例: 走る/飛ぶポーズ) -->
                            <Pose Image="shime4.png" ImageAnchor="64,128" Duration="100" />
                            <!-- 左・遅 (例: 歩く/揺れるポーズ) -->
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="100" />
                            <!-- 静止 (例: つままれポーズ) -->
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="100" />
                            <!-- 右・遅 -->
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="100" />
                            <!-- 右・速 -->
                            <Pose Image="shime4.png" ImageAnchor="64,128" Duration="100" />
                        </Animation>
                    </Action>
                    <Action Name="Jump" Type="Jump" VelocityY="12" VelocityX="3">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="JumpLeft" Type="Jump" VelocityY="12" VelocityX="-3">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="RandomJumpAction" Type="RandomChoice">
                        <ActionReference Name="Jump" />
                        <ActionReference Name="JumpLeft" />
                    </Action>
                    <Action Name="TripFall" Type="Animate">
                        <Animation>
                            <Pose Image="shime18.png" ImageAnchor="64,128" Duration="500" />
                        </Animation>
                    </Action>
                    <Action Name="TripSequence" Type="Sequence">
                        <ActionReference Name="TripFall" />
                        <ActionReference Name="Sit" />
                    </Action>
                    <Action Name="WallCling" Type="WallCling" Duration="2000">
                        <Animation>
                            <Pose Image="shime15.png" ImageAnchor="64,128" Duration="2000" />
                        </Animation>
                    </Action>
                    <Action Name="Climb" Type="Climb" Speed="1">
                        <Animation>
                            <Pose Image="shime15.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime16.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="Teeter" Type="Teeter" Duration="2000" FallProbability="0.2">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="150" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="150" />
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="150" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="150" />
                        </Animation>
                    </Action>
                    <Action Name="PullUp" Type="PullUp" Duration="2000">
                        <Animation>
                            <Pose Image="shime15.png" ImageAnchor="64,128" Duration="500" />
                            <Pose Image="shime13.png" ImageAnchor="64,128" Duration="1500" />
                        </Animation>
                    </Action>
                    <Action Name="CeilingCrawl" Type="CeilingCrawl" Speed="1" Duration="2000">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="CeilingStay" Type="Stay" Duration="5000">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="CeilingRandomMove" Type="RandomChoice">
                        <ActionReference Name="CeilingCrawl" />
                        <ActionReference Name="CeilingStay" />
                    </Action>
                    <Action Name="SlideDown" Type="SlideDown" Speed="2">
                        <Animation>
                            <Pose Image="shime17.png" ImageAnchor="64,128" Duration="400" />
                        </Animation>
                    </Action>
                    <Action Name="WallJump" Type="WallJump" VelocityY="12" VelocityX="10">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="WallRandomMove" Type="RandomChoice">
                        <ActionReference Name="Climb" />
                    </Action>
                    <Action Name="WallComplexSequence" Type="Sequence">
                        <ActionReference Name="WallCling" />
                        <ActionReference Name="WallRandomMove" />
                    </Action>
                    <Action Name="FallSequence" Type="Sequence">
                        <ActionReference Name="Fall" />
                        <ActionReference Name="LieDown" />
                        <ActionReference Name="Land" />
                    </Action>
                    <Action Name="Turn" Type="Turn" />
                    <Action Name="Grab" Type="Grab" Duration="5000">
                        <Animation>
                            <Pose Image="shime11.png" ImageAnchor="64,128" Duration="5000" />
                        </Animation>
                    </Action>
                    <Action Name="Throw" Type="Throw">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="50" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="50" />
                        </Animation>
                    </Action>
                    <Action Name="Bow" Type="Animate">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime34.png" ImageAnchor="64,128" Duration="500" />
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="LookRight" Type="Look" VelocityX="1" />
                    <Action Name="LookLeft" Type="Look" VelocityX="-1" />
                    <Action Name="GreetSequenceRight" Type="Sequence">
                        <ActionReference Name="LookRight" />
                        <ActionReference Name="Bow" />
                    </Action>
                    <Action Name="GreetSequenceLeft" Type="Sequence">
                        <ActionReference Name="LookLeft" />
                        <ActionReference Name="Bow" />
                    </Action>
                </Actions>
                """;
        Files.writeString(actionsPath, actionsContent);
        System.out.println("[Main] Updated actions.xml");
        }

        Path behaviorsPath = confDir.resolve("behaviors.xml");
        // ★修正: 開発中は常に最新の設定で上書きする
        {
        String behaviorsContent = """
                <Behaviors>
                    <Behavior Name="Dragged" Frequency="100">
                        <Condition>mascot.isBeingDragged()</Condition>
                        <ActionReference Name="Dragged" />
                    </Behavior>
                    <Behavior Name="JumpOnClick" Frequency="100">
                        <Condition>event.type == 'MOUSE_PRESSED'</Condition>
                        <ActionReference Name="Jump" />
                    </Behavior>
                    <Behavior Name="Fall" Frequency="100">
                        <Condition>!mascot.isGrounded() &amp;&amp; !mascot.isHittingLeftWall() &amp;&amp; !mascot.isHittingRightWall() &amp;&amp; !mascot.isHittingCeiling() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="FallSequence" />
                    </Behavior>
                    <Behavior Name="Teeter" Frequency="5000">
                        <Condition>mascot.isGrounded() &amp;&amp; isOnEdge &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Teeter" />
                    </Behavior>
                    <Behavior Name="PullUp" Frequency="200">
                        <Condition>(mascot.isHittingLeftWall() || mascot.isHittingRightWall()) &amp;&amp; signedDistToWallTop &lt; -30 &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="PullUp" />
                    </Behavior>
                    <Behavior Name="WallAction" Frequency="100">
                        <Condition>(mascot.isHittingLeftWall() || mascot.isHittingRightWall()) &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="WallComplexSequence" />
                    </Behavior>
                    <Behavior Name="CeilingAction" Frequency="100">
                        <Condition>mascot.isHittingCeiling() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="CeilingRandomMove" />
                    </Behavior>
                    <Behavior Name="TurnRandomly" Frequency="10">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Turn" />
                    </Behavior>
                    <Behavior Name="ChaseMouse" Frequency="30">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null &amp;&amp; ((mascot.getX() - mouse.x &gt; 150) || (mouse.x - mascot.getX() &gt; 150))</Condition>
                        <ActionReference Name="Chase" />
                    </Behavior>
                    <Behavior Name="Walk" Frequency="100">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Walk" />
                    </Behavior>
                    <Behavior Name="Trip" Frequency="10">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="TripSequence" />
                    </Behavior>
                    <Behavior Name="RandomJump" Frequency="10">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="RandomJumpAction" />
                    </Behavior>
                    <Behavior Name="Sit" Frequency="50">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="SitSequence" />
                    </Behavior>
                    <Behavior Name="Breed" Frequency="2">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Breed" />
                    </Behavior>
                    <Behavior Name="BreedJump" Frequency="2">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="BreedJump" />
                    </Behavior>
                    <Behavior Name="Dig" Frequency="1">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Dig" />
                    </Behavior>
                    <Behavior Name="Gather" Frequency="5">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Gather" />
                    </Behavior>
                    <Behavior Name="Stay" Frequency="100">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Stay" />
                    </Behavior>
                    <Behavior Name="Grab" Frequency="50">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getFloorWindow() != null &amp;&amp; !isOnEdge &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Grab" />
                    </Behavior>
                    <Behavior Name="Throw" Frequency="2">
                        <Condition>mascot.isGrounded() &amp;&amp; mascot.getFloorWindow() != null &amp;&amp; mascot.getCurrentAction() == null</Condition>
                        <ActionReference Name="Throw" />
                    </Behavior>
                    <Behavior Name="GreetRight" Frequency="5">
                        <Condition>mascot.isGrounded() &amp;&amp; nearestMascot.distance &lt; 150 &amp;&amp; nearestMascot.x &gt;= mascot.getX()</Condition>
                        <ActionReference Name="GreetSequenceRight" />
                    </Behavior>
                    <Behavior Name="GreetLeft" Frequency="5">
                        <Condition>mascot.isGrounded() &amp;&amp; nearestMascot.distance &lt; 150 &amp;&amp; nearestMascot.x &lt; mascot.getX()</Condition>
                        <ActionReference Name="GreetSequenceLeft" />
                    </Behavior>
                </Behaviors>
                """;
        Files.writeString(behaviorsPath, behaviorsContent);
        System.out.println("[Main] Updated behaviors.xml");
        }
    }

    /**
     * RECT構造体の値をDPIスケールで除算して論理座標に変換するヘルパー
     */
    private void scaleRect(RECT rect, double scale) {
        if (rect == null || scale == 0) return;
        rect.left = (int) (rect.left / scale);
        rect.right = (int) (rect.right / scale);
        rect.top = (int) (rect.top / scale);
        rect.bottom = (int) (rect.bottom / scale);
    }
}

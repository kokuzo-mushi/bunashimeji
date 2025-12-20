package com.group_finity.mascot;

import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.Configuration;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.nativeaccess.Win32;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.StateChangeEvent;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.image.ImageCache;
import com.group_finity.mascot.view.MascotView;

import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
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
import javax.imageio.ImageIO;
import java.io.File;

/**
 * アプリケーションのメインエントリーポイント。
 * 設定を読み込み、マスコットを生成し、メインループを開始します。
 */
public class Main {

    // マスコット1体分の管理情報をまとめるクラス
    private static class MascotInstance {
        Mascot mascot;
        MascotView view;
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

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    private static final int MAX_MASCOTS = 8;
    private final List<MascotInstance> mascotInstances = new ArrayList<>();
    private Configuration config;
    private ImageCache imageCache;
    private Rectangle workArea;

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("An unexpected error occurred. Exiting.");
        }
    }

    public void run() throws InterruptedException {
        instance = this;
        System.out.println("=== Shimeji Neo Start ===");
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

        // --- 2️⃣ 環境情報の取得 ---
        workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        System.out.printf("[Main] Work area detected: %s%n", workArea);

        // 画像キャッシュの初期化
        imageCache = new ImageCache(Path.of("img"));

        // システムトレイの初期化
        setupSystemTray();

        // --- 3️⃣ マスコットの生成 ---
        // 最初の一体を生成
        createMascot();

        // --- 4️⃣ メインループ ---
        System.out.println("[Main] Starting main loop... (Press Ctrl+C to exit)");
        long tickCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            final int GRAVITY = 3; // 1フレームあたりの落下量

            // マウス座標の更新
            java.awt.Point mousePos = java.awt.MouseInfo.getPointerInfo().getLocation();

            // リストのコピーを作成してイテレーション（ループ中の追加削除に備える）
            List<MascotInstance> currentInstances = new ArrayList<>(mascotInstances);

            for (MascotInstance instance : currentInstances) {
                // 既に削除されている場合はスキップ（他から削除された場合など）
                if (!mascotInstances.contains(instance)) continue;

                Mascot mascot = instance.mascot;
                EventDispatcher dispatcher = instance.dispatcher;
                EvaluationContext context = instance.context;
                MascotView mascotView = instance.view;

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

                // 接地していない、かつアクション中でない場合、重力を適用する（環境認識の前に移動）
                // これにより、重力適用後の位置で接地判定が行われ、めり込みが即座に補正される
                if (!mascot.isGrounded() && mascot.getCurrentAction() == null && !mascot.isHittingCeiling() && !mascot.isBeingDragged()) {
                    mascot.setY(mascot.getY() + GRAVITY);
                }

                // --- 3. 物理演算と座標補正 ---
                // マスコットのサイズを取得
                int mascotWidth = mascotView.getMascotWidth();
                int mascotHeight = mascotView.getMascotHeight();

                // 環境情報の取得（床、天井、壁の位置）
                // ウィンドウが動いている場合のみ、そのウィンドウを「前回乗っていたウィンドウ」として渡し、粘着力を高める
                HWND floorWindowForEnv = (floorMove.x != 0 || floorMove.y != 0) ? instance.currentFloorWindow : null;
                HWND ceilingWindowForEnv = (ceilingMove.x != 0 || ceilingMove.y != 0) ? instance.currentCeilingWindow : null;
                HWND leftWallWindowForEnv = (leftWallMove.x != 0 || leftWallMove.y != 0) ? instance.currentLeftWallWindow : null;
                HWND rightWallWindowForEnv = (rightWallMove.x != 0 || rightWallMove.y != 0) ? instance.currentRightWallWindow : null;

                Environment.EnvironmentInfo envInfo = Environment.getInstance().getEnvironmentInfo(
                        mascot.getX(), mascot.getY(), mascotWidth, mascotHeight, workArea,
                        floorWindowForEnv, ceilingWindowForEnv, leftWallWindowForEnv, rightWallWindowForEnv);

                // 現在の床情報を保存（次フレームの追従用）
                instance.currentFloorWindow = envInfo.floorWindow;
                instance.currentFloorRect = envInfo.floorRect;
                instance.currentCeilingWindow = envInfo.ceilingWindow;
                instance.currentCeilingRect = envInfo.ceilingRect;
                instance.currentLeftWallWindow = envInfo.leftWallWindow;
                instance.currentLeftWallRect = envInfo.leftWallRect;
                instance.currentRightWallWindow = envInfo.rightWallWindow;
                instance.currentRightWallRect = envInfo.rightWallRect;

                // 接地判定と座標補正
                boolean wasGrounded = mascot.isGrounded();
                boolean isNowGrounded = mascot.getY() >= envInfo.floorY;

                // ウィンドウが下に動いた場合、追従の遅れで一時的に浮いてしまうのを防ぐため、
                // 前回と同じ床に乗っていて、かつジャンプ中（VelocityY < 0）でなければ、
                // 移動量に応じた許容範囲内なら接地しているとみなす（吸着処理）
                if (!isNowGrounded && wasGrounded && envInfo.floorWindow != null && envInfo.floorWindow.equals(instance.currentFloorWindow)) {
                    int tolerance = (floorMove.y > 0) ? floorMove.y + 10 : 5; // ウィンドウが動いた分 + α を許容
                    if (mascot.getY() >= envInfo.floorY - tolerance && mascot.getVelocityY() >= 0) {
                        isNowGrounded = true;
                    }
                }

                mascot.setGrounded(isNowGrounded);

                if (isNowGrounded) {
                    mascot.setY(envInfo.floorY);
                }

                // 接地状態が変化した場合、イベントを発行する
                if (isNowGrounded != wasGrounded) {
                    dispatcher.evaluateTriggers(new EventEnvelope<>(
                        EventType.MASCOT_STATE_CHANGED,
                        new StateChangeEvent("isGrounded", wasGrounded, isNowGrounded),
                        mascot));
                }

                // 壁衝突判定と座標補正
                int halfWidth = mascotWidth / 2;
                
                // 左壁吸着: ウィンドウが左(dx < 0)に動いて壁が離れる場合、許容範囲を広げる
                int leftWallTolerance = 0;
                if (mascot.isHittingLeftWall() && envInfo.leftWallWindow != null && envInfo.leftWallWindow.equals(instance.currentLeftWallWindow)) {
                    leftWallTolerance = (leftWallMove.x < 0) ? -leftWallMove.x + 10 : 10;
                }
                boolean isHittingLeftWall = (mascot.getX() - halfWidth) <= envInfo.leftWallX + leftWallTolerance;

                // 右壁吸着: ウィンドウが右(dx > 0)に動いて壁が離れる場合、許容範囲を広げる
                int rightWallTolerance = 0;
                if (mascot.isHittingRightWall() && envInfo.rightWallWindow != null && envInfo.rightWallWindow.equals(instance.currentRightWallWindow)) {
                    rightWallTolerance = (rightWallMove.x > 0) ? rightWallMove.x + 10 : 10;
                }
                boolean isHittingRightWall = (mascot.getX() + halfWidth) >= envInfo.rightWallX - rightWallTolerance;

                // 天井吸着: ウィンドウが上(dy < 0)に動いて天井が離れる場合、許容範囲を広げる
                int ceilingTolerance = 0;
                if (mascot.isHittingCeiling() && envInfo.ceilingWindow != null && envInfo.ceilingWindow.equals(instance.currentCeilingWindow)) {
                    ceilingTolerance = (ceilingMove.y < 0) ? -ceilingMove.y + 10 : 10;
                }
                // 足元が画面内にある場合のみ天井判定を行う（初期落下時に天井に張り付かないようにするため）
                // さらに、生成直後（約3秒間 = 3000ms）は天井判定を無効にする
                boolean isHittingCeiling = (System.currentTimeMillis() - instance.bornTime > 3000) && (mascot.getY() - mascotHeight) <= envInfo.ceilingY + ceilingTolerance && mascot.getY() > envInfo.ceilingY;

                mascot.setHittingLeftWall(isHittingLeftWall);
                mascot.setHittingRightWall(isHittingRightWall);
                mascot.setHittingCeiling(isHittingCeiling);

                // ドラッグ中でなければ、画面内に押し戻す（壁として機能させる）
                if (!mascot.isBeingDragged()) {
                    if (isHittingLeftWall) {
                        mascot.setX(envInfo.leftWallX + halfWidth);
                    }
                    if (isHittingRightWall) {
                        mascot.setX(envInfo.rightWallX - halfWidth);
                    }
                    if (isHittingCeiling) {
                        mascot.setY(envInfo.ceilingY + mascotHeight);
                    }
                }

                // コンテキスト変数を更新します。
                context.getVariables().put("time", tickCount);
                context.getVariables().put("mouse.x", mousePos.x);
                context.getVariables().put("mouse.y", mousePos.y);

                // 4. 描画処理
                mascotView.update();
            }

            tickCount++;

            // 5. 少し待機して、CPU使用率を抑えます。
            Thread.sleep(30); // 約33 FPS
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
            File iconFile = new File("img/shime1.png");
            Image image = null;
            if (iconFile.exists()) {
                image = ImageIO.read(iconFile);
            } else {
                // 画像がない場合のフォールバック（16x16の空画像）
                image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            }

            PopupMenu popup = new PopupMenu();

            MenuItem createItem = new MenuItem("増やす");
            createItem.addActionListener(e -> createMascot());
            
            MenuItem gatherItem = new MenuItem("あつまれ！");
            gatherItem.addActionListener(e -> gatherAllMascots());

            MenuItem oneItem = new MenuItem("一匹にする");
            oneItem.addActionListener(e -> restoreToOne());

            MenuItem exitItem = new MenuItem("ばいばい");
            exitItem.addActionListener(e -> System.exit(0));

            popup.add(createItem);
            popup.add(gatherItem);
            popup.add(oneItem);
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
        contextVariables.put("workArea.x", workArea.x);
        contextVariables.put("workArea.y", workArea.y);
        contextVariables.put("workArea.width", workArea.width);
        contextVariables.put("workArea.height", workArea.height);
        contextVariables.put("workArea.right", workArea.x + workArea.width);
        contextVariables.put("workArea.bottom", workArea.y + workArea.height);

        mascot.setAnchor(new java.awt.Point(x, y));
        mascot.setVelocityX(velocityX);
        mascot.setVelocityY(velocityY);

        contextVariables.put("mascot", mascot);
        contextVariables.put("time", 0L);

        EvaluationContext context = new EvaluationContext(contextVariables);
        EventDispatcher dispatcher = new EventDispatcher(context, mascot);

        MascotView mascotView = new MascotView(mascot, imageCache, dispatcher);

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
        if (!Files.exists(actionsPath)) {
            String content = """
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
                    <Action Name="BreedJump" Type="Breed" Duration="4000" VelocityX="20" VelocityY="-40">
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
                    <Action Name="Gather" Type="Gather" Speed="2" Duration="4000">
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
                    <Action Name="Walk" Type="Walk" Speed="2">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
                    </Action>
                    <Action Name="Chase" Type="Chase" Speed="8" Duration="5000">
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
                    <Action Name="Jump" Type="Jump" VelocityY="20" VelocityX="5">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="JumpLeft" Type="Jump" VelocityY="20" VelocityX="-5">
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
                    <Action Name="Climb" Type="Climb" Speed="2">
                        <Animation>
                            <Pose Image="shime15.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime16.png" ImageAnchor="64,128" Duration="200" />
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
                    <Action Name="SlideDown" Type="SlideDown" Speed="4">
                        <Animation>
                            <Pose Image="shime17.png" ImageAnchor="64,128" Duration="400" />
                        </Animation>
                    </Action>
                    <Action Name="WallJump" Type="WallJump" VelocityY="20" VelocityX="15">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
                    </Action>
                    <Action Name="WallRandomMove" Type="RandomChoice">
                        <ActionReference Name="Climb" />
                        <ActionReference Name="SlideDown" />
                        <ActionReference Name="WallJump" />
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
                </Actions>
                """;
            Files.writeString(actionsPath, content);
            System.out.println("[Main] Created default actions.xml");
        }

        Path behaviorsPath = confDir.resolve("behaviors.xml");
        if (!Files.exists(behaviorsPath)) {
            String content = """
                <Behaviors>
                    <Behavior Name="Dragged" Frequency="100">
                        <Condition>mascot.isBeingDragged</Condition>
                        <ActionReference Name="Dragged" />
                    </Behavior>
                    <Behavior Name="JumpOnClick" Frequency="100">
                        <Condition>event.type == 'MOUSE_PRESSED'</Condition>
                        <ActionReference Name="Jump" />
                    </Behavior>
                    <Behavior Name="Fall" Frequency="100">
                        <Condition>!mascot.isGrounded &amp;&amp; !mascot.isHittingLeftWall &amp;&amp; !mascot.isHittingRightWall &amp;&amp; !mascot.isHittingCeiling &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="FallSequence" />
                    </Behavior>
                    <Behavior Name="WallAction" Frequency="100">
                        <Condition>(mascot.isHittingLeftWall || mascot.isHittingRightWall) &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="WallComplexSequence" />
                    </Behavior>
                    <Behavior Name="CeilingAction" Frequency="100">
                        <Condition>mascot.isHittingCeiling &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="CeilingRandomMove" />
                    </Behavior>
                    <Behavior Name="TurnRandomly" Frequency="10">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="Turn" />
                    </Behavior>
                    <Behavior Name="ChaseMouse" Frequency="30">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null &amp;&amp; ((mascot.x - mouse.x &gt; 150) || (mouse.x - mascot.x &gt; 150))</Condition>
                        <ActionReference Name="Chase" />
                    </Behavior>
                    <Behavior Name="Walk" Frequency="100">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="Walk" />
                    </Behavior>
                    <Behavior Name="Trip" Frequency="10">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="TripSequence" />
                    </Behavior>
                    <Behavior Name="RandomJump" Frequency="10">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="RandomJumpAction" />
                    </Behavior>
                    <Behavior Name="Sit" Frequency="50">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="SitSequence" />
                    </Behavior>
                    <Behavior Name="Breed" Frequency="2">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="Breed" />
                    </Behavior>
                    <Behavior Name="BreedJump" Frequency="2">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="BreedJump" />
                    </Behavior>
                    <Behavior Name="Dig" Frequency="1">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="Dig" />
                    </Behavior>
                    <Behavior Name="Gather" Frequency="5">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="Gather" />
                    </Behavior>
                    <Behavior Name="Stay" Frequency="100">
                        <Condition>mascot.isGrounded &amp;&amp; mascot.currentAction == null</Condition>
                        <ActionReference Name="Stay" />
                    </Behavior>
                </Behaviors>
                """;
            Files.writeString(behaviorsPath, content);
            System.out.println("[Main] Created default behaviors.xml");
        }
    }
}

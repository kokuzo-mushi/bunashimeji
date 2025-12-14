package com.group_finity.mascot;

import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.Configuration;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.StateChangeEvent;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.image.ImageCache;
import com.group_finity.mascot.view.MascotView;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * アプリケーションのメインエントリーポイント。
 * 設定を読み込み、マスコットを生成し、メインループを開始します。
 */
public class Main {

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("An unexpected error occurred. Exiting.");
        }
    }

    public void run() throws InterruptedException {
        System.out.println("=== Shimeji Neo Start ===");
        System.out.println("[Main] Current working directory: " + System.getProperty("user.dir"));

        try { ensureConfigurationExists(); } catch (IOException e) { e.printStackTrace(); }

        // --- 1️⃣ 設定の読み込み ---
        // actions.xml と behaviors.xml からアクションとビヘイビアの定義を読み込みます。
        Configuration config = new Configuration(Path.of("conf/actions.xml"), Path.of("conf/behaviors.xml"));
        List<Behavior> behaviors = config.getBehaviors();

        if (behaviors == null || behaviors.isEmpty()) {
            System.err.println("No behaviors found in configuration. The mascot will not do anything.");
            return;
        }

        // --- 2️⃣ マスコットとイベントシステムの初期化 ---
        Mascot mascot = new Mascot();

        Map<String, Object> contextVariables = new HashMap<>();
        // NOTE: ここで定義する変数が、behaviors.xml の <condition> で使用できます。

        // --- 2.6. 環境情報の取得とコンテキストへの追加 ---
        Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        contextVariables.put("workArea.x", workArea.x);
        contextVariables.put("workArea.y", workArea.y);
        contextVariables.put("workArea.width", workArea.width);
        contextVariables.put("workArea.height", workArea.height);
        contextVariables.put("workArea.right", workArea.x + workArea.width);
        contextVariables.put("workArea.bottom", workArea.y + workArea.height);
        System.out.printf("[Main] Work area detected: %s%n", workArea);

        // マスコットの初期位置を設定（画面中央から2/3の範囲、画面外上部から開始）
        int minX = workArea.x + workArea.width / 2;
        int maxX = workArea.x + (int) (workArea.width * 2.0 / 3.0);
        int startX = minX + (int) (Math.random() * (maxX - minX));
        mascot.setAnchor(new java.awt.Point(startX, workArea.y - 256));

        contextVariables.put("mascot", mascot);
        contextVariables.put("time", 0L);

        EvaluationContext context = new EvaluationContext(contextVariables);
        EventDispatcher dispatcher = new EventDispatcher(context, mascot);

        // --- 2.5. 描画システムの初期化 (Dispatcherの後に生成) ---
        ImageCache imageCache = new ImageCache(Path.of("img"));
        MascotView mascotView = new MascotView(mascot, imageCache, dispatcher);

        // 読み込んだビヘイビアをディスパッチャに登録します。
        for (Behavior behavior : behaviors) {
            dispatcher.registerTrigger(behavior);
        }
        System.out.printf("[Main] Loaded and registered %d behaviors.%n", dispatcher.getRegisteredCount());

        // ウィンドウを可視化
        mascotView.setVisible(true);

        // --- 3️⃣ メインループ ---
        System.out.println("[Main] Starting main loop... (Press Ctrl+C to exit)");
        long tickCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            final int GRAVITY = 3; // 1フレームあたりの落下量

            // 1. イベントをディスパッチして、条件に合うビヘイビアを探します。
            // SYSTEM_TICKは、毎フレーム発生する基本的なイベントです。
            dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, tickCount, this));

            // 2. マスコットのtick()を呼び出し、現在のアクションを実行させます。
            mascot.tick();

            // --- 3. 物理演算と座標補正 ---
            // アクションが実行されておらず、かつ（前フレームで）空中にいる場合は重力を適用
            if (mascot.getCurrentAction() == null && !mascot.isGrounded() && !mascot.isBeingDragged()) {
                mascot.setY(mascot.getY() + GRAVITY);
            }

            // 接地判定と座標補正
            boolean wasGrounded = mascot.isGrounded();
            boolean isNowGrounded = mascot.getY() >= (workArea.y + workArea.height);
            mascot.setGrounded(isNowGrounded);

            if (isNowGrounded) {
                mascot.setY(workArea.y + workArea.height);
            }

            // 接地状態が変化した場合、イベントを発行する
            if (isNowGrounded != wasGrounded) {
                dispatcher.evaluateTriggers(new EventEnvelope<>(
                    EventType.MASCOT_STATE_CHANGED,
                    new StateChangeEvent("isGrounded", wasGrounded, isNowGrounded),
                    mascot));
            }

            // 壁衝突判定と座標補正
            int mascotWidth = mascotView.getMascotWidth();
            int mascotHeight = mascotView.getMascotHeight();
            int halfWidth = mascotWidth / 2;
            
            boolean isHittingLeftWall = (mascot.getX() - halfWidth) <= workArea.x;
            boolean isHittingRightWall = (mascot.getX() + halfWidth) >= (workArea.x + workArea.width);
            boolean isHittingCeiling = (mascot.getY() - mascotHeight) <= workArea.y;

            mascot.setHittingLeftWall(isHittingLeftWall);
            mascot.setHittingRightWall(isHittingRightWall);

            // ドラッグ中でなければ、画面内に押し戻す（壁として機能させる）
            if (!mascot.isBeingDragged()) {
                if (isHittingLeftWall) {
                    mascot.setX(workArea.x + halfWidth);
                }
                if (isHittingRightWall) {
                    mascot.setX(workArea.x + workArea.width - halfWidth);
                }
                if (isHittingCeiling) {
                    mascot.setY(workArea.y + mascotHeight);
                }
            }

            // コンテキスト変数を更新します。
            // これにより、ビヘイビアの条件が動的に変化します。
            context.getVariables().put("time", ++tickCount);
            
            // マウス座標の更新
            java.awt.Point mousePos = java.awt.MouseInfo.getPointerInfo().getLocation();
            context.getVariables().put("mouse.x", mousePos.x);
            context.getVariables().put("mouse.y", mousePos.y);

            // 4. 描画処理
            mascotView.update();

            // 5. 少し待機して、CPU使用率を抑えます。
            Thread.sleep(30); // 約33 FPS
        }

        System.out.println("=== Shimeji Neo Shutdown ===");
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
                    <Action Name="Stay" Type="Animate">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
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
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="100" />
                        </Animation>
                    </Action>
                    <Action Name="Jump" Type="Jump" VelocityY="20" VelocityX="5">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="1000" />
                        </Animation>
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
                    <Action Name="CeilingCrawl" Type="CeilingCrawl" Speed="2">
                        <Animation>
                            <Pose Image="shime1.png" ImageAnchor="64,128" Duration="200" />
                            <Pose Image="shime2.png" ImageAnchor="64,128" Duration="200" />
                        </Animation>
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
                        <Condition>!mascot.isGrounded &amp;&amp; !mascot.isHittingLeftWall &amp;&amp; !mascot.isHittingRightWall &amp;&amp; !(mascot.getY() - mascot.getMascotHeight() &lt;= 0)</Condition>
                        <ActionReference Name="FallSequence" />
                    </Behavior>
                    <Behavior Name="WallAction" Frequency="100">
                        <!-- currentAction == null を外すことで、落下アクション実行中でも壁にぶつかれば強制的に上書き遷移する -->
                        <Condition>mascot.isHittingLeftWall || mascot.isHittingRightWall</Condition>
                        <ActionReference Name="WallComplexSequence" />
                    </Behavior>
                    <Behavior Name="CeilingAction" Frequency="100">
                        <Condition>mascot.isHittingCeiling</Condition>
                        <ActionReference Name="CeilingCrawl" />
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

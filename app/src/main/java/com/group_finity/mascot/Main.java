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

        contextVariables.put("mascot.x", mascot.getX());
        contextVariables.put("mascot.y", mascot.getY());
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

            // --- 状態更新 ---
            // アクションが実行されておらず、かつ（前フレームで）空中にいる場合は重力を適用
            if (mascot.getCurrentAction() == null && !mascot.isGrounded() && !mascot.isBeingDragged()) {
                mascot.setY(mascot.getY() + GRAVITY);
            }

            // 接地判定と座標補正
            boolean wasGrounded = mascot.isGrounded();
            int mascotHeight = mascotView.getMascotHeight();
            boolean isNowGrounded = (mascot.getY() + mascotHeight) >= (workArea.y + workArea.height);
            mascot.setGrounded(isNowGrounded);

            if (isNowGrounded) {
                mascot.setY(workArea.y + workArea.height - mascotHeight);
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
            boolean isHittingLeftWall = mascot.getX() <= workArea.x;
            boolean isHittingRightWall = (mascot.getX() + mascotWidth) >= (workArea.x + workArea.width);
            mascot.setHittingLeftWall(isHittingLeftWall);
            mascot.setHittingRightWall(isHittingRightWall);

            if (isHittingLeftWall) {
                mascot.setX(workArea.x);
            }
            if (isHittingRightWall) {
                mascot.setX(workArea.x + workArea.width - mascotWidth);
            }

            // コンテキスト変数を更新します。
            // これにより、ビヘイビアの条件が動的に変化します。
            context.getVariables().put("time", ++tickCount);
            context.getVariables().put("mascot.x", mascot.getX());
            context.getVariables().put("mascot.y", mascot.getY());
            context.getVariables().put("mascot.lookRight", mascot.isLookRight());
            context.getVariables().put("mascot.isGrounded", isNowGrounded);
            context.getVariables().put("mascot.isHittingLeftWall", isHittingLeftWall);
            context.getVariables().put("mascot.isHittingRightWall", isHittingRightWall);
            context.getVariables().put("mascot.isBeingDragged", mascot.isBeingDragged());
            context.getVariables().put("mascot.currentAction", mascot.getCurrentAction());

            // 1. イベントをディスパッチして、条件に合うビヘイビアを探します。
            // SYSTEM_TICKは、毎フレーム発生する基本的なイベントです。
            dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, tickCount, this));

            // 2. マスコットのtick()を呼び出し、現在のアクションを実行させます。
            mascot.tick();

            // 3. 描画処理
            mascotView.update();

            // 4. 少し待機して、CPU使用率を抑えます。
            Thread.sleep(30); // 約33 FPS
        }

        System.out.println("=== Shimeji Neo Shutdown ===");
    }
}

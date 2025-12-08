package com.group_finity.mascot.trigger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.log.EventLog;
import com.group_finity.mascot.log.EventLogRecord;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.ExprTrigger;
import com.group_finity.mascot.trigger.IntervalTrigger;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.event.StateChangeEvent;

/**
 * ShimejiApp (EventDispatcher 統合版)
 * - アプリケーションのメインループで EventDispatcher を利用するサンプル実装。
 * - 時間経過や状態変化のイベントを擬似的に生成し、ディスパッチャに投入します。
 * - 安全なシャットダウンフックを登録します。
 */
public final class ShimejiApp {

    private EventDispatcher dispatcher;
    private EvaluationContext context;
    private Mascot mascot;

    public static void main(String[] args) {
        // ==========================
        // 1️⃣ 初期化フェーズ
        // ==========================
        System.out.println("=== Shimeji Neo - D-4d Aggregator Test Start ===");
        // EventLog.initDefault(Path.of("logs")); // 必要に応じて有効化
        // EventLog.record("ShimejiApp", "Startup", true, 0L, EventLogRecord.Level.INFO, Map.of());

        ShimejiApp app = new ShimejiApp();

        // JVM終了時にクリーンアップ処理を呼び出すシャットダウンフックを登録
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Shutdown hook triggered.");
            app.shutdown();
        }));

        try {
            app.initialize();
            app.runMainLoop();
        } catch (Exception e) {
            e.printStackTrace();
            // EventLog.record("ShimejiApp", "FatalError", false, 0L, EventLogRecord.Level.ERROR, Map.of("error", e.getMessage()));
        } finally {
            // ==========================
            // 2️⃣ シャットダウン
            // ==========================
            app.shutdown();
            // EventLog.record("ShimejiApp", "Shutdown", true, 0L, EventLogRecord.Level.INFO, Map.of());
            // EventLog.shutdown(); // 必要に応じて有効化
            System.out.println("=== Shimeji Neo - D-4d Aggregator Test End ===");
        }
    }

    /** Dispatcherとテスト用Triggerの初期化 */
    private void initialize() {
        // マスコットインスタンス生成
        this.mascot = new Mascot();

        // 複数のトリガーで共有される変数を保持するマップ
        Map<String, Object> sharedVariables = new HashMap<>();
        sharedVariables.put("time", 0L);
        sharedVariables.put("mascot.state", "idle");
        sharedVariables.put("window.active", true);

        // コンテキスト生成
        this.context = new EvaluationContext(sharedVariables);

        // Dispatcher生成 (Mascotインスタンスを渡す)
        this.dispatcher = new EventDispatcher(context, mascot);

        // --- 様々なトリガーを登録 ---
        // 1. 時間ベースの式トリガー: 5秒経過したら発火 -> "Sit"アクション
        this.dispatcher.registerTrigger(new ExprTrigger("time >= 5"), "Sit");

        // 2. 状態ベースの式トリガー: マスコットの状態が "active" になったら発火 -> "Jump" と "LookAtMouse" を順番に実行
        this.dispatcher.registerTrigger(new ExprTrigger("mascot.state == 'active'"), "Jump", "LookAtMouse");

        // 3. 複合条件の式トリガー: アクティブウィンドウで、かつマスコットの状態が "idle" -> "Stare"アクション
        this.dispatcher.registerTrigger(new ExprTrigger("window.active && mascot.state == 'idle'"), "Stare");

        // 4. 時間間隔トリガー: 1秒ごとに発火 -> "Blink"アクション
        this.dispatcher.registerTrigger(new IntervalTrigger(1000), "Blink");

        // EventDispatcherのワーカースレッドを起動
        this.dispatcher.start();
    }

    /** メインループ: 擬似的にイベントを発生させ続ける */
    private void runMainLoop() throws InterruptedException {
        System.out.println("[Main] Starting main loop... (Press Ctrl+C to exit)");
        long lastTick = System.currentTimeMillis();
        long time = 0;
        Random random = new Random();

        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();
            long deltaTime = now - lastTick;
            lastTick = now;

            // 1. 時間経過イベントをディスパッチ
            context.getVariables().put("time", ++time);
            dispatcher.dispatchEvent(new EventEnvelope<>(EventType.SYSTEM_TICK, deltaTime, this));

            // 2. 3秒に1回くらいの確率で、マスコットの状態をランダムに変更するイベントをディスパッチ
            if (random.nextInt(10) == 0) { // 約3秒に1回 (300ms * 10)
                String oldState = (String) context.getVariables().get("mascot.state");
                String newState = oldState.equals("idle") ? "active" : "idle";
                context.getVariables().put("mascot.state", newState);
                dispatcher.dispatchEvent(new EventEnvelope<>(EventType.MASCOT_STATE_CHANGED, new StateChangeEvent("mascot.state", oldState, newState), this));
                System.out.printf("[Main] State changed to: %s%n", newState);
            }

            sleep(300); // 300ms待機
        }
    }

    private void shutdown() {
        // EventDispatcher を安全にシャットダウン
        if (dispatcher != null) dispatcher.shutdown();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

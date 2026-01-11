package com.group_finity.mascot;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.Configuration;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.event.StateChangeEvent;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

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
        // 1. マスコットと評価コンテキストを初期化
        this.mascot = new Mascot();
        Map<String, Object> sharedVariables = new HashMap<>();
        sharedVariables.put("time", 0L);
        sharedVariables.put("mascot.state", "idle");
        sharedVariables.put("window.active", true);
        this.context = new EvaluationContext(sharedVariables);

        // 2. EventDispatcherを生成
        this.dispatcher = new EventDispatcher(context, mascot);

        // 3. 設定ファイルからアクションとビヘイビアを読み込む
        // 注意: 現状のActionBuilderとBehaviorBuilderはプレースホルダです。
        // 実際の動作にはXMLをパースする実装が必要です。
        Configuration config = new Configuration(Path.of("conf/actions.xml"), Path.of("conf/behaviors.xml"));

        // 5. 読み込んだビヘイビアをEventDispatcherに登録
        List<Behavior> behaviors = config.getBehaviors() != null ? config.getBehaviors() : Collections.emptyList();
        for (Behavior behavior : behaviors) {
            // アクションがnullでないことを確認し、登録する
            if (behavior.getAction() != null) {
                // EventDispatcherはTrigger (Behavior) を直接受け取る
                dispatcher.registerTrigger(behavior);
            } else {
                // BehaviorBuilderの段階でこれは起こらないはずだが、念のため
                System.err.println("Warning: Behavior without an action found and was not registered: " + behavior);
            }
        }

        if (behaviors.isEmpty()) {
            System.out.println("[ShimejiApp] No behaviors loaded from configuration. The mascot will be idle.");
        } else {
            System.out.printf("[ShimejiApp] Loaded and registered %d behaviors.%n", behaviors.size());
        }

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
            dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, deltaTime, this));

            // 2. 3秒に1回くらいの確率で、マスコットの状態をランダムに変更するイベントをディスパッチ
            if (random.nextInt(10) == 0) { // 約3秒に1回 (300ms * 10)
                String oldState = (String) context.getVariables().get("mascot.state");
                String newState = oldState.equals("idle") ? "active" : "idle";
                context.getVariables().put("mascot.state", newState);
                dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.MASCOT_STATE_CHANGED, new StateChangeEvent("mascot.state", oldState, newState), this));
                System.out.printf("[Main] State changed to: %s%n", newState);
            }

            // 3. マスコットのアクションを1フレーム進める
            mascot.tick();

            sleep(300); // 300ms待機
        }
    }

    private void shutdown() {
        // EventDispatcherはスレッドを管理しなくなったため、シャットダウン処理は不要
        System.out.println("[ShimejiApp] Shutdown process complete.");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

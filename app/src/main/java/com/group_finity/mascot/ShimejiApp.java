package com.group_finity.mascot;

import java.nio.file.Path;
import java.util.Map;

import com.group_finity.mascot.log.EventLog;
import com.group_finity.mascot.log.EventLogRecord;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.EventQueue;
import com.group_finity.mascot.trigger.Trigger;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * ShimejiApp (D-4d 統合版)
 * - EventLog Aggregator を初期化
 * - Dispatcher / WorkerPool 経路の統合テスト
 * - 安全なシャットダウン処理を追加
 */
public final class ShimejiApp {

    private EventDispatcher dispatcher;
    private EventQueue eventQueue;
    private EvaluationContext context;

    public static void main(String[] args) {
        // ==========================
        // 1️⃣ 初期化フェーズ
        // ==========================
        System.out.println("=== Shimeji Neo - D-4d Aggregator Test Start ===");
        EventLog.initDefault(Path.of("logs"));
        EventLog.record("ShimejiApp", "Startup", true, 0L, EventLogRecord.Level.INFO, Map.of());

        ShimejiApp app = new ShimejiApp();
        try {
            app.initialize();
            app.runTestLoop();
        } catch (Exception e) {
            e.printStackTrace();
            EventLog.record("ShimejiApp", "FatalError", false, 0L,
                    EventLogRecord.Level.ERROR, Map.of("error", e.getMessage()));
        } finally {
            // ==========================
            // 2️⃣ シャットダウン
            // ==========================
            app.shutdown();
            EventLog.record("ShimejiApp", "Shutdown", true, 0L,
                    EventLogRecord.Level.INFO, Map.of());
            EventLog.shutdown();
            System.out.println("=== Shimeji Neo - D-4d Aggregator Test End ===");
        }
    }

    /** Dispatcherとテスト用Triggerの初期化 */
    private void initialize() {
        // コンテキスト・イベントキュー生成
        this.context = new EvaluationContext(Map.of(
                "time", 0,
                "state", "idle"
        ));
        this.eventQueue = new EventQueue();

        // Dispatcher生成
        this.dispatcher = new EventDispatcher(context, eventQueue);

        // ダミートリガ登録（任意の軽負荷テスト用）
        this.dispatcher.registerTrigger(new Trigger() {
            @Override
            public boolean check(EvaluationContext ctx) {
                Object timeObj = ctx.getVariablesSnapshot().get("time");
                if (timeObj instanceof Number num) {
                    long time = num.longValue();
                    return time % 2 == 0; // 偶数時のみ発火
                }
                return false;
            }

            @Override
            public void execute(EvaluationContext ctx) {
                System.out.println("[Trigger] Executed with ctx=" + ctx.getVariablesSnapshot());
            }

            @Override
            public String toString() {
                return "EvenTimeTrigger";
            }
        });
    }

    /** テストループ: 擬似的にイベントを発生させる */
    private void runTestLoop() {
        for (int t = 0; t < 5; t++) {
            context.getVariablesSnapshot().put("time", t);
            System.out.printf("[Main] Step %d → Context: %s%n", t, context.getVariablesSnapshot());
            dispatcher.pollAndDispatch();
            sleep(200);
        }
    }

    private void shutdown() {
        if (dispatcher != null) {
            dispatcher.shutdownWorkers();
            dispatcher.awaitWorkers(1000);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}

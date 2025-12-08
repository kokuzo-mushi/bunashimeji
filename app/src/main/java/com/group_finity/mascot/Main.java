package com.group_finity.mascot;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import com.group_finity.mascot.trigger.CompositeTrigger;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.EventLog;
import com.group_finity.mascot.trigger.EventQueue;
import com.group_finity.mascot.trigger.TriggerCondition;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * Shimeji Neo: EventDispatcher + EventQueue 統合動作テスト
 * フェーズ D-2 検証用メインクラス
 */
public class Main {

    public static void main(String[] args) {

        System.out.println("=== Shimeji Neo - EventDispatcher + EventQueue Test Start ===");

        // --- 1️⃣ コンテキスト準備 ---
        Map<String, Object> vars = new HashMap<>();
        vars.put("time", 500);
        vars.put("state", "idle");

        EvaluationContext ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        // --- 2️⃣ イベントキューとディスパッチャを初期化 ---
        Mascot mascot = new Mascot(); // Mascotインスタンスを生成
        EventDispatcher dispatcher = new EventDispatcher(ctx, mascot);

        // --- 3️⃣ トリガー定義 ---
        TriggerCondition cond1 = new TriggerCondition("time > 1000", vars);
        TriggerCondition cond2 = new TriggerCondition("state === \"falling\"", vars);

        CompositeTrigger trigger1 = new CompositeTrigger(List.of(cond1, cond2), CompositeTrigger.Mode.ALL);
        CompositeTrigger trigger2 = new CompositeTrigger(List.of(cond1), CompositeTrigger.Mode.ANY);

        // --- 4️⃣ 登録 ---
        dispatcher.registerTrigger(trigger1, "Action1", "Action2"); // 複数のアクションを登録
        dispatcher.registerTrigger(trigger2, "Action3");

        System.out.println("[Main] Registered triggers: " + dispatcher.getRegisteredTriggerCount());

        // --- 5️⃣ コンテキスト変化シミュレーション ---
        int[] timeSteps = {500, 900, 1200, 1500};
        String[] states = {"idle", "active", "falling", "falling"};

     // ループ内の更新箇所をこうする
        for (int i = 0; i < timeSteps.length; i++) {
            int t = timeSteps[i];
            String st = states[i];

            // 表示用に vars を更新（任意）
            vars.put("time", t);
            vars.put("state", st);

            // 評価に使われるのは ctx なので、必ず ctx にも反映する！
            ctx.setValue("time", t);
            ctx.setValue("state", st);

            System.out.println("\n[Main] Step " + (i + 1) + " → Context: {time=" + t + ", state=" + st + "}");

            // EventDispatcher の run() メソッドを直接呼び出す代わりに、
            // EventDispatcher が EventQueue からイベントをポーリングして処理するロジックをシミュレート
            // ここでは EventDispatcher が EventQueue を持っていることを前提とする
            // EventDispatcher の内部で EventQueue.take() または EventQueue.poll() が呼ばれる想定
            // Main クラスでは EventDispatcher にイベントをディスパッチする役割に徹する (EventDispatcher は内部でキューを管理)
            // EventDispatcher は EventQueue を持っていないため、直接イベントをディスパッチする
            // ここでは EventDispatcher の run() メソッドを直接呼び出す代わりに、イベントを生成してディスパッチする
            dispatcher.dispatchEvent(new EventEnvelope<>(EventType.SYSTEM_TICK, (long)t, "Main"));

            // EventDispatcher は非同期でイベントを処理するため、ここではキューのポーリングは行わない
            // イベント処理の結果は EventDispatcher の handleTriggerFired メソッドでログ出力される

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n=== Shimeji Neo - EventDispatcher Test Complete ===");
    }
}

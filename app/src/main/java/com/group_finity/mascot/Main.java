package com.group_finity.mascot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.GenericBehavior;
import com.group_finity.mascot.trigger.CompositeTrigger;
import com.group_finity.mascot.trigger.EventDispatcher;
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

        // ShimejiAppの実装に合わせ、マップを直接共有するコンストラクタを使用
        EvaluationContext ctx = new EvaluationContext(vars);

        // --- 2️⃣ イベントキューとディスパッチャを初期化 ---
        Mascot mascot = new Mascot(); // Mascotインスタンスを生成
        EventDispatcher dispatcher = new EventDispatcher(ctx, mascot);

        // --- 3️⃣ トリガー定義 ---
        TriggerCondition cond1 = new TriggerCondition("time > 1000", vars);
        TriggerCondition cond2 = new TriggerCondition("state === \"falling\"", vars);

        CompositeTrigger trigger1 = new CompositeTrigger(List.of(cond1, cond2), CompositeTrigger.Mode.ALL);
        CompositeTrigger trigger2 = new CompositeTrigger(List.of(cond1), CompositeTrigger.Mode.ANY);

        // --- 4️⃣ 登録 ---
        // テスト用のアクションを匿名クラスで定義
        Action action1 = new Action() {
            private boolean hasNext = true;
            @Override public boolean hasNext() { return hasNext; }
            @Override public void execute(Mascot m) {
                System.out.println("[Action] Executing Action1 (Triggered by: " + trigger1 + ")");
                hasNext = false; // 1回実行したら終了
            }
        };
        Action action3 = new Action() {
            private boolean hasNext = true;
            @Override public boolean hasNext() { return hasNext; }
            @Override public void execute(Mascot m) {
                System.out.println("[Action] Executing Action3 (Triggered by: " + trigger2 + ")");
                hasNext = false; // 1回実行したら終了
            }
        };

        // TriggerとActionをBehaviorでラップして登録する
        dispatcher.registerTrigger(new GenericBehavior(trigger1, action1));
        dispatcher.registerTrigger(new GenericBehavior(trigger2, action3));

        // メソッド名を修正
        System.out.println("[Main] Registered triggers: " + dispatcher.getRegisteredCount());
        // --- 5️⃣ コンテキスト変化シミュレーション ---
        int[] timeSteps = {500, 900, 1200, 1500};
        String[] states = {"idle", "active", "falling", "falling"};

     // ループ内の更新箇所をこうする
        for (int i = 0; i < timeSteps.length; i++) {
            int t = timeSteps[i];
            String st = states[i];

            // コンテキストの変数を更新 (ShimejiAppの実装に合わせる)
            ctx.getVariables().put("time", t);
            ctx.getVariables().put("state", st);

            System.out.println("\n[Main] Step " + (i + 1) + " → Context: {time=" + t + ", state=" + st + "}");

            // メソッド名を修正し、イベントをディスパッチしてトリガーを評価
            dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, (long)t, "Main"));

            // マスコットのtickを呼び出し、アクションを実行させる
            mascot.tick();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n=== Shimeji Neo - EventDispatcher Test Complete ===");
    }
}

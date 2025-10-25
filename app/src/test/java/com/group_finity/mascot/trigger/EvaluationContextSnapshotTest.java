package com.group_finity.mascot.trigger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

public class EvaluationContextSnapshotTest {

    @Test
    void snapshotPreventsRaceWithConcurrentUpdates() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        vars.put("time", 0);
        vars.put("state", "idle");
        EvaluationContext base = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        EventQueue logQ = new EventQueue();
        EventDispatcher dispatcher = new EventDispatcher(base, logQ, 2);

        // 条件: time > 100, state === "active"
        TriggerCondition c1 = new TriggerCondition("time > 100", vars);
        TriggerCondition c2 = new TriggerCondition("state === \"active\"", vars);
        CompositeTrigger trig = new CompositeTrigger(List.of(c1, c2), CompositeTrigger.Mode.ALL);
        dispatcher.registerTrigger(trig);

        // 1) まずはヒットしない状態
        base.setValue("time", 50);
        base.setValue("state", "idle");
        dispatcher.pollAndDispatch();

        // 少し待機（失敗ログが記録されることを確認）
        Thread.sleep(100);

        // ★ 修正: 失敗ログを消費
        EventLog firstLog = logQ.poll();
        assertNotNull(firstLog, "First log should be present");
        assertFalse(firstLog.isSuccess(), "First log should be failure");

        // 2) 条件を満たす状態に更新
        base.setValue("time", 200);
        base.setValue("state", "active");
        dispatcher.pollAndDispatch();

        // ワーカーの処理完了を待つ
        Thread.sleep(300);

        // ★ 修正: 成功ログを取得
        EventLog successLog = logQ.poll();
        assertNotNull(successLog, "Success log should be present");
        assertTrue(successLog.isSuccess(), "Log should indicate success");

        // コンテキストスナップショットの検証
        Map<String, Object> snapshot = successLog.getContextSnapshot();
        assertNotNull(snapshot, "Context snapshot should not be null");
        assertEquals(200, snapshot.get("time"), "Snapshot should have updated time");
        assertEquals("active", snapshot.get("state"), "Snapshot should have updated state");

        dispatcher.shutdownWorkers();
        dispatcher.awaitWorkers(1000);
    }
}
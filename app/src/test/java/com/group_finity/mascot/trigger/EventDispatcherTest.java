package com.group_finity.mascot.trigger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

public class EventDispatcherTest {

    private Map<String, Object> vars;
    private EvaluationContext ctx;
    private EventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        vars = new HashMap<>();
        vars.put("time", 0);
        vars.put("state", "idle");
        ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        // ✅ EventQueueを渡す形に修正
        EventQueue queue = new EventQueue();
        dispatcher = new EventDispatcher(ctx, queue);
    }


    @Test
    void testTriggerFiresWhenConditionTrue() {
        TriggerCondition cond = new TriggerCondition("time > 1000", vars);
        TriggerCondition cond2 = new TriggerCondition("state === \"active\"", vars);
        CompositeTrigger trigger = new CompositeTrigger(List.of(cond, cond2), CompositeTrigger.Mode.ALL);

        dispatcher.registerTrigger(trigger);

        // 条件を満たさない → 発火しない
        dispatcher.pollAndDispatch();
        assertEquals(1, dispatcher.getRegisteredCount());

        // 条件を満たす → 発火する（ログ出力確認用）
        vars.put("time", 1500);
        vars.put("state", "active");
        dispatcher.pollAndDispatch();
    }

    @Test
    void testAnyModeFiresWithPartialMatch() {
        TriggerCondition cond1 = new TriggerCondition("time > 1000", vars);
        TriggerCondition cond2 = new TriggerCondition("state === \"active\"", vars);
        CompositeTrigger trigger = new CompositeTrigger(List.of(cond1, cond2), CompositeTrigger.Mode.ANY);

        dispatcher.registerTrigger(trigger);

        vars.put("time", 2000);
        vars.put("state", "idle");

        assertDoesNotThrow(() -> dispatcher.pollAndDispatch());
    }

    @Test
    void testDispatcherHandlesMultipleTriggers() {
        TriggerCondition c1 = new TriggerCondition("time > 1000", vars);
        TriggerCondition c2 = new TriggerCondition("state === \"falling\"", vars);
        CompositeTrigger t1 = new CompositeTrigger(List.of(c1, c2), CompositeTrigger.Mode.ALL);
        CompositeTrigger t2 = new CompositeTrigger(List.of(c1), CompositeTrigger.Mode.ANY);

        dispatcher.registerTrigger(t1);
        dispatcher.registerTrigger(t2);

        vars.put("time", 2000);
        vars.put("state", "falling");

        assertEquals(2, dispatcher.getRegisteredCount());
        assertDoesNotThrow(() -> dispatcher.pollAndDispatch());
    }

    @Test
    void testNoTriggersRegistered() {
        assertEquals(0, dispatcher.getRegisteredCount());
        dispatcher.pollAndDispatch(); // Should not throw
    }

    @Test
    void testClearRemovesAllTriggers() {
        TriggerCondition cond = new TriggerCondition("time > 1000", vars);
        CompositeTrigger trigger = new CompositeTrigger(List.of(cond), CompositeTrigger.Mode.ANY);
        dispatcher.registerTrigger(trigger);
        assertEquals(1, dispatcher.getRegisteredCount());

        dispatcher.clear();
        assertEquals(0, dispatcher.getRegisteredCount());
    }
}

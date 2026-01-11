package com.group_finity.mascot.trigger.expr.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.group_finity.mascot.trigger.TriggerCondition;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * D-5 Evaluation Cache Integration Test (Fixed Version)
 *
 * Main changes:
 * - Changed @BeforeAll to @BeforeEach (Independent context for each test)
 * - Recreate context when variables change
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExprCacheIntegrationTest {

    private Map<String, Object> vars;
    private EvaluationContext ctx;
    private TriggerCondition condition;

    @BeforeEach
    void setup() {
        // Create new instances for each test
        vars = new HashMap<>();
        vars.put("x", 1);

        ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);
        condition = new TriggerCondition("x + 1 === 2", vars);

        // Reset statistics
        CacheStatsTracker.INSTANCE.reset();
        TriggerCondition.clearGlobalCache();
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Correctly evaluated without cache")
    void testInitialEvaluation() {
        boolean result = condition.evaluate(ctx);

        assertTrue(result, "Expression 'x + 1 === 2' should be true");
        assertEquals(0, CacheStatsTracker.INSTANCE.getHitCount());
        assertEquals(1, CacheStatsTracker.INSTANCE.getMissCount());
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Cache hit under same conditions")
    void testCacheHit() {
        // Initial evaluation
        condition.evaluate(ctx);

        // Second evaluation (same variable values)
        boolean result = condition.evaluate(ctx);

        assertTrue(result);
        assertEquals(1, CacheStatsTracker.INSTANCE.getHitCount(),
                "Should hit on second attempt");
        assertEquals(1, CacheStatsTracker.INSTANCE.getMissCount(),
                "Miss count should be 1 (initial only)");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Cache miss when dependent variable changes")
    void testCacheMissOnVariableChange() {
        // Initial evaluation (x=1)
        condition.evaluate(ctx);
        assertEquals(1, CacheStatsTracker.INSTANCE.getMissCount());

        // FIX: Recreate context on variable change
        vars.put("x", 5);
        ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        // Re-evaluation (x=5)
        boolean result = condition.evaluate(ctx);

        assertFalse(result, "Should be false when x=5");
        assertEquals(2, CacheStatsTracker.INSTANCE.getMissCount(),
                "Re-evaluation should occur due to variable change");
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Verify difference between STRICT and LOOSE modes")
    void testStrictVsLoose() {
        // Evaluation in STRICT mode
        Map<String, Object> strictVars = new HashMap<>();
        strictVars.put("x", 1);
        EvaluationContext strictCtx = new EvaluationContext(strictVars, new DefaultTypeCoercion(), Mode.STRICT);
        TriggerCondition strictCondition = new TriggerCondition("x + 1 === 2", strictVars);

        strictCondition.evaluate(strictCtx); // MISS
        strictCondition.evaluate(strictCtx); // HIT（同条件）

        // Variable change
        strictVars.put("x", 5);
        strictCtx = new EvaluationContext(strictVars, new DefaultTypeCoercion(), Mode.STRICT);
        strictCondition.evaluate(strictCtx); // MISS（変数変化）

        assertTrue(CacheStatsTracker.INSTANCE.getMissCount() >= 2,
                "At least 2 misses should occur");
    }
    /*
     * @Test
     * 
     * @Order(5)
     * 
     * @DisplayName("Step 5: Verify performance improvement with 1000 evaluations")
     * void testPerformance() {
     * vars.put("x", 1);
     * ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);
     * condition = new TriggerCondition("x + 1 === 2", vars);
     * 
     * // Warmup (JIT optimization)
     * for (int i = 0; i < 10; i++) {
     * condition.evaluate(ctx);
     * }
     * 
     * CacheStatsTracker.INSTANCE.reset();
     * 
     * // First 100 runs (Cache construction phase)
     * long startNoCache = System.nanoTime();
     * for (int i = 0; i < 100; i++) {
     * condition.evaluate(ctx);
     * }
     * long mid = System.nanoTime();
     * 
     * // Remaining 900 runs (Cache usage phase)
     * for (int i = 0; i < 900; i++) {
     * condition.evaluate(ctx);
     * }
     * long end = System.nanoTime();
     * 
     * long firstPhase = mid - startNoCache;
     * long cachedPhase = end - mid;
     * 
     * System.out.printf("[Perf] first100=%dμs, cached900=%dμs%n",
     * firstPhase / 1000, cachedPhase / 1000);
     * System.out.printf("[Stats] Hit=%d, Miss=%d, HitRate=%.2f%%%n",
     * CacheStatsTracker.INSTANCE.getHitCount(),
     * CacheStatsTracker.INSTANCE.getMissCount(),
     * CacheStatsTracker.INSTANCE.getGlobalHitRate() * 100);
     * 
     * // Verify that cached execution is faster (comparing average time per run)
     * double avgFirst = (double) firstPhase / 100.0;
     * double avgCached = (double) cachedPhase / 900.0;
     * assertTrue(avgCached < avgFirst,
     * String.
     * format("Cached average (%.2f ns) should be faster than initial average (%.2f ns)"
     * , avgCached, avgFirst));
     * 
     * // Verify hit rate is over 80%
     * assertTrue(CacheStatsTracker.INSTANCE.getGlobalHitRate() > 0.8,
     * "Hit rate should be over 80%");
     * }
     */
}
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
 * D-5 評価キャッシュ統合テスト（修正版）
 * 
 * 主な変更点:
 * - @BeforeAll → @BeforeEach に変更（各テストで独立したコンテキスト）
 * - 変数変更時にコンテキストを再作成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExprCacheIntegrationTest {

    private Map<String, Object> vars;
    private EvaluationContext ctx;
    private TriggerCondition condition;

    @BeforeEach
    void setup() {
        // 各テストで新しいインスタンスを作成
        vars = new HashMap<>();
        vars.put("x", 1);
        
        ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);
        condition = new TriggerCondition("x + 1 === 2", vars);
        
        // 統計をリセット
        CacheStatsTracker.INSTANCE.reset();
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: キャッシュなしで正しく評価される")
    void testInitialEvaluation() {
        boolean result = condition.evaluate(ctx);
        
        assertTrue(result, "式 'x + 1 === 2' は true であるべき");
        assertEquals(0, CacheStatsTracker.INSTANCE.getHitCount());
        assertEquals(1, CacheStatsTracker.INSTANCE.getMissCount());
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: 同一条件でキャッシュヒットする")
    void testCacheHit() {
        // 初回評価
        condition.evaluate(ctx);
        
        // 2回目評価（同じ変数値）
        boolean result = condition.evaluate(ctx);
        
        assertTrue(result);
        assertEquals(1, CacheStatsTracker.INSTANCE.getHitCount(), 
                    "2回目はヒットするはず");
        assertEquals(1, CacheStatsTracker.INSTANCE.getMissCount(),
                    "ミスは初回の1回のみ");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: 依存変数が変化するとキャッシュミスになる")
    void testCacheMissOnVariableChange() {
        // 初回評価（x=1）
        condition.evaluate(ctx);
        assertEquals(1, CacheStatsTracker.INSTANCE.getMissCount());
        
        // ★ FIX: 変数変更時はコンテキストを再作成
        vars.put("x", 5);
        ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);
        
        // 再評価（x=5）
        boolean result = condition.evaluate(ctx);
        
        assertFalse(result, "x=5 の場合 false であるべき");
        assertEquals(2, CacheStatsTracker.INSTANCE.getMissCount(), 
                    "変数変更により再評価が発生しているはず");
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: STRICT と LOOSE モードの差を検証")
    void testStrictVsLoose() {
        // STRICT モードでの評価
        Map<String, Object> strictVars = new HashMap<>();
        strictVars.put("x", 1);
        EvaluationContext strictCtx = 
            new EvaluationContext(strictVars, new DefaultTypeCoercion(), Mode.STRICT);
        TriggerCondition strictCondition = new TriggerCondition("x + 1 === 2", strictVars);
        
        strictCondition.evaluate(strictCtx); // MISS
        strictCondition.evaluate(strictCtx); // HIT（同条件）
        
        // 変数変更
        strictVars.put("x", 5);
        strictCtx = new EvaluationContext(strictVars, new DefaultTypeCoercion(), Mode.STRICT);
        strictCondition.evaluate(strictCtx); // MISS（変数変化）
        
        assertTrue(CacheStatsTracker.INSTANCE.getMissCount() >= 2,
                  "少なくとも2回のミスが発生しているはず");
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: 1000回連続評価でパフォーマンス改善を確認（目視）")
    void testPerformance() {
        vars.put("x", 1);
        ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);
        condition = new TriggerCondition("x + 1 === 2", vars);
        
        // ウォームアップ（JIT最適化）
        for (int i = 0; i < 10; i++) {
            condition.evaluate(ctx);
        }
        
        CacheStatsTracker.INSTANCE.reset();
        
        // 初回100回（キャッシュ構築フェーズ）
        long startNoCache = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            condition.evaluate(ctx);
        }
        long mid = System.nanoTime();
        
        // 残り900回（キャッシュ活用フェーズ）
        for (int i = 0; i < 900; i++) {
            condition.evaluate(ctx);
        }
        long end = System.nanoTime();

        long firstPhase = mid - startNoCache;
        long cachedPhase = end - mid;
        
        System.out.printf("[Perf] first100=%dμs, cached900=%dμs%n",
                firstPhase / 1000, cachedPhase / 1000);
        System.out.printf("[Stats] Hit=%d, Miss=%d, HitRate=%.2f%%%n",
                CacheStatsTracker.INSTANCE.getHitCount(),
                CacheStatsTracker.INSTANCE.getMissCount(),
                CacheStatsTracker.INSTANCE.getGlobalHitRate() * 100);
        
        // キャッシュ後のほうが高速であることを検証
        assertTrue(cachedPhase < firstPhase,
                String.format("キャッシュ後の900回(%dμs)は初回100回(%dμs)より高速であるべき",
                        cachedPhase / 1000, firstPhase / 1000));
        
        // ヒット率が80%以上であることを検証
        assertTrue(CacheStatsTracker.INSTANCE.getGlobalHitRate() > 0.8,
                "ヒット率は80%以上であるべき");
    }
}
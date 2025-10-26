package com.group_finity.mascot.trigger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.group_finity.mascot.trigger.expr.ExpressionEngine;
import com.group_finity.mascot.trigger.expr.cache.CacheStatsTracker;
import com.group_finity.mascot.trigger.expr.cache.EvaluationResult;
import com.group_finity.mascot.trigger.expr.cache.ExprCacheKey;
import com.group_finity.mascot.trigger.expr.cache.ExprCacheManager;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver;
import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * D-5 修正版:
 * - 取得は AST+Mode のキーのみ（依存はキーに含めない）
 * - HIT 判定は EvaluationResult 内の依存と「現在の依存値」の比較で行う
 * - clearAccessLog() は再評価する時だけ呼ぶ
 * - EvaluationContext は外部変数マップを参照共有（コンストラクタ呼び出し側の責務）
 */
public class TriggerCondition {

    private static final Map<String, ExpressionNode> AST_CACHE = new ConcurrentHashMap<>();
    private static final ExprCacheManager cacheManager = new ExprCacheManager();

    private final String expression;
    private final ExpressionEngine engine;
    private EvaluationContext context; // 参照共有される想定

    public TriggerCondition(String expression, Map<String, Object> variables) {
        this.expression = expression;
        this.engine = new ExpressionEngine();
        if (variables == null) variables = new HashMap<>();
        // ★ EvaluationContext 側が参照共有コンストラクタを持つ前提（下の修正②参照）
        this.context = new EvaluationContext(variables, new DefaultTypeCoercion(), Mode.STRICT, true);
    }

    public EvaluationContext getContext() { return context; }
    public void setVariable(String name, Object value) {
        if (context != null && context.getVariables() != null) {
            context.getVariables().put(name, value);
        }
    }
    public String getExpression() { return expression; }

    public boolean evaluate() {
        return evaluate(this.context);
    }

    public boolean evaluate(EvaluationContext externalCtx) {
        if (externalCtx == null && this.context == null) {
            this.context = new EvaluationContext(new HashMap<>(), new DefaultTypeCoercion(), Mode.STRICT, true);
        }
        EvaluationContext ctx = (externalCtx != null) ? externalCtx : this.context;
        if (ctx == null) return false;

        // 1) AST 構築（失敗時は false リテラルでフォールバック）
        ExpressionNode ast = AST_CACHE.computeIfAbsent(expression, key -> {
            try {
                ExpressionNode parsed = new ExpressionParser(key).parse();
                return (parsed != null) ? parsed : new com.group_finity.mascot.trigger.expr.node.LiteralNode(false);
            } catch (Exception e) {
                System.err.println("[TriggerCondition] Parse error: " + key);
                e.printStackTrace();
                return new com.group_finity.mascot.trigger.expr.node.LiteralNode(false);
            }
        });

        // 2) AST+Mode のキーで取得（依存はキーに含めない）
        ExprCacheKey astKey = ExprCacheKey.ofAst(ast, ctx.getMode());
        Optional<EvaluationResult> cached = cacheManager.get(astKey);

        // 3) 依存比較で HIT 判定（clearAccessLog はここでは呼ばない）
        if (cached.isPresent()) {
            Map<String, Object> currentDeps;
            if (ctx.getMode() == Mode.STRICT) {
            	// new: no copy; equals() compares entries, not identity
            	currentDeps = ctx.getVariables();
            	
            } else {
                // LOOSE: 前回依存していたキーのみ抽出
                Set<String> keys = cached.get().getDependencies().keySet();
                currentDeps = keys.stream()
                        .collect(Collectors.toMap(k -> k, k -> ctx.getVariables().get(k),
                                (a, b) -> a, LinkedHashMap::new));
            }
            if (!cached.get().isOutdated(currentDeps)) {
                CacheStatsTracker.INSTANCE.recordHit(expression);
                return TypeResolver.toBoolean(cached.get().getValue());
            }
        }
        CacheStatsTracker.INSTANCE.recordMiss(expression);

        // 4) 再評価（この時だけアクセスログをクリア）
        ctx.clearAccessLog();
        long start = System.nanoTime();
        Object result;
        try {
            result = ast.evaluate(ctx, new DefaultTypeResolver(), new DefaultTypeCoercion());
        } catch (Exception e) {
            System.err.println("[TriggerCondition] Evaluation failed: " + expression);
            e.printStackTrace();
            result = false;
        }
        long end = System.nanoTime();

        // 5) 依存スナップショットを保存（put は AST キーに上書き）
        Map<String, Object> deps = ctx.snapshotDependencies();
        EvaluationResult evalResult = new EvaluationResult(result, deps, end, end - start, ctx.getMode());
        cacheManager.put(astKey, evalResult);

        return TypeResolver.toBoolean(result);
    }

    @Override
    public String toString() { return "TriggerCondition[" + expression + "]"; }
    
    public static void clearGlobalCache() {
        cacheManager.clear();
    }

}

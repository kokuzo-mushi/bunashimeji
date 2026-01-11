package com.group_finity.mascot.trigger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.group_finity.mascot.trigger.expr.cache.ExprCacheManager;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.ast.Expression;
import com.group_finity.mascot.trigger.expr.ast.LiteralExpression;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;
import com.group_finity.mascot.trigger.util.VariableToEventTypeMapper;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * D-5 Modified version:
 * - Get using AST+Mode key only (dependencies not included in key)
 * - HIT judgment is done by comparing dependencies in EvaluationResult with
 * "current dependency values"
 * - Call clearAccessLog() only when re-evaluating
 * - EvaluationContext shares reference to external variable map (responsibility
 * of constructor caller)
 */
public class TriggerCondition {

    private static final Map<String, Expression> AST_CACHE = new ConcurrentHashMap<>();
    private static final ExprCacheManager cacheManager = new ExprCacheManager();

    private final String expression;
    private EvaluationContext context; // Assumed to share reference
    private final Set<EventType> subscribedEventTypes;

    public TriggerCondition(String expression, Map<String, Object> variables) {
        this.expression = expression;
        if (variables == null)
            variables = new HashMap<>();
        // Assumes EvaluationContext has a reference-sharing constructor
        this.context = new EvaluationContext(variables, new DefaultTypeCoercion(), Mode.STRICT, true);

        // Get or create AST
        Expression ast = AST_CACHE.computeIfAbsent(expression, key -> {
            try {
                Expression parsed = new ExpressionParser(key).parse();
                return (parsed != null) ? parsed : new LiteralExpression(false);
            } catch (Exception e) {
                System.err.println("[TriggerCondition] Parse error: " + key);
                e.printStackTrace();
                return new LiteralExpression(false);
            }
        });

        // Statically analyze AST to identify dependent events
        this.subscribedEventTypes = analyzeDependencies(ast, expression);
    }

    private static Set<EventType> analyzeDependencies(Expression ast, String expressionForLogging) {
        Set<EventType> events = EnumSet.noneOf(EventType.class);

        // Fallback: If AST analysis yielded no events (and it's not a trivial
        // constant),
        // use Regex to find potential variables. This handles cases where AST parsing
        // might fail or differ (e.g. GraalVM).
        if (events.isEmpty()) {
            Set<String> regexVars = extractVariablesWithRegex(expressionForLogging);
            if (!regexVars.isEmpty()) {
                events.addAll(VariableToEventTypeMapper.map(regexVars));
            }
        }

        return events;
    }

    private static Set<String> extractVariablesWithRegex(String expression) {
        Set<String> vars = new HashSet<>();
        // Simple regex to find identifiers that might be variables (e.g., mascot.state,
        // time)
        Pattern p = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");
        Matcher m = p.matcher(expression);
        while (m.find()) {
            vars.add(m.group());
        }
        return vars;
    }

    public EvaluationContext getContext() {
        return context;
    }

    public void setVariable(String name, Object value) {
        if (context != null && context.getVariables() != null) {
            context.getVariables().put(name, value);
        }
    }

    public String getExpression() {
        return expression;
    }

    public Set<EventType> getSubscribedEventTypes() {
        return subscribedEventTypes;
    }

    public boolean evaluate() {
        return evaluate(this.context);
    }

    public boolean evaluate(EvaluationContext externalCtx) {
        if (externalCtx == null && this.context == null) {
            this.context = new EvaluationContext(new HashMap<>(), new DefaultTypeCoercion(), Mode.STRICT, true);
        }
        EvaluationContext ctx = (externalCtx != null) ? externalCtx : this.context;
        if (ctx == null)
            return false;

        // 1) Build AST (fallback to false literal on failure)
        Expression ast = AST_CACHE.computeIfAbsent(expression, key -> {
            try {
                Expression parsed = new ExpressionParser(key).parse();
                return (parsed != null) ? parsed : new LiteralExpression(false);
            } catch (Exception e) {
                System.err.println("[TriggerCondition] Parse error: " + key);
                e.printStackTrace();
                return new LiteralExpression(false);
            }
        });

        // FIXME: Mascotオブジェクトのようなミュータブルな変数の内部状態変化を検知できないため、
        // 一時的にキャッシュを無効化して常に再評価するように修正
        // 2) Get with AST+Mode key (dependencies not included in key)
        com.group_finity.mascot.trigger.expr.cache.ExprCacheKey astKey = com.group_finity.mascot.trigger.expr.cache.ExprCacheKey
                .ofAst(ast, ctx.getMode());
        java.util.Optional<com.group_finity.mascot.trigger.expr.cache.EvaluationResult> cached = cacheManager
                .get(astKey);

        // 3) HIT judgment by dependency comparison (clearAccessLog is not called here)
        if (cached.isPresent()) {
            // 依存している変数の現在の値を取得して比較用マップを作成
            // getVariable() を使うことでドット記法や標準関数も正しく解決する
            Set<String> keys = cached.get().getDependencies().keySet();
            Map<String, Object> currentDeps = new HashMap<>();
            for (String key : keys) {
                currentDeps.put(key, ctx.getVariable(key));
            }
            if (!cached.get().isOutdated(currentDeps)) {
                com.group_finity.mascot.trigger.expr.cache.CacheStatsTracker.INSTANCE.recordHit(expression);
                return TypeResolver.toBoolean(cached.get().getValue());
            }
        }
        com.group_finity.mascot.trigger.expr.cache.CacheStatsTracker.INSTANCE.recordMiss(expression);

        // 4) Re-evaluate (clear access log only at this time)
        ctx.clearAccessLog();
        long start = System.nanoTime(); // Start timing
        Object result;
        try {
            result = ast.evaluate(ctx);
        } catch (Exception e) {
            System.err.println("[TriggerCondition] Evaluation failed: " + expression);
            e.printStackTrace();
            result = false;
        }
        long end = System.nanoTime(); // End timing

        // 5) Save dependency snapshot (put overwrites AST key)
        Map<String, Object> deps = ctx.snapshotDependencies();
        com.group_finity.mascot.trigger.expr.cache.EvaluationResult evalResult = new com.group_finity.mascot.trigger.expr.cache.EvaluationResult(
                result, deps, end, end - start, ctx.getMode());
        cacheManager.put(astKey, evalResult);

        return TypeResolver.toBoolean(result);
    }

    @Override
    public String toString() {
        return "TriggerCondition[" + expression + "]";
    }

    public static void clearGlobalCache() {
        cacheManager.clear();
        AST_CACHE.clear();
    }

}

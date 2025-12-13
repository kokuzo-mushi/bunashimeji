package com.group_finity.mascot.trigger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.group_finity.mascot.trigger.expr.ExpressionEngine;
import com.group_finity.mascot.trigger.expr.cache.CacheStatsTracker;
import com.group_finity.mascot.trigger.expr.cache.EvaluationResult;
import com.group_finity.mascot.trigger.expr.cache.ExprCacheKey;
import com.group_finity.mascot.trigger.expr.cache.ExprCacheManager;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;
import com.group_finity.mascot.trigger.expr.visitor.VariableCollectorVisitor;
import com.group_finity.mascot.trigger.util.VariableToEventTypeMapper;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver;
import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * D-5 Modified version:
 * - Get using AST+Mode key only (dependencies not included in key)
 * - HIT judgment is done by comparing dependencies in EvaluationResult with "current dependency values"
 * - Call clearAccessLog() only when re-evaluating
 * - EvaluationContext shares reference to external variable map (responsibility of constructor caller)
 */
public class TriggerCondition {

    private static final Map<String, ExpressionNode> AST_CACHE = new ConcurrentHashMap<>();
    private static final ExprCacheManager cacheManager = new ExprCacheManager();

    private final String expression;
    private final ExpressionEngine engine;
    private EvaluationContext context; // Assumed to share reference
    private final Set<EventType> subscribedEventTypes;

    public TriggerCondition(String expression, Map<String, Object> variables) {
        this.expression = expression;
        this.engine = new ExpressionEngine();
        if (variables == null) variables = new HashMap<>();
        // Assumes EvaluationContext has a reference-sharing constructor
        this.context = new EvaluationContext(variables, new DefaultTypeCoercion(), Mode.STRICT, true);

        // Get or create AST
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

        // Statically analyze AST to identify dependent events
        this.subscribedEventTypes = analyzeDependencies(ast, expression);
    }

    private static Set<EventType> analyzeDependencies(ExpressionNode ast, String expressionForLogging) {
        Set<EventType> events = EnumSet.noneOf(EventType.class);
        try {
            if (ast != null) {
                // Collect variable names from AST using Visitor
                final VariableCollectorVisitor visitor = new VariableCollectorVisitor();
                ast.accept(visitor); // ExpressionNode and its subclasses must implement accept()
                final Set<String> variables = visitor.getCollectedVariables();

                // Map variable name set to EventType set
                events.addAll(VariableToEventTypeMapper.map(variables));
            }
        } catch (Exception e) {
            System.err.println("Failed to statically analyze expression: '" + expressionForLogging + "'. Falling back to broad event subscription. Error: " + e.getMessage());
            return EnumSet.of(EventType.MASCOT_STATE_CHANGED, EventType.ENVIRONMENT_CHANGED, EventType.SYSTEM_TICK);
        }

        // Fallback: If AST analysis yielded no events (and it's not a trivial constant),
        // use Regex to find potential variables. This handles cases where AST parsing might fail or differ (e.g. GraalVM).
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
        // Simple regex to find identifiers that might be variables (e.g., mascot.state, time)
        Pattern p = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");
        Matcher m = p.matcher(expression);
        while (m.find()) {
            vars.add(m.group());
        }
        return vars;
    }

    public EvaluationContext getContext() { return context; }
    public void setVariable(String name, Object value) {
        if (context != null && context.getVariables() != null) {
            context.getVariables().put(name, value);
        }
    }
    public String getExpression() { return expression; }

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
        if (ctx == null) return false;

        // 1) Build AST (fallback to false literal on failure)
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

        // 2) Get with AST+Mode key (dependencies not included in key)
        ExprCacheKey astKey = ExprCacheKey.ofAst(ast, ctx.getMode());
        Optional<EvaluationResult> cached = cacheManager.get(astKey);

        // 3) HIT judgment by dependency comparison (clearAccessLog is not called here)
        if (cached.isPresent()) {
            Map<String, Object> currentDeps;
            if (ctx.getMode() == Mode.STRICT) {
            	// new: no copy; equals() compares entries, not identity
            	currentDeps = ctx.getVariables();
            	
            } else {
                // LOOSE: Extract only keys depended on last time
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

        // 4) Re-evaluate (clear access log only at this time)
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

        // 5) Save dependency snapshot (put overwrites AST key)
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

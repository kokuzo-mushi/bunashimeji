package com.group_finity.mascot.trigger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.group_finity.mascot.trigger.expr.ExpressionEngine;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver;
import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * TriggerCondition — 単一の条件式を保持し、評価を行う。
 */
public class TriggerCondition {

    private static final Map<String, ExpressionNode> AST_CACHE = new ConcurrentHashMap<>();

    private final String expression;
    private final ExpressionEngine engine;
    private final EvaluationContext context;

    public TriggerCondition(String expression, Map<String, Object> variables) {
        this.expression = expression;
        this.engine = new ExpressionEngine();
        this.engine.setTypeResolver(new DefaultTypeResolver());
        this.engine.setTypeCoercion(new DefaultTypeCoercion());
        this.engine.setMode(Mode.STRICT);
        this.context = new EvaluationContext(variables, new DefaultTypeCoercion(), Mode.STRICT);
    }

    /** 内部のコンテキストを使って評価 */
    public boolean evaluate() {
        return evaluate(this.context);
    }

    /** 外部から渡された EvaluationContext を使って評価 */
    public boolean evaluate(EvaluationContext externalCtx) {
        try {
            EvaluationContext ctx = (externalCtx != null) ? externalCtx : this.context;

            ExpressionNode ast = AST_CACHE.computeIfAbsent(expression, expr -> {
                try {
                    return new ExpressionParser(expr).parse();
                } catch (Exception e) {
                    System.err.println("[TriggerCondition] Failed to parse expression: " + expr);
                    e.printStackTrace();
                    return null;
                }
            });

            if (ast == null) return false;

            Object result = ast.evaluate(ctx, new DefaultTypeResolver(), new DefaultTypeCoercion());
            if (result instanceof Boolean) return (Boolean) result;
            if (result instanceof Number) return ((Number) result).doubleValue() != 0.0;
            return result != null;

        } catch (Exception e) {
            System.err.println("[TriggerCondition] Evaluation failed for expression: " + expression);
            e.printStackTrace();
            return false;
        }
    }

    public void setVariable(String name, Object value) {
        context.setValue(name, value);
    }

    public EvaluationContext getContext() {
        return context;
    }

    public String getExpression() {
        return expression;
    }

    public static void clearCache() {
        AST_CACHE.clear();
    }

    public static int getCacheSize() {
        return AST_CACHE.size();
    }
}

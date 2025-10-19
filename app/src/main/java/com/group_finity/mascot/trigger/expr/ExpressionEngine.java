package com.group_finity.mascot.trigger.expr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver;
import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

public final class ExpressionEngine {

    private TypeResolver typeResolver;
    private TypeCoercion typeCoercion;
    private Mode mode;

    private final Map<String, ExpressionNode> cache = new ConcurrentHashMap<>();

    public ExpressionEngine() {
        // デフォルト構成（明示的に指定されなければDefault）
        this.typeResolver = new DefaultTypeResolver();
        this.typeCoercion = new DefaultTypeCoercion();
        this.mode = Mode.STRICT;
    }

    public ExpressionEngine(TypeResolver resolver, TypeCoercion coercion, Mode mode) {
        this.typeResolver = resolver;
        this.typeCoercion = coercion;
        this.mode = mode;
    }

    // --- 外部注入ポイント ---
    public void setTypeResolver(TypeResolver resolver) { this.typeResolver = resolver; }
    public void setTypeCoercion(TypeCoercion coercion) { this.typeCoercion = coercion; }
    public void setMode(Mode mode) { this.mode = mode; }

    public TypeResolver getTypeResolver() { return typeResolver; }
    public TypeCoercion getTypeCoercion() { return typeCoercion; }
    public Mode getMode() { return mode; }

    public Object evaluate(String expression, EvaluationContext context) {
        if (expression == null || expression.isEmpty()) return Boolean.FALSE;
        try {
            ExpressionNode node = cache.computeIfAbsent(expression, expr -> {
                ExpressionParser parser = new ExpressionParser(expr);
                return parser.parse();
            });
            if (node == null) return Boolean.FALSE;
            return node.evaluate(context, typeResolver, typeCoercion);
        } catch (Exception e) {
            System.err.println("[ExpressionEngine] evaluate error for \"" + expression + "\": " + e.getMessage());
            return Boolean.FALSE;
        }
    }

    public void clearCache() { cache.clear(); }
    public ExpressionNode getCachedNode(String expression) { return cache.get(expression); }
}

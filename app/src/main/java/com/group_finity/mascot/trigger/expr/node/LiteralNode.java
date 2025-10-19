package com.group_finity.mascot.trigger.expr.node;

import java.util.Objects;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

public final class LiteralNode implements ExpressionNode {
    private final Object value;
    private final Class<?> type;

    public LiteralNode(Object value, Class<?> type) {
        this.value = value;
        this.type = type != null ? type : (value != null ? value.getClass() : Object.class);
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        return value;
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        return evaluate(context); // 既存の単純評価にフォールバック
    }

    @Override
    public String toString() {
        return "Literal(" + Objects.toString(value) + ":" + type.getSimpleName() + ")";
    }
}

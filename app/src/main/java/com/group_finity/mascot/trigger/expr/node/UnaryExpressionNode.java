package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * 単項演算子ノード。符号・否定などを扱う。
 */
public class UnaryExpressionNode implements ExpressionNode {

    private final String operator;
    private final ExpressionNode child;

    public UnaryExpressionNode(String operator, ExpressionNode child) {
        this.operator = operator;
        this.child = child;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        Object value = child.evaluate(context);
        switch (operator) {
            case "!": return !((Boolean) value);
            case "+": return ((Number) value);
            case "-": return -((Number) value).doubleValue();
            default: throw new RuntimeException("Unsupported unary operator: " + operator);
        }
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        Object value = child.evaluate(context, resolver, coercion);
        switch (operator) {
            case "!": return !coercion.toBoolean(value);
            case "+": return coercion.toNumber(value);
            case "-": return -coercion.toNumber(value).doubleValue();
            default: throw new RuntimeException("Unsupported unary operator: " + operator);
        }
    }
}

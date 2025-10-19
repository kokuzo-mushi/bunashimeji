package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * 二項演算子ノード。TypeResolver / TypeCoercion を考慮した評価を追加。
 */
public class BinaryExpressionNode implements ExpressionNode {

    private final String operator;
    private final ExpressionNode left;
    private final ExpressionNode right;

    public BinaryExpressionNode(String operator, ExpressionNode left, ExpressionNode right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        // 従来の評価パス（後方互換性維持）
        Object leftValue = left.evaluate(context);
        Object rightValue = right.evaluate(context);
        switch (operator) {
            case "+": return (leftValue instanceof String || rightValue instanceof String)
                    ? String.valueOf(leftValue) + String.valueOf(rightValue)
                    : ((Number) leftValue).doubleValue() + ((Number) rightValue).doubleValue();
            case "-": return ((Number) leftValue).doubleValue() - ((Number) rightValue).doubleValue();
            case "*": return ((Number) leftValue).doubleValue() * ((Number) rightValue).doubleValue();
            case "/": return ((Number) leftValue).doubleValue() / ((Number) rightValue).doubleValue();
            case "===": return leftValue == null ? rightValue == null : leftValue.equals(rightValue);
            case "&&": return ((Boolean) leftValue) && ((Boolean) rightValue);
            case "||": return ((Boolean) leftValue) || ((Boolean) rightValue);
            default: throw new RuntimeException("Unsupported operator: " + operator);
        }
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        Object leftValue = left.evaluate(context, resolver, coercion);
        Object rightValue = right.evaluate(context, resolver, coercion);

        switch (operator) {
            case "+": return coercion.add(leftValue, rightValue);
            case "-": return coercion.subtract(leftValue, rightValue);
            case "*": return coercion.multiply(leftValue, rightValue);
            case "/": return coercion.divide(leftValue, rightValue);
            case "===": return coercion.strictEquals(leftValue, rightValue);
            case "<": return coercion.compare(leftValue, rightValue) < 0;
            case ">": return coercion.compare(leftValue, rightValue) > 0;
            case "<=": return coercion.compare(leftValue, rightValue) <= 0;
            case ">=": return coercion.compare(leftValue, rightValue) >= 0;
            case "&&": return coercion.toBoolean(leftValue) && coercion.toBoolean(rightValue);
            case "||": return coercion.toBoolean(leftValue) || coercion.toBoolean(rightValue);
            default: throw new RuntimeException("Unsupported operator: " + operator);
        }
    }

    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }
}

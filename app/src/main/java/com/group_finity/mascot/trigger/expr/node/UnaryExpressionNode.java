package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * UnaryExpressionNode
 * 単項演算ノード。
 * 対応: +, -, !, ~
 */
public class UnaryExpressionNode implements ExpressionNode {

    private final String operator;
    private final ExpressionNode operand;

    public UnaryExpressionNode(String operator, ExpressionNode operand) {
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        return evaluate(context, null, null);
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        Object value = operand.evaluate(context, resolver, coercion);

        switch (operator) {
            case "+":
                return applyPlus(value);
            case "-":
                return applyMinus(value);
            case "!":
                return applyNot(value);
            case "~":
                return applyBitwiseNot(value);
            default:
                throw new RuntimeException("Unsupported unary operator: " + operator);
        }
    }

    // ---------- 各単項演算の実装 ----------

    private static Object applyPlus(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return normalizeNumber(d);
        }
        throw new RuntimeException("Unary '+' not applicable to type: " + v);
    }

    private static Object applyMinus(Object v) {
        if (v instanceof Number n) {
            double d = -n.doubleValue();
            return normalizeNumber(d);
        }
        throw new RuntimeException("Unary '-' not applicable to type: " + v);
    }

    private static Object applyNot(Object v) {
        if (v instanceof Boolean b) return !b;
        if (v instanceof Number n) return n.doubleValue() == 0.0;
        return v == null || v.toString().isEmpty();
    }

    private static Object applyBitwiseNot(Object v) {
        if (v instanceof Number n) return ~n.longValue();
        throw new RuntimeException("Unary '~' not applicable to type: " + v);
    }

    /**
     * 整数化可能ならLong、そうでなければDoubleで返す
     */
    private static Object normalizeNumber(double val) {
        if (val % 1 == 0 && val >= Long.MIN_VALUE && val <= Long.MAX_VALUE) {
            return Long.valueOf((long) val);
        }
        return val;
    }

    @Override
    public String toString() {
        return "(" + operator + operand + ")";
    }
}
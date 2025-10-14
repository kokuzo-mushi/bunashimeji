package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public final class UnaryExpressionNode implements ExpressionNode {

    private final String operator;
    private final ExpressionNode operand;
    private final Class<?> resultType;

    public UnaryExpressionNode(String operator, ExpressionNode operand, Class<?> resultType) {
        this.operator = operator;
        this.operand = operand;
        this.resultType = resultType;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        Object value = operand.evaluate(context);

        System.out.println("[ExprDebug] unary=" + operator + " | value=" + value +
            (value != null ? "(" + value.getClass().getSimpleName() + ")" : ""));

        if (value == null) {
            return null;
        }

        switch (operator) {
        case "-": {
            if (value instanceof Number n) {
                if (n instanceof Double || n instanceof Float) {
                    return -n.doubleValue();
                } else if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
                    return -n.longValue();
                } else {
                    // その他の Number 型は Double として処理
                    return -n.doubleValue();
                }
            } else if (value instanceof Boolean b) {
                // boolean にマイナスを付けたら、false→0, true→-1 として扱う
                return b ? -1L : 0L;
            }
            // 文字列などなら 0 にする
            return 0L;
        }

            case "+": {
                if (value instanceof Number n) {
                    if (n instanceof Long || n instanceof Integer) {
                        return +n.longValue();
                    } else {
                        return +n.doubleValue();
                    }
                } else if (value instanceof Boolean b) {
                    // +true → 1, +false → 0
                    return b ? 1L : 0L;
                }
                return 0L;
            }

            case "!": {
                if (value instanceof Boolean b) {
                    return !b;
                }
                // 数値も 0=false, それ以外=true として評価
                if (value instanceof Number n) {
                    return n.doubleValue() == 0.0;
                }
                return value == null; // null → true, それ以外 → false
            }

            default:
                throw new RuntimeException("Unsupported unary operator: " + operator);
        }
    }

    @Override
    public Class<?> getResultType() {
        return resultType;
    }
}

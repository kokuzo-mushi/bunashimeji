package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public class BinaryExpression implements Expression {
    private final Expression left;
    private final String operator;
    private final Expression right;

    public BinaryExpression(Expression left, String operator, Expression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        Object leftVal = left.evaluate(context);

        // 短絡評価 (Short-circuit evaluation)
        if (operator.equals("and") || operator.equals("&&")) {
            if (!asBoolean(leftVal)) return false;
            return asBoolean(right.evaluate(context));
        }
        if (operator.equals("or") || operator.equals("||")) {
            if (asBoolean(leftVal)) return true;
            return asBoolean(right.evaluate(context));
        }

        Object rightVal = right.evaluate(context);

        switch (operator) {
            case "==": return equals(leftVal, rightVal);
            case "!=": return !equals(leftVal, rightVal);
            case "===": return strictEquals(leftVal, rightVal);
            case "!==": return !strictEquals(leftVal, rightVal);
            case "<":  return compare(leftVal, rightVal) < 0;
            case "<=": return compare(leftVal, rightVal) <= 0;
            case ">":  return compare(leftVal, rightVal) > 0;
            case ">=": return compare(leftVal, rightVal) >= 0;
            case "+":  return asDouble(leftVal) + asDouble(rightVal);
            case "-":  return asDouble(leftVal) - asDouble(rightVal);
            case "*":  return asDouble(leftVal) * asDouble(rightVal);
            case "/":  return asDouble(leftVal) / asDouble(rightVal);
            case "%":  return asDouble(leftVal) % asDouble(rightVal);
            default: throw new RuntimeException("Unknown operator: " + operator);
        }
    }

    private boolean asBoolean(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        return val != null;
    }

    private double asDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }

    private boolean equals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // 数値同士の比較ならdoubleとして比較
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        // EnumとStringの比較を許容 (XML設定ファイルでの利便性のため)
        if (a instanceof Enum && b instanceof String) {
            return ((Enum<?>) a).name().equals(b);
        }
        if (a instanceof String && b instanceof Enum) {
            return a.equals(((Enum<?>) b).name());
        }
        return a.equals(b);
    }

    private boolean strictEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return 0;
    }
    
    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }
}
package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

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
        TypeCoercion coercer = context.getTypeCoercion();
        Mode mode = context.getMode();

        // 短絡評価 (Short-circuit evaluation)
        if (operator.equals("and") || operator.equals("&&")) {
            Boolean l = coercer.coerceTo(leftVal, Boolean.class, mode);
            if (!Boolean.TRUE.equals(l))
                return false;
            Boolean r = coercer.coerceTo(right.evaluate(context), Boolean.class, mode);
            return Boolean.TRUE.equals(r);
        }
        if (operator.equals("or") || operator.equals("||")) {
            Boolean l = coercer.coerceTo(leftVal, Boolean.class, mode);
            if (Boolean.TRUE.equals(l))
                return true;
            Boolean r = coercer.coerceTo(right.evaluate(context), Boolean.class, mode);
            return Boolean.TRUE.equals(r);
        }

        Object rightVal = right.evaluate(context);

        switch (operator) {
            case "==":
                return equals(leftVal, rightVal);
            case "!=":
                return !equals(leftVal, rightVal);
            case "===":
                return strictEquals(leftVal, rightVal);
            case "!==":
                return !strictEquals(leftVal, rightVal);
            case "<":
                return compare(leftVal, rightVal) < 0;
            case "<=":
                return compare(leftVal, rightVal) <= 0;
            case ">":
                return compare(leftVal, rightVal) > 0;
            case ">=":
                return compare(leftVal, rightVal) >= 0;
            case "+":
                Number lNum = com.group_finity.mascot.trigger.expr.type.TypeResolver.toNumber(leftVal);
                Number rNum = com.group_finity.mascot.trigger.expr.type.TypeResolver.toNumber(rightVal);
                if (lNum != null && rNum != null) {
                    java.math.BigDecimal lBd = new java.math.BigDecimal(lNum.toString());
                    java.math.BigDecimal rBd = new java.math.BigDecimal(rNum.toString());
                    return lBd.add(rBd); // Return BigDecimal (Number)
                }
                return String.valueOf(leftVal) + String.valueOf(rightVal);
            case "-":
                return toDouble(coercer.coerceTo(leftVal, Double.class, mode))
                        - toDouble(coercer.coerceTo(rightVal, Double.class, mode));
            case "*":
                return toDouble(coercer.coerceTo(leftVal, Double.class, mode))
                        * toDouble(coercer.coerceTo(rightVal, Double.class, mode));
            case "/":
                return toDouble(coercer.coerceTo(leftVal, Double.class, mode))
                        / toDouble(coercer.coerceTo(rightVal, Double.class, mode));
            case "%":
                return toDouble(coercer.coerceTo(leftVal, Double.class, mode))
                        % toDouble(coercer.coerceTo(rightVal, Double.class, mode));
            default:
                throw new RuntimeException("Unknown operator: " + operator);
        }
    }

    private double toDouble(Double d) {
        return d != null ? d : 0.0;
    }

    private boolean equals(Object a, Object b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
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
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        // Fallback to String comparison
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }
}
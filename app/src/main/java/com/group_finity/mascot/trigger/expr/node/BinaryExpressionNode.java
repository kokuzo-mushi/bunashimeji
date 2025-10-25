package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public final class BinaryExpressionNode implements ExpressionNode {

    private final ExpressionNode left;
    private final ExpressionNode right;
    private final String operator;

    public BinaryExpressionNode(ExpressionNode left, String operator, ExpressionNode right) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        Object l = left.evaluate(ctx);
        Object r = right.evaluate(ctx);

        return switch (operator) {
            case "+" -> add(l, r);
            case "-" -> sub(l, r);
            case "*" -> mul(l, r);
            case "/" -> div(l, r);
            case "%" -> mod(l, r);
            case "===" -> strictEquals(l, r);
            case "!==" -> !strictEquals(l, r);
            case "==" -> eq(l, r);
            case "!=" -> !eq(l, r);
            case "<" -> cmp(l, r) < 0;
            case "<=" -> cmp(l, r) <= 0;
            case ">" -> cmp(l, r) > 0;
            case ">=" -> cmp(l, r) >= 0;
            case "&&" -> toBool(l) && toBool(r);
            case "||" -> toBool(l) || toBool(r);
            default -> throw new RuntimeException("Unknown operator: " + operator);
        };
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0;
        return v != null;
    }

    private static Object add(Object a, Object b) {
        // 両方が数値、または数値変換可能な文字列の場合は数値加算
        if (isNumericType(a) && isNumericType(b)) {
            double result = toDouble(a) + toDouble(b);
            return normalizeNumber(result);
        }
        // それ以外は文字列連結
        return String.valueOf(a) + String.valueOf(b);
    }

    private static Object sub(Object a, Object b) {
        if (isNumericType(a) && isNumericType(b)) {
            double result = toDouble(a) - toDouble(b);
            return normalizeNumber(result);
        }
        throw new RuntimeException("Unsupported '-' between " + a + " and " + b);
    }

    private static Object mul(Object a, Object b) {
        if (isNumericType(a) && isNumericType(b)) {
            double result = toDouble(a) * toDouble(b);
            return normalizeNumber(result);
        }
        throw new RuntimeException("Unsupported '*' between " + a + " and " + b);
    }

    private static Object div(Object a, Object b) {
        if (isNumericType(a) && isNumericType(b))
            return toDouble(a) / toDouble(b);
        throw new RuntimeException("Unsupported '/' between " + a + " and " + b);
    }

    private static Object mod(Object a, Object b) {
        if (isNumericType(a) && isNumericType(b))
            return toDouble(a) % toDouble(b);
        throw new RuntimeException("Unsupported '%' between " + a + " and " + b);
    }

    private static boolean strictEquals(Object a, Object b) {
        if (a == null || b == null) return a == b;
        // 数値型は正規化して比較（Longに統一可能なら統一）
        if (a instanceof Number && b instanceof Number) {
            Object na = normalizeNumber(((Number) a).doubleValue());
            Object nb = normalizeNumber(((Number) b).doubleValue());
            return na.getClass() == nb.getClass() && na.equals(nb);
        }
        if (a.getClass() != b.getClass()) return false;
        return a.equals(b);
    }

    private static boolean eq(Object a, Object b) {
        if (a instanceof Number && b instanceof Number)
            return Double.compare(toDouble(a), toDouble(b)) == 0;
        if (a instanceof Boolean || b instanceof Boolean)
            return toBool(a) == toBool(b);
        return (a == null) ? b == null : a.toString().equals(b.toString());
    }

    private static int cmp(Object a, Object b) {
        double da = (a instanceof Number n1) ? n1.doubleValue() : 0;
        double db = (b instanceof Number n2) ? n2.doubleValue() : 0;
        return Double.compare(da, db);
    }

    // ========== ヘルパーメソッド ==========

    /**
     * 数値型または数値変換可能な文字列かどうかを判定
     */
    private static boolean isNumericType(Object v) {
        if (v instanceof Number) return true;
        if (v instanceof String s) {
            try {
                Double.parseDouble(s.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * オブジェクトをdoubleに変換
     */
    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * double値を整数化可能ならLong、そうでなければDoubleで返す
     */
    private static Object normalizeNumber(double val) {
        if (val % 1 == 0 && val >= Long.MIN_VALUE && val <= Long.MAX_VALUE) {
            return Long.valueOf((long) val);
        }
        return val;
    }
}
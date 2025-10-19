package com.group_finity.mascot.trigger.expr.type;

/**
 * DefaultTypeCoercion — 標準的な型変換と演算ロジックの実装。
 * 文字列 ⇄ 数値 ⇄ 真偽値 などの相互変換を安全に処理する。
 */
public class DefaultTypeCoercion implements TypeCoercion {

    @Override
    public Number toNumber(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return (Number) value;
        if (value instanceof Boolean) return ((Boolean) value) ? 1L : 0L;
        if (value instanceof String) {
            try {
                String s = ((String) value).trim();
                return s.contains(".") ? Double.parseDouble(s) : Long.parseLong(s);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    public boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase();
            return s.equals("true") || s.equals("1") || s.equals("yes");
        }
        return false;
    }

    @Override
    public String toString(Object value) {
        return (value == null) ? "null" : String.valueOf(value);
    }

    @Override
    public Object add(Object a, Object b) {
        // 1️⃣ 両方 null の場合
        if (a == null && b == null) return 0L;

        // 2️⃣ どちらかが文字列でも、それが数値として解釈できる場合は算術演算を優先
        boolean aNumeric = a instanceof Number || isNumericString(a);
        boolean bNumeric = b instanceof Number || isNumericString(b);

        if (aNumeric && bNumeric) {
            double result = toNumber(a).doubleValue() + toNumber(b).doubleValue();
            return (result % 1 == 0) ? (long) result : result;
        }

        // 3️⃣ それ以外は文字列結合
        return toString(a) + toString(b);
    }

    private boolean isNumericString(Object value) {
        if (!(value instanceof String)) return false;
        try {
            Double.parseDouble(((String) value).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public Object subtract(Object a, Object b) {
        double result = toNumber(a).doubleValue() - toNumber(b).doubleValue();
        return (result % 1 == 0) ? (long) result : result;
    }

    @Override
    public Object multiply(Object a, Object b) {
        double result = toNumber(a).doubleValue() * toNumber(b).doubleValue();
        return (result % 1 == 0) ? (long) result : result;
    }

    @Override
    public Object divide(Object a, Object b) {
        double result = toNumber(a).doubleValue() / toNumber(b).doubleValue();
        return result;
    }

    @Override
    public boolean strictEquals(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a.getClass() != b.getClass()) return false;
        return a.equals(b);
    }

    @Override
    public int compare(Object a, Object b) {
        double da = toNumber(a).doubleValue();
        double db = toNumber(b).doubleValue();
        return Double.compare(da, db);
    }
}

package com.group_finity.mascot.trigger.expr.type;

@SuppressWarnings("unchecked")
public class DefaultTypeCoercion implements TypeCoercion {

    @Override
    public Number toNumber(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n;
        if (value instanceof Boolean b) return b ? 1L : 0L;
        if (value instanceof String s) {
            try {
                String t = s.trim();
                return t.contains(".") ? Double.parseDouble(t) : Long.parseLong(t);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    public boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) {
            String t = s.trim().toLowerCase();
            return t.equals("true") || t.equals("1") || t.equals("yes");
        }
        return false;
    }

    @Override
    public String toString(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    @Override
    public Object add(Object a, Object b) {
        if (a instanceof Number || b instanceof Number ||
            isNumericString(a) || isNumericString(b)) {
            double result = toNumber(a).doubleValue() + toNumber(b).doubleValue();
            return (result % 1 == 0) ? (long) result : result;
        }
        return toString(a) + toString(b);
    }

    private boolean isNumericString(Object v) {
        if (!(v instanceof String)) return false;
        try { Double.parseDouble(((String) v).trim()); return true; }
        catch (NumberFormatException e) { return false; }
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

    @Override
    public <T> T coerceTo(Object value, Class<T> targetType, Mode mode) {
        if (targetType == null) return (T) value;
        if (value == null || targetType.isInstance(value)) return (T) value;

        // プリミティブ型をラッパークラスに変換
        if (targetType.isPrimitive()) {
            if (targetType == int.class) targetType = (Class<T>) Integer.class;
            else if (targetType == long.class) targetType = (Class<T>) Long.class;
            else if (targetType == double.class) targetType = (Class<T>) Double.class;
            else if (targetType == float.class) targetType = (Class<T>) Float.class;
            else if (targetType == boolean.class) targetType = (Class<T>) Boolean.class;
        }

        try {
            if (targetType == Integer.class)
                return (T) Integer.valueOf(parseInt(value, mode));
            if (targetType == Long.class)
                return (T) Long.valueOf(parseLong(value, mode));
            if (targetType == Double.class)
                return (T) Double.valueOf(parseDouble(value, mode));
            if (targetType == Float.class)
                return (T) Float.valueOf((float) parseDouble(value, mode));
            if (targetType == Boolean.class)
                return (T) Boolean.valueOf(toBoolean(value));
            if (targetType == String.class)
                return (T) toString(value);
        } catch (CoercionException e) {
            // STRICT時の例外は再スロー
            if (mode == Mode.STRICT) throw e;
            // LOOSE時はデフォルト値
            return (T) getLooseDefault(targetType);
        }

        // 上記以外の型への変換
        if (mode == Mode.STRICT)
            throw new CoercionException("Cannot coerce " + value + " to " + targetType.getSimpleName());
        return (T) value;
    }

    private int parseInt(Object v, Mode mode) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) {
                if (mode == Mode.STRICT)
                    throw new CoercionException("Cannot coerce \"" + v + "\" to Integer", e);
                return 0;
            }
        }
        if (mode == Mode.STRICT)
            throw new CoercionException("Cannot coerce " + v + " to Integer");
        return 0;
    }

    private long parseLong(Object v, Mode mode) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof Boolean b) return b ? 1L : 0L;
        if (v instanceof String s) {
            try { return Long.parseLong(s.trim()); }
            catch (NumberFormatException e) {
                if (mode == Mode.STRICT)
                    throw new CoercionException("Cannot coerce \"" + v + "\" to Long", e);
                return 0L;
            }
        }
        if (mode == Mode.STRICT)
            throw new CoercionException("Cannot coerce " + v + " to Long");
        return 0L;
    }

    private double parseDouble(Object v, Mode mode) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); }
            catch (NumberFormatException e) {
                if (mode == Mode.STRICT)
                    throw new CoercionException("Cannot coerce \"" + v + "\" to Double", e);
                return 0.0;
            }
        }
        if (mode == Mode.STRICT)
            throw new CoercionException("Cannot coerce " + v + " to Double");
        return 0.0;
    }

    private Object getLooseDefault(Class<?> type) {
        if (type == Double.class || type == Float.class) return 0.0;
        if (type == Integer.class || type == Long.class) return 0;
        if (type == Boolean.class) return false;
        if (type == String.class) return "";
        return null;
    }
}
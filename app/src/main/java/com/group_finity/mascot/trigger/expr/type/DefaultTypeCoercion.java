package com.group_finity.mascot.trigger.expr.type;

public class DefaultTypeCoercion implements TypeCoercion {

    @Override
    public Object coerceTo(Object value, Class<?> targetType, Mode mode) throws CoercionException {
        if (value == null) {
            return null;
        }

        // すでに目的の型ならそのまま返す
        if (targetType.isInstance(value)) {
            return value;
        }

        // プリミティブのラッパー変換
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String s) {
                return Integer.parseInt(s);
            }
        }

        if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String s) {
                return Double.parseDouble(s);
            }
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            if (value instanceof String s) {
                return Boolean.parseBoolean(s);
            }
            if (value instanceof Number n) {
                return n.doubleValue() != 0.0;
            }
        }

        if (targetType == String.class) {
            return value.toString();
        }

        // 到達しない場合は例外
        throw new CoercionException(
            "Cannot coerce value of type " + value.getClass().getName() + " to " + targetType.getName()
        );
    }
}

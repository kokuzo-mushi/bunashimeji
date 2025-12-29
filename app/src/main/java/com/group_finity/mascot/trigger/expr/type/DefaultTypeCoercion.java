package com.group_finity.mascot.trigger.expr.type;

public class DefaultTypeCoercion implements TypeCoercion {

    @SuppressWarnings("unchecked")
    public <T> T coerceTo(Object value, Class<T> targetType, Mode mode) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }

        if (targetType == String.class) {
            return (T) String.valueOf(value);
        }

        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return (T) Integer.valueOf(((Number) value).intValue());
            }
            if (value instanceof String) {
                try {
                    return (T) Integer.valueOf((String) value);
                } catch (NumberFormatException e) {
                    if (mode == Mode.STRICT) throw new CoercionException("Cannot coerce String to Integer: " + value, e);
                }
            }
        }

        if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return (T) Double.valueOf(((Number) value).doubleValue());
            }
            if (value instanceof String) {
                try {
                    return (T) Double.valueOf((String) value);
                } catch (NumberFormatException e) {
                    if (mode == Mode.STRICT) throw new CoercionException("Cannot coerce String to Double: " + value, e);
                }
            }
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) return (T) value;
            if (value instanceof Number) return (T) Boolean.valueOf(((Number) value).doubleValue() != 0.0);
            if (value instanceof String) return (T) Boolean.valueOf(Boolean.parseBoolean((String) value));
        }

        if (mode == Mode.STRICT) {
            throw new CoercionException("Cannot coerce " + value.getClass().getName() + " to " + targetType.getName());
        }
        return null;
    }
}
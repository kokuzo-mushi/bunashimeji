package com.group_finity.mascot.trigger.expr.type;

public interface TypeCoercion {
    Object coerceTo(Object value, Class<?> targetType, Mode mode) throws CoercionException;
}

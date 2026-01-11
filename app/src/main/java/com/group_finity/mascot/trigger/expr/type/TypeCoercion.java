package com.group_finity.mascot.trigger.expr.type;

public interface TypeCoercion {
    <T> T coerceTo(Object value, Class<T> targetType, Mode mode);
}
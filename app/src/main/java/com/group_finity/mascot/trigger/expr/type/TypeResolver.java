package com.group_finity.mascot.trigger.expr.type;

public interface TypeResolver {
    /**
     * Resolve coercion plan for binary operator.
     */
    CoercionPlan resolve(String operator, Class<?> leftType, Class<?> rightType, Mode mode);
}

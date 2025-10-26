package com.group_finity.mascot.trigger.expr.type;

public interface TypeResolver {
    /**
     * Resolve coercion plan for binary operator.
     */
    CoercionPlan resolve(String operator, Class<?> leftType, Class<?> rightType, Mode mode);
    
    public static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty() && !"false".equalsIgnoreCase(s);
        return true;
    }

}

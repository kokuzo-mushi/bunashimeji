package com.group_finity.mascot.trigger.expr.type;

import java.math.BigDecimal;

public interface TypeResolver {

    Object applyUnaryOp(String operator, Object value);

    Object applyBinaryOp(String operator, Object left, Object right);

    static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return !s.isEmpty() && !s.equalsIgnoreCase("false");
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return value != null;
    }

    static Number toNumber(Object value) {
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof String s) {
            try {
                // Use BigDecimal to handle floating point numbers accurately
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return null;
    }
}

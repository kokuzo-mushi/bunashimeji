package com.group_finity.mascot.trigger.expr.type;

/**
 * 型変換時の例外。
 */
public class CoercionException extends RuntimeException {
    public CoercionException(String message) {
        super(message);
    }

    public CoercionException(String message, Throwable cause) {
        super(message, cause);
    }
}

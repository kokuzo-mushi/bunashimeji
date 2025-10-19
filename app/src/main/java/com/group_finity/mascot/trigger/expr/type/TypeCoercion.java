package com.group_finity.mascot.trigger.expr.type;

/**
 * TypeCoercion — 異なる型間の演算・比較・変換を統一的に扱うためのインターフェース。
 * 実装クラス（例：DefaultTypeCoercion）が実際の変換ロジックを提供する。
 */
public interface TypeCoercion {

    // 型変換
    Number toNumber(Object value);
    boolean toBoolean(Object value);
    String toString(Object value);

    // 基本演算
    Object add(Object a, Object b);
    Object subtract(Object a, Object b);
    Object multiply(Object a, Object b);
    Object divide(Object a, Object b);

    // 比較・等価
    boolean strictEquals(Object a, Object b);
    int compare(Object a, Object b);

    // 共通ユーティリティ（Null安全対応）
    default boolean isNull(Object value) {
        return value == null || value == TypeKind.NULL;
    }

    /**
     * 任意の値を目標TypeKindに変換する（必要に応じて実装）。
     */
    default <T> T coerceTo(Object value, Class<T> targetType, Mode mode) {
        if (value == null) return null;
        Object coerced;
        if (targetType == String.class) {
            coerced = toString(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            coerced = toBoolean(value);
        } else if (Number.class.isAssignableFrom(targetType)
                || targetType == int.class
                || targetType == long.class
                || targetType == double.class
                || targetType == float.class) {
            coerced = toNumber(value);
        } else {
            coerced = value;
        }
        return targetType.cast(coerced);
    }

}

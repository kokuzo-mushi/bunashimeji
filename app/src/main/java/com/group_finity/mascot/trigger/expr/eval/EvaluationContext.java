package com.group_finity.mascot.trigger.expr.eval;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;

/**
 * Minimal evaluation context: variable lookup + coercion helpers + TypeCoercion provider.
 */
public final class EvaluationContext {

    private final Map<String, Object> variables;
    private final TypeCoercion typeCoercion;
    private final Mode mode;

    /**
     * フルコンストラクタ
     */
    public EvaluationContext(Map<String, Object> variables, TypeCoercion coercion, Mode mode) {
        this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
        this.typeCoercion = Objects.requireNonNull(coercion);
        this.mode = Objects.requireNonNull(mode);
    }

    /**
     * 簡易コンストラクタ（TypeCoercion をデフォルト実装、Mode を STRICT に）
     * テストや簡易利用向け。
     */
    public EvaluationContext(Map<String, Object> variables) {
        this(variables, new DefaultTypeCoercion(), Mode.STRICT);
    }

    /**
     * 中間コンストラクタ（TypeCoercion 指定だが Mode はデフォルト）
     */
    public EvaluationContext(Map<String, Object> variables, TypeCoercion coercion) {
        this(variables, coercion, Mode.STRICT);
    }

    /** 値の取得（型未指定） */
    public Object getValue(String name) {
        return variables.get(name);
    }

    /** 値の取得（期待型あり）。存在しない場合は null を返す。 */
    public Object getValue(String name, Class<?> expectedType) {
        Object v = getValue(name);
        if (v == null) return null;
        // coercion を使って期待型へ変換する
        return typeCoercion.coerceTo(v, expectedType, mode);
    }

    public TypeCoercion getTypeCoercion() {
        return typeCoercion;
    }

    public Mode getMode() {
        return mode;
    }

    /** 変数の設定（テスト時や実行時に値を差し替えるために便利） */
    public void setValue(String name, Object value) {
        variables.put(name, value);
    }

    /** 変数が存在するか */
    public boolean hasValue(String name) {
        return variables.containsKey(name);
    }

    // helpers used by nodes
    public Double toNumber(Object o) {
        if (o == null) return 0.0;
        Object v = typeCoercion.coerceTo(o, Double.class, mode);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    public Boolean toBoolean(Object o) {
        if (o == null) return Boolean.FALSE;
        Object v = typeCoercion.coerceTo(o, Boolean.class, mode);
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @Override
    public String toString() {
        return "EvaluationContext(vars=" + variables + ", mode=" + mode + ")";
    }
}


/*
package com.group_finity.mascot.trigger.expr.eval;

import java.util.Map;

import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;

public final class EvaluationContext {

    private final Map<String, Object> variables;
    private final TypeCoercion coercion;
    private final Mode mode;

    public EvaluationContext(Map<String, Object> variables, TypeCoercion coercion, Mode mode) {
        this.variables = variables;
        this.coercion = coercion;
        this.mode = mode;
    }

    public Object getValue(String name) {
        return variables.get(name);
    }

    public Object getValue(String name, Class<?> expectedType) {
        Object v = getValue(name);
        if (v == null) return null;
        return coercion.coerceTo(v, expectedType, mode);
    }

    public TypeCoercion getTypeCoercion() {
        return coercion;
    }

    public Mode getMode() {
        return mode;
    }

    // Utility coercion helpers
    public boolean toBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        return v != null && Boolean.parseBoolean(v.toString());
    }

    public Number toNumber(Object v) {
        if (v instanceof Number n) return n;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0.0;
        }
    }
}
*/
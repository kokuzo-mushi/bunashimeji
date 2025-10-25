package com.group_finity.mascot.trigger.expr.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;

/**
 * 評価時の変数・型変換・モード管理。
 * D-4d fix: スナップショットの完全ディープコピー化。
 */
public final class EvaluationContext {

    private final Map<String, Object> variables;
    private final TypeCoercion typeCoercion;
    private final Mode mode;
    private final boolean immutable;

    // --- コンストラクタ群 ---
    public EvaluationContext(Map<String, Object> vars, TypeCoercion coercion, Mode mode, boolean immutable) {
        this.variables = (vars != null) ? new HashMap<>(vars) : new HashMap<>();
        this.typeCoercion = coercion;
        this.mode = mode;
        this.immutable = immutable;
    }

    public EvaluationContext(Map<String, Object> vars, TypeCoercion coercion, Mode mode) {
        this(vars, coercion, mode, false);
    }

    public EvaluationContext(Map<String, Object> vars) {
        this(vars, new DefaultTypeCoercion(), Mode.STRICT, false);
    }

    // --- 値アクセス ---
    public synchronized Object getValue(String name) { return variables.get(name); }
    public synchronized Object getVariable(String name) { return variables.get(name); }

    public synchronized void setValue(String name, Object value) {
        if (immutable) throw new UnsupportedOperationException("Immutable context");
        variables.put(name, value);
    }

    public synchronized boolean hasValue(String name) { return variables.containsKey(name); }

    // --- ✅ 完全ディープコピー版スナップショット ---
    public EvaluationContext snapshotImmutable() {
        synchronized (this) {
            Map<String, Object> deepCopy = deepCopyMap(this.variables);
            return new EvaluationContext(Collections.unmodifiableMap(deepCopy), typeCoercion, mode, true);
        }
    }

    // --- 深いコピー（List, Map, Array対応） ---
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> src) {
        Map<String, Object> dest = new HashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?,?> m)
                dest.put(e.getKey(), Collections.unmodifiableMap(deepCopyMap((Map<String, Object>) m)));
            else if (v instanceof List<?> l)
                dest.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(l)));
            else if (v instanceof Object[] arr)
                dest.put(e.getKey(), Arrays.copyOf(arr, arr.length));
            else if (v instanceof EvaluationContext c)
                dest.put(e.getKey(), c.snapshotImmutable());
            else
                dest.put(e.getKey(), v);
        }
        return dest;
    }

    public synchronized Map<String, Object> getVariablesSnapshot() {
        return new HashMap<>(variables);
    }

    public TypeCoercion getTypeCoercion() { return typeCoercion; }
    public Mode getMode() { return mode; }
    public boolean isImmutable() { return immutable; }
}

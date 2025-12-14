package com.group_finity.mascot.trigger.expr.eval;

import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EvaluationContext {
    private final Map<String, Object> variables;
    private final TypeCoercion typeCoercion;
    private final Mode mode;
    private final boolean recordAccess;
    private final Set<String> accessLog = new HashSet<>();

    public EvaluationContext(Map<String, Object> variables) {
        // デフォルトでアクセスログ記録を有効化
        this(variables, null, Mode.STRICT, true);
    }

    public EvaluationContext(Map<String, Object> variables, TypeCoercion typeCoercion, Mode mode) {
        this(variables, typeCoercion, mode, true);
    }

    public EvaluationContext(Map<String, Object> variables, TypeCoercion typeCoercion, Mode mode, boolean recordAccess) {
        this.variables = variables != null ? variables : new HashMap<>();
        this.typeCoercion = typeCoercion;
        this.mode = mode != null ? mode : Mode.STRICT;
        this.recordAccess = recordAccess;
    }

    public Object getVariable(String name) {
        if (recordAccess) {
            accessLog.add(name);
        }
        return variables.get(name);
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setValue(String name, Object value) {
        variables.put(name, value);
    }

    public void removeVariable(String name) {
        variables.remove(name);
    }

    public Map<String, Object> getVariablesSnapshot() {
        return new HashMap<>(variables);
    }

    public Mode getMode() {
        return mode;
    }

    public void clearAccessLog() {
        accessLog.clear();
    }

    public Map<String, Object> snapshotDependencies() {
        Map<String, Object> snapshot = new HashMap<>();
        for (String key : accessLog) {
            // 値が null の場合も許容して格納する
            snapshot.put(key, getVariable(key));
        }
        return snapshot;
    }
}
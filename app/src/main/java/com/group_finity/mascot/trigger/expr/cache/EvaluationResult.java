package com.group_finity.mascot.trigger.expr.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * Holds evaluated result value and dependency snapshot.
 */
public final class EvaluationResult {

    private final Object value;
    private final Map<String, Object> dependencies;
    private final long timestamp;
    private final long evalDurationNanos;
    private final Mode mode;

    public EvaluationResult(
            Object value,
            Map<String, Object> dependencies,
            long timestamp,
            long evalDurationNanos,
            Mode mode) {
        this.value = value;
        this.dependencies = Collections.unmodifiableMap(new LinkedHashMap<>(dependencies));
        this.timestamp = timestamp;
        this.evalDurationNanos = evalDurationNanos;
        this.mode = mode;
    }

    public Object getValue() { return value; }
    public Map<String, Object> getDependencies() { return dependencies; }
    public long getTimestamp() { return timestamp; }
    public long getEvalDurationNanos() { return evalDurationNanos; }
    public Mode getMode() { return mode; }

    public boolean isOutdated(Map<String, Object> currentDeps) {
        return !Objects.equals(this.dependencies, currentDeps);
    }

    @Override
    public String toString() {
        return String.format(
            "[EvalResult value=%s mode=%s deps=%d time=%dμs]",
            value,
            mode,
            dependencies.size(),
            evalDurationNanos / 1000
        );
    }
}

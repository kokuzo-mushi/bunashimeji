package com.group_finity.mascot.trigger;

import java.util.List;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * CompositeTrigger — 複数の TriggerCondition をまとめて評価。
 */
public class CompositeTrigger extends Trigger {

    public enum Mode { ALL, ANY }

    private final List<TriggerCondition> conditions;
    private final Mode mode;

    public CompositeTrigger(List<TriggerCondition> conditions, Mode mode) {
        this.conditions = conditions;
        this.mode = mode;
    }

    @Override
    public boolean check(EvaluationContext ctx) {
        if (conditions == null || conditions.isEmpty()) return false;

        return switch (mode) {
            case ALL -> conditions.stream().allMatch(c -> c.evaluate(ctx));
            case ANY -> conditions.stream().anyMatch(c -> c.evaluate(ctx));
        };
    }

    @Override
    public String toString() {
        return "CompositeTrigger{" +
                "mode=" + mode +
                ", conditionCount=" + (conditions == null ? 0 : conditions.size()) +
                '}';
    }
}

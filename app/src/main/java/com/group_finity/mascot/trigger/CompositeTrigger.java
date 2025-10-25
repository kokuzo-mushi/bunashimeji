package com.group_finity.mascot.trigger;

import java.util.List;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * CompositeTrigger — 複数の TriggerCondition をまとめて評価。
 * （修正版: デバッグログ追加）
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
        if (conditions == null || conditions.isEmpty()) {
            System.err.println("[CompositeTrigger] No conditions to evaluate");
            return false;
        }

        System.out.printf("[CompositeTrigger] Checking %d conditions with mode=%s, context=%s%n",
            conditions.size(), mode, ctx.getVariablesSnapshot());

        boolean result = switch (mode) {
            case ALL -> {
                for (TriggerCondition c : conditions) {
                    boolean condResult = c.evaluate(ctx);
                    System.out.printf("[CompositeTrigger.ALL] Condition '%s' = %s%n", 
                        c.getExpression(), condResult);
                    if (!condResult) {
                        System.out.println("[CompositeTrigger.ALL] Failed early, returning false");
                        yield false;
                    }
                }
                System.out.println("[CompositeTrigger.ALL] All conditions passed!");
                yield true;
            }
            case ANY -> {
                for (TriggerCondition c : conditions) {
                    boolean condResult = c.evaluate(ctx);
                    System.out.printf("[CompositeTrigger.ANY] Condition '%s' = %s%n", 
                        c.getExpression(), condResult);
                    if (condResult) {
                        System.out.println("[CompositeTrigger.ANY] Found matching condition, returning true");
                        yield true;
                    }
                }
                System.out.println("[CompositeTrigger.ANY] No conditions matched, returning false");
                yield false;
            }
        };

        System.out.printf("[CompositeTrigger] Final result: %s%n", result);
        return result;
    }

    @Override
    public String toString() {
        return "CompositeTrigger{" +
                "mode=" + mode +
                ", conditionCount=" + (conditions == null ? 0 : conditions.size()) +
                '}';
    }
}

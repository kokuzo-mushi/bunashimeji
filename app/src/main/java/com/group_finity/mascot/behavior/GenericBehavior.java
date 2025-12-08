package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.trigger.Trigger;
import com.group_finity.mascot.trigger.TriggerCondition;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * A generic, concrete implementation of the {@link Behavior} interface.
 * <p>
 * This class combines a trigger condition with an action, forming a complete
 * behavioral rule. The trigger logic is delegated to a {@link Trigger} object,
 * typically a {@link TriggerCondition}.
 */
public class GenericBehavior implements Behavior {

    private final Trigger condition;
    private final Action action;

    public GenericBehavior(Trigger condition, Action action) {
        this.condition = condition;
        this.action = action;
    }

    @Override
    public boolean evaluate(EvaluationContext context) {
        return condition.evaluate(context);
    }

    @Override
    public Action getAction() {
        return action;
    }
}
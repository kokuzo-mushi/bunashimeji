package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public abstract class Trigger {

    public abstract boolean check(EvaluationContext ctx);

    public void execute(EvaluationContext ctx) {
        // override if needed
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public interface Expression {
    Object evaluate(EvaluationContext context);
}
package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * Generic, non-generic core ExpressionNode.
 * Concrete nodes may expose getResultType() where applicable.
 */
public interface ExpressionNode {
    /**
     * Evaluate node using context. Result is boxed Object.
     */
    Object evaluate(EvaluationContext ctx);

    /**
     * Static estimation of node result type. May be null if unknown.
     */
    Class<?> getResultType();
}

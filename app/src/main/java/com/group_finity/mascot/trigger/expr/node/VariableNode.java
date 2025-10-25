package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * VariableNode
 * EvaluationContextから変数値を取得するASTノード。
 */
public class VariableNode implements ExpressionNode {

    private final String name;

    public VariableNode(String name) {
        this.name = name;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        if (context == null) return null;
        return context.getVariable(name);
    }

    @Override
    public String toString() {
        return name;
    }
}

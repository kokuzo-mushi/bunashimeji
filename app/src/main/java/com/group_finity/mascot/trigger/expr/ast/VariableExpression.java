package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public class VariableExpression implements Expression {
    private final String name;

    public VariableExpression(String name) {
        this.name = name;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        return context.getVariable(name);
    }
}
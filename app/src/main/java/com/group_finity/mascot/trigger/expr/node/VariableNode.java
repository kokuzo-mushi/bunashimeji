package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

public final class VariableNode implements ExpressionNode {

    private final String name;
    private final Class<?> expectedType;

    public VariableNode(String name, Class<?> expectedType) {
        this.name = name;
        this.expectedType = expectedType;
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        return expectedType != null
            ? ctx.getValue(name, expectedType)
            : ctx.getValue(name);
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        return evaluate(context); // 既存の単純評価にフォールバック
    }
}





/*
package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public final class VariableNode implements ExpressionNode {
    private final String name;
    private final Class<?> expectedType; // may be null (unknown)

    public VariableNode(String name, Class<?> expectedType) {
        this.name = name;
        this.expectedType = expectedType;
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        if (expectedType != null) {
        	System.out.println("[DEBUG] " + name + " = " + ctx.getValue(name));

            return ctx.getValue(name, expectedType);
        } else {
            return ctx.getValue(name);
        }
    }

    @Override
    public Class<?> getResultType() {
        return expectedType;
    }

    public String getName() {
        return name;
    }
}
*/
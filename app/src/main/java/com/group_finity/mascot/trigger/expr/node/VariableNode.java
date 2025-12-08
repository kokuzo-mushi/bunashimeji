package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;
import com.group_finity.mascot.trigger.expr.visitor.AstVisitor;

public class VariableNode extends ExpressionNode {

    private final String variableName;

    public VariableNode(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableName() {
        return variableName;
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        return context.getVariable(variableName);
    }

    @Override
    public void accept(AstVisitor visitor) {
        visitor.visit(this);
    }
}
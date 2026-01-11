package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;
import com.group_finity.mascot.trigger.expr.visitor.AstVisitor;

public class UnaryExpressionNode extends ExpressionNode {

    private final ExpressionNode operand;
    private final String operator;

    public UnaryExpressionNode(String operator, ExpressionNode operand) {
        this.operator = operator;
        this.operand = operand;
    }

    public ExpressionNode getOperand() {
        return operand;
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        Object value = operand.evaluate(context, resolver, coercion);
        return resolver.applyUnaryOp(operator, value);
    }

    @Override
    public void accept(AstVisitor visitor) {
        visitor.visit(this);
    }
}
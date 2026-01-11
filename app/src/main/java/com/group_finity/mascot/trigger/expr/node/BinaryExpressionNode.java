package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;
import com.group_finity.mascot.trigger.expr.visitor.AstVisitor;

public class BinaryExpressionNode extends ExpressionNode {

    private final ExpressionNode left;
    private final ExpressionNode right;
    private final String operator;

    public BinaryExpressionNode(ExpressionNode left, String operator, ExpressionNode right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public ExpressionNode getLeft() {
        return left;
    }

    public ExpressionNode getRight() {
        return right;
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        Object leftVal = left.evaluate(context, resolver, coercion);
        Object rightVal = right.evaluate(context, resolver, coercion);
        return resolver.applyBinaryOp(operator, leftVal, rightVal);
    }

    @Override
    public void accept(AstVisitor visitor) {
        visitor.visit(this);
    }
}
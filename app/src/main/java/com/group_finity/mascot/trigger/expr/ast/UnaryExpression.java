package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

public class UnaryExpression implements Expression {
    private final String operator;
    private final Expression operand;

    public UnaryExpression(String operator, Expression operand) {
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        Object val = operand.evaluate(context);
        TypeCoercion coercer = context.getTypeCoercion();
        Mode mode = context.getMode();
        switch (operator) {
            case "!":
                Boolean b = coercer.coerceTo(val, Boolean.class, mode);
                return !Boolean.TRUE.equals(b);
            case "-":
                Double d = coercer.coerceTo(val, Double.class, mode);
                return -(d != null ? d : 0.0);
            default: throw new RuntimeException("Unknown unary operator: " + operator);
        }
    }

}
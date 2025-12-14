package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

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
        switch (operator) {
            case "!": return !asBoolean(val);
            case "-": return -asDouble(val);
            default: throw new RuntimeException("Unknown unary operator: " + operator);
        }
    }

    private boolean asBoolean(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        return val != null;
    }

    private double asDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }
}
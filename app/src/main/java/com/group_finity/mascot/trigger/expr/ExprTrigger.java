package com.group_finity.mascot.trigger.expr;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;

public final class ExprTrigger {

    private final ExpressionNode expression;

    public ExprTrigger(String exprText) {
        this.expression = new ExpressionParser(exprText).parse();
    }

    public boolean check(EvaluationContext ctx) {
        try {
            Object result = expression.evaluate(ctx);
            if (result instanceof Boolean b) return b;
            if (result instanceof Number n) return n.doubleValue() != 0.0;
            return result != null;
        } catch (Exception e) {
            System.err.println("[ExprTrigger] Evaluation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String toString() {
        return "ExprTrigger(" + expression + ")";
    }
}

package com.group_finity.mascot.trigger.expr;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.parser.ExpressionParser;

public final class ExpressionEngine {

    public ExpressionNode parse(String expr) {
        // ExpressionParser はコンストラクタで式文字列を受け取り、parse() は引数なし
        return new ExpressionParser(expr).parse();
    }

    public Object evaluate(String expr, EvaluationContext ctx) {
        ExpressionNode node = parse(expr);
        return node.evaluate(ctx);
    }
}

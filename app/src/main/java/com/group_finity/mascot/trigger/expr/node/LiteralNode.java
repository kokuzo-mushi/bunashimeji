package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * LiteralNode
 * リテラル値（数値・文字列・真偽値）を表すASTノード。
 */
public class LiteralNode implements ExpressionNode {

    private final Object value;

    public LiteralNode(Object value) {
        this.value = value;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    public Object getValue() {
        return value;
    }
}

package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * VariableNode
 * EvaluationContextから変数値を取得するASTノード。
 */
public class VariableNode implements ExpressionNode {

    private final String name;

    public VariableNode(String name) {
        this.name = name;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        if (context == null) return null;
        return context.getVariable(name);
    }

    @Override
    public String toString() {
        return name;
    }
    @Override
    public Object evaluate(EvaluationContext context,
                           TypeResolver resolver,
                           TypeCoercion coercion) {
        // 変数取得時にアクセス記録
        if (context != null) {
            context.markAccess(name);  // ← 新規メソッド呼び出し
        }
        return context != null ? context.getVariable(name) : null;
    }

}

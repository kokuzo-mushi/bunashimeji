package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;
import com.group_finity.mascot.trigger.expr.visitor.AstVisitor;

/**
 * 抽象構文木（AST）のすべてのノードの基底クラス。
 */
public abstract class ExpressionNode {

    /**
     * このノードを評価し、結果を返します。
     */
    public abstract Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion);

    /**
     * Visitorを受け入れ、ダブルディスパッチを実行します。
     */
    public abstract void accept(AstVisitor visitor);
}
package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;

/**
 * ExpressionNode — すべてのASTノードの共通インタフェース。
 * TypeResolver / TypeCoercion に対応するevaluate拡張を追加。
 */
public interface ExpressionNode {

    Object evaluate(EvaluationContext context);

    /**
     * TypeResolver / TypeCoercion を外部から注入可能な拡張評価メソッド。
     * デフォルトでは既存evaluate(context)を呼び出す。
     */
    default Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        return evaluate(context);
    }

    /**
     * デフォルトのResolver/Coercionを用いた簡易評価。
     */
    default Object evaluateWithDefaults(EvaluationContext context) {
        return evaluate(context, new DefaultTypeResolver(), new DefaultTypeCoercion());
    }
}

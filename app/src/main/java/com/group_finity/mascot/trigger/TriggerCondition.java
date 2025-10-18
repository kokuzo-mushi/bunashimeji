package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.expr.ExprEvaluator;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * TriggerCondition:
 * トリガーの発動条件を表すクラス。
 * 内部で式（JavaScript風の条件式）をASTキャッシュ付きで評価する。
 */
public class TriggerCondition {

    /** 評価対象の式（例: "shimeji.x > 100 && shimeji.state === 'falling'"） */
    private final String expression;

    /** 式評価器（ASTキャッシュ機構を内包） */
    private final ExprEvaluator evaluator;

    /**
     * TriggerCondition の生成。
     *
     * @param expression 評価する式
     * @param evaluator 共有される式評価器
     */
    public TriggerCondition(String expression, ExprEvaluator evaluator) {
        this.expression = expression;
        this.evaluator = evaluator;
    }

    /**
     * 条件を評価する。
     *
     * @param context 評価コンテキスト（変数環境など）
     * @return 条件が真の場合 true、偽またはエラー時は false
     */
    public boolean evaluate(EvaluationContext context) {
        try {
            Object result = evaluator.evaluate(expression, context);
            return (result instanceof Boolean b) ? b : false;
        } catch (Exception e) {
            // 評価中のエラーはログ出力して false 扱いにする
            System.err.println("[TriggerCondition] Evaluation error: " + e.getMessage());
            return false;
        }
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "TriggerCondition[" + expression + "]";
    }
}

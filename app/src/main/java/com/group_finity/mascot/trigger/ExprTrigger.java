package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

import java.util.Set;

/**
 * 式言語 (Expression Language) を用いて発火条件を定義するトリガー。
 * Shimeji Neo の中核的なトリガー実装です。
 */
public class ExprTrigger implements Trigger {

    private final TriggerCondition condition;
    private final Set<EventType> subscribedEvents;

    /**
     * @param expression 発火条件を定義する式 (例: "mascot.x > 100 && window.active")
     */
    public ExprTrigger(String expression) {
        // D-5時点のTriggerConditionコンストラクタに合わせてnullを渡す
        // TriggerCondition が内部で式のパースと静的解析を一度だけ行う
        this.condition = new TriggerCondition(expression, null);
        this.subscribedEvents = this.condition.getSubscribedEventTypes();
    }

    @Override
    public boolean check(EventEnvelope<?> eventEnvelope, EvaluationContext context) {
        // ExprTrigger はイベントのペイロード自体は直接利用せず、
        // EvaluationContext を通じて更新された最新の状態（変数）を参照して式を評価する。
        // イベントの発生が、式の再評価の「きっかけ」となる。
        return this.condition.evaluate(context);
    }

    @Override
    public Set<EventType> getSubscribedEventTypes() {
        return subscribedEvents;
    }
}



/** 
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
*/
package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope; // NOTE: This should be trigger.event
import com.group_finity.mascot.trigger.event.EventType;     // NOTE: This should be trigger.event

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
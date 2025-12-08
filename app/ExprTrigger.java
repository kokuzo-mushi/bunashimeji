package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;

import java.util.EnumSet;
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
        // TriggerCondition は内部で式をパースし、キャッシュ機構を持つ
        this.condition = new TriggerCondition(expression);

        // 本来は式の静的解析などを行い、依存する変数に応じて購読イベントを決定するのが理想。
        // ここでは簡略化のため、状態変化や時間経過の可能性があるイベント全般を購読する。
        this.subscribedEvents = EnumSet.of(
                EventType.MASCOT_STATE_CHANGED,
                EventType.ENVIRONMENT_CHANGED,
                EventType.SYSTEM_TICK // 時間経過に依存する式 (例: 'tick % 10 == 0') もあるため
        );
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
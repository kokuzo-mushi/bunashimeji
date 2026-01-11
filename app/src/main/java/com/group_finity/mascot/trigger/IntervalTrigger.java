package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

import java.util.Collections;
import java.util.Set;

/**
 * 一定時間ごとに発火するトリガー。
 */
public class IntervalTrigger implements Trigger {

    private final long intervalMillis;
    private long accumulatedMillis = 0;

    public IntervalTrigger(long intervalMillis) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Interval must be positive.");
        }
        this.intervalMillis = intervalMillis;
    }

    @Override
    public boolean evaluate(EventEnvelope<?> event, EvaluationContext context) {
        // このトリガーは SYSTEM_TICK イベントにのみ関心がある
        if (event.getType() != EventType.SYSTEM_TICK) {
            return false;
        }

        // ペイロードから経過時間を取得
        Object payload = event.getPayload();
        if (!(payload instanceof Long deltaTimeMillis)) {
            return false; // 不正なペイロードの場合は何もしない
        }

        this.accumulatedMillis += deltaTimeMillis;

        if (this.accumulatedMillis >= this.intervalMillis) {
            this.accumulatedMillis %= this.intervalMillis; // 蓄積時間をリセット（超過分を考慮）
            return true;
        }

        return false;
    }

    @Override
    public Set<EventType> getSubscribedEventTypes() {
        // SYSTEM_TICK イベントのみを購読する
        return Collections.singleton(EventType.SYSTEM_TICK);
    }
}
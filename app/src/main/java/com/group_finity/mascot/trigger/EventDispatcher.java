package com.group_finity.mascot.trigger;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches events to triggers and fires the first one whose conditions are met.
 * This class is central to the event-driven architecture of Shimeji Neo.
 * It holds a list of registered triggers and evaluates them when an event occurs.
 */
public class EventDispatcher {

    private final EvaluationContext context;
    private final Mascot mascot;
    private final List<Trigger> triggers = new CopyOnWriteArrayList<>();

    public EventDispatcher(EvaluationContext context, Mascot mascot) {
        this.context = context;
        this.mascot = mascot;
    }

    public void registerTrigger(Trigger trigger) {
        this.triggers.add(trigger);
    }

    /**
     * Evaluates registered triggers in response to an event.
     * <p>
     * This method iterates through the list of triggers. For each trigger, it checks if its
     * conditions are met by calling {@link Trigger#evaluate(EvaluationContext)}.
     * <p>
     * The first trigger that evaluates to {@code true} is considered "fired".
     * If the fired trigger is a {@link Behavior}, its associated {@link Action} is
     * retrieved and passed to the {@link Mascot} to be executed. The evaluation then stops.
     * <p>
     * Currently, this method evaluates all triggers for any event. Future optimizations
     * could filter triggers based on the event type to improve performance.
     *
     * @param event The event that triggered the evaluation (currently unused, for future filtering).
     */
    public void evaluateTriggers(EventEnvelope<?> event) {
        if (mascot == null) {
            // Cannot execute actions without a mascot.
            return;
        }

        List<Behavior> candidates = new ArrayList<>();

        // イベント変数をコンテキストに注入して、条件式から参照できるようにする
        this.context.setValue("event", event);

        try {
            for (final Trigger trigger : triggers) {
                if (trigger.evaluate(event, this.context)) {
                    if (trigger instanceof Behavior) {
                        Behavior b = (Behavior) trigger;
                        candidates.add(b);
                    }
                }
            }
        } finally {
            // 評価終了後にイベント変数を削除
            this.context.removeVariable("event");
        }

        if (candidates.isEmpty()) {
            return;
        }

        // 候補の中からFrequencyに基づいて抽選を行う
        Behavior selectedBehavior = selectBehavior(candidates);
        
        if (selectedBehavior != null) {
            Action action = selectedBehavior.getAction();

            // 現在実行中のアクションと同じインスタンスであれば、リセット・再設定を行わない
            // これにより、毎フレーム条件を満たすアクション（Fallなど）がリセットされずに継続実行され、加速などが有効になる
            if (this.mascot.getCurrentAction() == action) {
                return;
            }

            action.reset(); // アクションを再利用する前に必ず初期化する
            this.mascot.setNextAction(action);
        }
    }

    private Behavior selectBehavior(List<Behavior> candidates) {
        int totalFrequency = candidates.stream().mapToInt(Behavior::getFrequency).sum();
        if (totalFrequency == 0) return candidates.get(0);

        int random = new Random().nextInt(totalFrequency);
        int current = 0;
        for (Behavior behavior : candidates) {
            current += behavior.getFrequency();
            if (random < current) return behavior;
        }
        return candidates.get(candidates.size() - 1);
    }

    public void clear() {
        triggers.clear();
    }

    public int getRegisteredCount() {
        return triggers.size();
    }
}
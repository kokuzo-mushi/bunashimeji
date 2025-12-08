package com.group_finity.mascot.trigger;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.event.Event;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

import java.util.List;
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
    public void evaluateTriggers(Event event) {
        if (mascot == null) {
            // Cannot execute actions without a mascot.
            return;
        }

        for (final Trigger trigger : triggers) {
            if (trigger.evaluate(this.context)) {
                // The trigger's condition is met.
                // In Phase 2, the Trigger will be part of a Behavior that holds an Action.
                // We check if the trigger is a Behavior to get the action.
                if (trigger instanceof Behavior) {
                    Action action = ((Behavior) trigger).getAction();
                    this.mascot.setNextAction(action);
                }

                // Stop after the first successful trigger, as a mascot can only perform one action at a time.
                break;
            }
        }
    }

    public void clear() {
        triggers.clear();
    }

    public int getRegisteredCount() {
        return triggers.size();
    }
}
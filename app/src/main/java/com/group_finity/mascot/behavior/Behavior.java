package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.trigger.Trigger;
import com.group_finity.mascot.trigger.TriggerCondition;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

import java.util.Set;

/**
 * Represents a behavior of the mascot.
 * A behavior defines a condition (Trigger) and an Action to be executed when the condition is met.
 * <p>
 * Behaviors are typically loaded from configuration files (behaviors.xml) and registered
 * with the EventDispatcher.
 */
public class Behavior implements Trigger {

    private final String name;
    private final Action action;
    private final TriggerCondition condition;
    private boolean hidden;
    private int frequency;

    /**
     * Creates a new Behavior.
     *
     * @param name                The name of the behavior (e.g., "Walk", "Jump").
     * @param action              The action to execute when the condition is met.
     * @param conditionExpression The logical expression that determines when this behavior should trigger.
     * @param hidden              If true, this behavior will not appear in the user menu.
     * @param frequency           The relative frequency of this behavior being selected.
     */
    public Behavior(String name, Action action, String conditionExpression, boolean hidden, int frequency) {
        this.name = name;
        this.action = action;
        this.condition = new TriggerCondition(conditionExpression, null);
        this.hidden = hidden;
        this.frequency = frequency;
    }

    /**
     * Overload for backward compatibility or simpler creation.
     */
    public Behavior(String name, Action action, String conditionExpression) {
        this(name, action, conditionExpression, false, 1);
    }

    @Override
    public boolean evaluate(EventEnvelope<?> event, EvaluationContext context) {
        return condition.evaluate(context);
    }

    @Override
    public Set<EventType> getSubscribedEventTypes() {
        return condition.getSubscribedEventTypes();
    }

    public Action getAction() {
        return action;
    }

    public String getName() {
        return name;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return "Behavior{name='" + name + "', condition='" + condition.getExpression() + "'}";
    }
}
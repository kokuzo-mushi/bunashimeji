package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.trigger.Trigger;

/**
 * Connects a {@link Trigger} with an {@link Action}.
 * This interface represents a rule: "When the trigger's condition is met, perform this action."
 * <p>
 * It extends {@link Trigger} to inherit the evaluation logic.
 */
public interface Behavior extends Trigger {

    /**
     * @return The action to be executed when this behavior's trigger condition is met.
     */
    Action getAction();
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * Represents a concrete action that a mascot can perform, such as walking or jumping.
 * The full implementation of actions is part of Phase 2.
 */
public interface Action {
    /**
     * Executes one step of the action. This method is called repeatedly by the core loop.
     * @param mascot The mascot instance to apply changes to (e.g., position, state).
     */
    void execute(Mascot mascot);

    /**
     * Checks if the action has more steps to execute.
     * @return {@code true} if the action is ongoing, {@code false} if it has finished.
     */
    boolean hasNext();
}
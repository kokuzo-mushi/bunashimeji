package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * A concrete action for walking.
 * <p>
 * This is a template for Phase 2 implementation. It demonstrates the basic
 * structure of an action that has a finite duration.
 */
public class WalkAction implements Action {

    private static final int DEFAULT_STEPS = 100;

    private int stepsRemaining;

    public WalkAction() {
        // In a real implementation, this might take parameters from an XML configuration.
        this.stepsRemaining = DEFAULT_STEPS;
    }

    @Override
    public void execute(Mascot mascot) {
        // In a full implementation, this method would:
        // 1. Update the mascot's X/Y coordinates based on its direction.
        // 2. Select the appropriate animation frame for walking.
        // 3. Decrement the step counter.
        this.stepsRemaining--;
    }

    @Override
    public boolean hasNext() {
        // The action continues as long as there are steps remaining.
        return this.stepsRemaining > 0;
    }
}
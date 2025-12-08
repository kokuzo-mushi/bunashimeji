package com.group_finity.mascot;

import com.group_finity.mascot.action.Action;

/**
 * The core class representing a single mascot character.
 * <p>
 * It holds the mascot's state (e.g., position, orientation) and manages the
 * execution of its current {@link Action}.
 */
public class Mascot {

    // The action currently being executed.
    private Action currentAction;

    // The next action to be executed, typically set by the EventDispatcher.
    private Action nextAction;

    // Mascot's state variables. These will be manipulated by Actions.
    private int x = 0;
    private int y = 0;
    private boolean lookRight = true;

    /**
     * The main "heartbeat" method for the mascot. This should be called periodically
     * by the main application loop.
     * <p>
     * It manages the lifecycle of actions:
     * 1. If the current action is finished, it transitions to the next scheduled action.
     * 2. It then executes a single step of the current action.
     */
    public void tick() {
        // If the current action is null or has finished, switch to the next one.
        if (this.currentAction == null || !this.currentAction.hasNext()) {
            this.currentAction = this.nextAction;
            this.nextAction = null;
        }

        // If there's an action to perform, execute it.
        if (this.currentAction != null) {
            this.currentAction.execute(this);
        }
    }

    /**
     * Schedules the next action to be executed once the current one is complete.
     * This method is designed to be called by the {@code EventDispatcher} when a trigger fires.
     *
     * @param nextAction The action to schedule.
     */
    public void setNextAction(Action nextAction) {
        this.nextAction = nextAction;
    }

    //--- Getters and Setters for State ---

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * Action that makes the mascot fall down until it hits the ground.
 */
public class FallAction implements Action {

    private static final int FALL_SPEED = 4;
    private boolean finished = false;

    @Override
    public void execute(Mascot mascot) {
        if (mascot.isOnGround()) {
            finished = true;
            return;
        }

        int newY = mascot.getY() + FALL_SPEED;
        mascot.setY(newY);

        // Check if grounded after movement
        if (mascot.isOnGround()) {
            finished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }
}
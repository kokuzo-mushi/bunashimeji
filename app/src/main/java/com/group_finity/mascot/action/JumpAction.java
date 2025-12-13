package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * Action that makes the mascot jump with an initial velocity.
 * Gravity is applied until the mascot lands.
 */
public class JumpAction implements Action {

    private static final int GRAVITY = 1;
    private int velocityY;
    private final int velocityX;
    private boolean finished = false;

    /**
     * @param velocityY Initial vertical velocity (negative for up).
     * @param velocityX Horizontal velocity.
     */
    public JumpAction(int velocityY, int velocityX) {
        this.velocityY = velocityY;
        this.velocityX = velocityX;
    }

    @Override
    public void execute(Mascot mascot) {
        // If moving down (velocityY > 0) and hitting the ground, finish the action.
        // We check velocityY > 0 to ensure we don't stop immediately at the start of a jump.
        if (mascot.isOnGround() && velocityY > 0) {
            finished = true;
            return;
        }

        mascot.setX(mascot.getX() + velocityX);
        mascot.setY(mascot.getY() + velocityY);

        // Apply gravity for the next frame
        velocityY += GRAVITY;
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }
}
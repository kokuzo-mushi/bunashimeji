package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * Action representing the state of being dragged by the user.
 * This action continues as long as the mascot is being dragged.
 * Actual position updates are typically handled by the mouse event listeners in the View layer.
 */
public class DraggedAction implements Action {

    private boolean finished = false;

    @Override
    public void execute(Mascot mascot) {
        // If the mascot is no longer being dragged, finish this action.
        if (!mascot.isBeingDragged()) {
            finished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }
}
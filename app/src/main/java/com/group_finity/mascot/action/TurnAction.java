package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

public class TurnAction implements Action {

    private boolean executed = false;

    @Override
    public void execute(Mascot mascot) {
        mascot.setLookRight(!mascot.isLookRight());
        executed = true;
    }

    @Override
    public boolean hasNext() {
        return !executed;
    }

    @Override
    public void reset() {
        executed = false;
    }
}
package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * 何もしないアクション。
 * 動作の終了待ちや、特定の状態を維持するために使用されます。
 */
public class StayAction implements Action {

     private final int duration;
    private long startTime = -1;

    public StayAction(int duration) {
        this.duration = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (startTime == -1) {
            startTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean hasNext() {
        return startTime == -1 || (System.currentTimeMillis() - startTime) < duration;
    }

    @Override
    public void reset() {
        this.startTime = -1;
    }
}
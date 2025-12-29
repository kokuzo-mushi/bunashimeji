package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * マスコットの向きを指定された方向に変更するアクション。
 * アニメーションは持たず、一瞬で完了します。
 */
public class LookAction implements Action {
    private final boolean lookRight;
    private boolean finished = false;

    public LookAction(boolean lookRight) {
        this.lookRight = lookRight;
    }

    @Override
    public void execute(Mascot mascot) {
        mascot.setLookRight(lookRight);
        finished = true;
        System.out.println("[LookAction] Executed. LookRight: " + lookRight);
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public String toString() {
        return "LookAction[lookRight=" + lookRight + "]";
    }
}
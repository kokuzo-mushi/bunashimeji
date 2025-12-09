package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;

/**
 * マスコットの向きを瞬時に反転させるアクション。
 * このアクションは一度実行されるとすぐに終了します。
 */
public class TurnAction implements Action {

    private boolean hasNext = true;

    @Override
    public void execute(Mascot mascot) {
        // 現在の向きを取得し、それを反転させて設定する
        mascot.setLookRight(!mascot.isLookRight());
        // アクションは一度実行したら終了
        this.hasNext = false;
    }

    @Override
    public boolean hasNext() {
        return this.hasNext;
    }
}
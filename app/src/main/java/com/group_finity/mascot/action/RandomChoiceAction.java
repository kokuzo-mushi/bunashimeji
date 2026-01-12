package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import java.util.List;
import java.util.Random;

/**
 * 登録されたアクションの中からランダムで1つを選んで実行するアクション。
 */
public class RandomChoiceAction implements Action {

    private List<Action> candidates;
    private Action currentAction;
    private final Random random = new Random();

    public void setCandidates(List<Action> candidates) {
        this.candidates = candidates;
    }

    public List<Action> getCandidates() {
        return candidates;
    }

    @Override
    public void execute(Mascot mascot) {
        // まだアクションが選ばれていない場合、候補からランダムに選択
        if (currentAction == null && candidates != null && !candidates.isEmpty()) {
            currentAction = candidates.get(random.nextInt(candidates.size()));
            currentAction.reset();
        }

        if (currentAction != null) {
            currentAction.execute(mascot);
        }
    }

    @Override
    public boolean hasNext() {
        return currentAction != null && currentAction.hasNext();
    }

    @Override
    public void reset() {
        // リセット時は選択をクリアし、次回実行時に再抽選する
        currentAction = null;
    }
}
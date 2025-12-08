package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import java.util.ArrayList;
import java.util.List;

/**
 * 複数のアクションを順番に実行するアクション。
 */
public class SequenceAction implements Action {

    private List<Action> sequence;
    private int currentIndex = 0;

    /**
     * ActionBuilderが使用するためのコンストラクタ。
     * 後から setSequence() でアクションリストを設定する必要があります。
     */
    public SequenceAction() {
        this.sequence = new ArrayList<>();
    }

    /**
     * BehaviorBuilderが使用するためのコンストラクタ。
     * @param sequence 実行するアクションのリスト
     */
    public SequenceAction(List<Action> sequence) {
        this.sequence = sequence;
    }

    public void setSequence(List<Action> sequence) {
        this.sequence = sequence;
        this.currentIndex = 0; // リストが再設定されたらリセット
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        Action currentAction = sequence.get(currentIndex);
        currentAction.execute(mascot);

        if (!currentAction.hasNext()) {
            currentIndex++;
        }
    }

    @Override
    public boolean hasNext() {
        return currentIndex < sequence.size();
    }
}
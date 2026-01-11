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
    private int loopCount = 1; // デフォルトは1回実行
    private int currentLoop = 0;

    /**
     * ActionBuilderが使用するためのコンストラクタ。
     * 後から setSequence() でアクションリストを設定する必要があります。
     */
    public SequenceAction() {
        this.sequence = new ArrayList<>();
    }

    /**
     * BehaviorBuilderが使用するためのコンストラクタ。
     * 
     * @param sequence 実行するアクションのリスト
     */
    public SequenceAction(List<Action> sequence) {
        this.sequence = sequence;
    }

    public void setSequence(List<Action> sequence) {
        this.sequence = sequence;
        this.currentIndex = 0; // リストが再設定されたらリセット
    }

    public void setLoopCount(int loopCount) {
        this.loopCount = loopCount;
    }

    @Override
    public void execute(Mascot mascot) {
        // シーケンスが終了するまで、または継続中のアクションに到達するまでループ
        while (hasNext()) {
            Action currentAction = sequence.get(currentIndex);
            currentAction.execute(mascot);

            // 現在のアクションがまだ継続中の場合、このフレームでの処理は終了
            if (currentAction.hasNext()) {
                break;
            }

            // 現在のアクションが終了したので、次のアクションのインデックスに進む
            currentIndex++;

            // シーケンスの最後に到達した場合
            if (currentIndex >= sequence.size()) {
                currentLoop++;
                // ループ回数が残っている、または無限ループ(-1)の場合
                if (loopCount == -1 || currentLoop < loopCount) {
                    // シーケンスをリセットして再開
                    resetSequence();
                } else {
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        // まだアクションが残っているか、ループ回数が残っていれば継続
        return currentIndex < sequence.size();
    }

    @Override
    public void reset() {
        this.currentLoop = 0;
        resetSequence();
    }

    private void resetSequence() {
        this.currentIndex = 0;
        for (Action action : sequence) {
            action.reset();
        }
    }
}
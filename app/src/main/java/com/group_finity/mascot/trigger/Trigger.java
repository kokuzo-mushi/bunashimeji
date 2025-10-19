package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * Trigger — 抽象的なトリガー基底。
 * 条件評価や実行処理を拡張クラスが定義する。
 */
public abstract class Trigger {

    /** トリガー条件を評価 */
    public abstract boolean check(EvaluationContext ctx);

    /** トリガー発火時の挙動（必要に応じてオーバーライド） */
    public void execute(EvaluationContext ctx) {}

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

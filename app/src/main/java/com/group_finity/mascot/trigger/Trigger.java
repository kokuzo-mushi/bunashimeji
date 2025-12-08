package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

import java.util.Set;

/**
 * イベント発火の条件を抽象化するインターフェース。
 * すべてのトリガーはこのインターフェースを実装します。
 */
public interface Trigger {

    /**
     * 指定されたイベントを元に、このトリガーが発火条件を満たすかを評価します。
     *
     * @param eventEnvelope 現在ディスパッチされているイベント
     * @param context       評価に必要な変数などを提供するコンテキスト
     * @return 条件を満たせば true
     */
    boolean check(EventEnvelope<?> eventEnvelope, EvaluationContext context);

    /**
     * このトリガーが関心を持つイベントの型一覧を返します。
     * EventDispatcher はこの情報を利用して、不要なトリガーへのイベント配送をスキップします。
     *
     * @return 関心のある EventType の Set
     */
    Set<EventType> getSubscribedEventTypes();
}








/**
package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * Trigger — 抽象的なトリガー基底。
 * 条件評価や実行処理を拡張クラスが定義する。
public abstract class Trigger {

    /** トリガー条件を評価 
    public abstract boolean check(EvaluationContext ctx);

    /** トリガー発火時の挙動（必要に応じてオーバーライド） 
    public void execute(EvaluationContext ctx) {}

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
*/
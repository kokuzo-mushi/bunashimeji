package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;

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
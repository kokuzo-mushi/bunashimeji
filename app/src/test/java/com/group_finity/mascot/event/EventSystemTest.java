package com.group_finity.mascot.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * Shimeji Neo: EventDispatcher 統合テスト
 * イベント発生 -> トリガー評価 -> アクション設定 のフローを検証する。
 */
public class EventSystemTest {

    @Test
    public void testEventDispatchingTriggersAction() {
        // 1. 準備
        Mascot mascot = mock(Mascot.class); // 実インスタンスではなくモックを使用
        EvaluationContext context = new EvaluationContext(new HashMap<>());
        EventDispatcher dispatcher = new EventDispatcher(context, mascot);

        // モックのアクションとビヘイビアを作成
        Action mockAction = mock(Action.class);
        Behavior mockBehavior = mock(Behavior.class);

        // 条件に合致するように設定
        when(mockBehavior.evaluate(any(), any())).thenReturn(true);
        when(mockBehavior.getAction()).thenReturn(mockAction);

        dispatcher.registerTrigger(mockBehavior);

        // 2. 実行
        // 任意のイベントを発行
        EventEnvelope<Void> event = new EventEnvelope<>(EventType.SYSTEM_TICK, null, this);
        dispatcher.evaluateTriggers(event);

        // 3. 検証
        // マスコットにアクションがセットされたか確認
        // setNextActionが呼ばれたことを検証する（Mascotの実装に依存しない）
        verify(mascot).setNextAction(mockAction);
    }

    @Test
    public void testEventVariableInjection() {
        // EventDispatcherがイベントオブジェクトをコンテキスト変数 "event" として注入しているか確認
        Mascot mascot = mock(Mascot.class); // モックを使用
        EvaluationContext context = new EvaluationContext(new HashMap<>());
        EventDispatcher dispatcher = new EventDispatcher(context, mascot);

        // 検証用トリガー (Behaviorのふりをする)
        Behavior spyBehavior = mock(Behavior.class);
        
        // evaluateメソッド内でコンテキストを検査する
        when(spyBehavior.evaluate(any(), any())).thenAnswer(invocation -> {
            EvaluationContext ctx = invocation.getArgument(1);
            Object eventObj = ctx.getVariable("event");
            
            // event変数が存在し、かつ期待する型であることを確認
            if (eventObj instanceof EventEnvelope) {
                EventEnvelope<?> env = (EventEnvelope<?>) eventObj;
                return env.getType() == EventType.MOUSE_PRESSED;
            }
            return false;
        });
        
        Action mockAction = mock(Action.class);
        when(spyBehavior.getAction()).thenReturn(mockAction);

        dispatcher.registerTrigger(spyBehavior);

        // 1. 条件に合わないイベント (SYSTEM_TICK)
        dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, null, this));
        verify(mascot, never()).setNextAction(any()); // アクション設定メソッドは呼ばれないはず

        // 2. 条件に合うイベント (MOUSE_PRESSED)
        dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.MOUSE_PRESSED, null, this));
        verify(mascot).setNextAction(mockAction); // アクション設定メソッドが呼ばれるはず
        
        // 3. 評価終了後にevent変数がクリアされているか確認
        assertNull(context.getVariable("event"), "評価終了後はevent変数はクリアされるべき");
    }
}

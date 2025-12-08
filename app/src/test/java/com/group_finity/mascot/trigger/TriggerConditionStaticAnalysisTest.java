package com.group_finity.mascot.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.group_finity.mascot.trigger.event.EventType;

/**
 * TriggerCondition の静的解析ロジックをテストするクラス。
 * <p>
 * 式が依存する変数を基に、購読すべき EventType が正しく特定されることを検証します。
 */
class TriggerConditionStaticAnalysisTest {

    /**
     * 指定された式から生成された TriggerCondition が、期待されるイベントセットを購読するかを検証するヘルパーメソッド。
     *
     * @param expression テスト対象の式
     * @param expectedEvents 期待される EventType のセット
     */
    private void assertSubscribedEvents(String expression, Set<EventType> expectedEvents) {
        // TriggerCondition のコンストラクタは内部で静的解析を実行する
        TriggerCondition condition = new TriggerCondition(expression, null);
        Set<EventType> actualEvents = condition.getSubscribedEventTypes();

        assertEquals(expectedEvents, actualEvents,
                "Expression '" + expression + "' should subscribe to " + expectedEvents);
    }

    @Test
    @DisplayName("mascot.* 変数に依存する式は MASCOT_STATE_CHANGED を購読すべき")
    void testMascotStateDependency() {
        assertSubscribedEvents("mascot.state == 'idle'", EnumSet.of(EventType.MASCOT_STATE_CHANGED));
    }

    @Test
    @DisplayName("time/tick 変数に依存する式は SYSTEM_TICK を購読すべき")
    void testTimeDependency() {
        assertSubscribedEvents("time > 1000", EnumSet.of(EventType.SYSTEM_TICK));
    }

    @Test
    @DisplayName("window.* 変数に依存する式は ENVIRONMENT_CHANGED を購読すべき")
    void testEnvironmentDependency() {
        assertSubscribedEvents("window.isMinimized", EnumSet.of(EventType.ENVIRONMENT_CHANGED));
    }

    @Test
    @DisplayName("複数の種類の変数に依存する式は、対応するすべてのイベントを購読すべき")
    void testMultipleDependencies() {
        assertSubscribedEvents("mascot.x > 100 && time % 10 == 0 && window.isActive",
                EnumSet.of(EventType.MASCOT_STATE_CHANGED, EventType.SYSTEM_TICK, EventType.ENVIRONMENT_CHANGED));
    }

    @Test
    @DisplayName("変数を含まない定数式は、どのイベントも購読すべきではない")
    void testConstantExpression() {
        assertSubscribedEvents("true", EnumSet.noneOf(EventType.class));
        assertSubscribedEvents("100 > 50", EnumSet.noneOf(EventType.class));
    }

    @ParameterizedTest
    @CsvSource({
            "'tick > 0', SYSTEM_TICK",
            "'ie.isForeground', ENVIRONMENT_CHANGED"
    })
    @DisplayName("様々な単一依存の式をテストする")
    void testSingleDependencyWithParameters(String expression, EventType expectedType) {
        assertSubscribedEvents(expression, EnumSet.of(expectedType));
    }
}
package com.group_finity.mascot.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.group_finity.mascot.trigger.event.EventType;

/**
 * Test class for static analysis logic of TriggerCondition.
 * <p>
 * Verifies that the correct EventType to subscribe to is identified based on the variables the expression depends on.
 */
class TriggerConditionStaticAnalysisTest {

    /**
     * Helper method to verify that the TriggerCondition generated from the specified expression subscribes to the expected event set.
     *
     * @param expression Expression to test
     * @param expectedEvents Expected set of EventTypes
     */
    private void assertSubscribedEvents(String expression, Set<EventType> expectedEvents) {
        // TriggerCondition constructor executes static analysis internally
        TriggerCondition condition = new TriggerCondition(expression, null);
        Set<EventType> actualEvents = condition.getSubscribedEventTypes();

        assertEquals(expectedEvents, actualEvents,
                "Expression '" + expression + "' should subscribe to " + expectedEvents);
    }

    @Test
    @DisplayName("Expressions dependent on mascot.* variables should subscribe to MASCOT_STATE_CHANGED")
    void testMascotStateDependency() {
        assertSubscribedEvents("mascot.state == 'idle'", EnumSet.of(EventType.MASCOT_STATE_CHANGED));
    }

    @Test
    @DisplayName("Expressions dependent on time/tick variables should subscribe to SYSTEM_TICK")
    void testTimeDependency() {
        assertSubscribedEvents("time > 1000", EnumSet.of(EventType.SYSTEM_TICK));
    }

    @Test
    @DisplayName("Expressions dependent on window.* variables should subscribe to ENVIRONMENT_CHANGED")
    void testEnvironmentDependency() {
        assertSubscribedEvents("window.isMinimized", EnumSet.of(EventType.ENVIRONMENT_CHANGED));
    }

    @Test
    @DisplayName("Expressions dependent on multiple types of variables should subscribe to all corresponding events")
    void testMultipleDependencies() {
        assertSubscribedEvents("mascot.x > 100 && time % 10 == 0 && window.isActive",
                EnumSet.of(EventType.MASCOT_STATE_CHANGED, EventType.SYSTEM_TICK, EventType.ENVIRONMENT_CHANGED));
    }

    @Test
    @DisplayName("Constant expressions containing no variables should not subscribe to any events")
    void testConstantExpression() {
        assertSubscribedEvents("true", EnumSet.noneOf(EventType.class));
        assertSubscribedEvents("100 > 50", EnumSet.noneOf(EventType.class));
    }

    @ParameterizedTest
    @CsvSource({
            "'tick > 0', SYSTEM_TICK",
            "'ie.isForeground', ENVIRONMENT_CHANGED"
    })
    @DisplayName("Test various single dependency expressions")
    void testSingleDependencyWithParameters(String expression, EventType expectedType) {
        assertSubscribedEvents(expression, EnumSet.of(expectedType));
    }
}
package com.group_finity.mascot.trigger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

@ExtendWith(MockitoExtension.class)
public class EventDispatcherTest {

    private Map<String, Object> vars;
    private EvaluationContext ctx;
    private EventDispatcher dispatcher;

    @Mock
    private Mascot mascot;

    @BeforeEach
    void setUp() {
        vars = new HashMap<>();
        ctx = new EvaluationContext(vars);
        dispatcher = new EventDispatcher(ctx, mascot);
    }


    @Test
    void testTriggerFiresAndSetsAction() {
        // Arrange
        Behavior mockBehavior = mock(Behavior.class);
        Action mockAction = mock(Action.class);
        when(mockBehavior.evaluate(any(), any(EvaluationContext.class))).thenReturn(true);
        when(mockBehavior.getAction()).thenReturn(mockAction);

        dispatcher.registerTrigger(mockBehavior);

        // Act
        dispatcher.evaluateTriggers(null); // The event parameter is not used yet

        // Assert
        verify(mascot).setNextAction(mockAction); // Verify that the mascot's action was set
    }

    @Test
    void testTriggerDoesNotFireWhenConditionFalse() {
        // Arrange
        Behavior mockBehavior = mock(Behavior.class);
        when(mockBehavior.evaluate(any(), any(EvaluationContext.class))).thenReturn(false);
        dispatcher.registerTrigger(mockBehavior);

        // Act
        dispatcher.evaluateTriggers(null);

        // Assert
        verify(mascot, never()).setNextAction(any(Action.class)); // Verify action was NOT set
    }

    @Test
    void testDispatcherHandlesMultipleTriggers() {
        // Arrange
        Behavior behavior1 = mock(Behavior.class);
        Behavior behavior2 = mock(Behavior.class);
        Action action2 = mock(Action.class);

        when(behavior1.evaluate(any(), any(EvaluationContext.class))).thenReturn(false);
        when(behavior2.evaluate(any(), any(EvaluationContext.class))).thenReturn(true);
        when(behavior2.getAction()).thenReturn(action2);

        dispatcher.registerTrigger(behavior1);
        dispatcher.registerTrigger(behavior2);
        assertEquals(2, dispatcher.getRegisteredCount());

        // Act
        dispatcher.evaluateTriggers(null);

        // Assert
        verify(mascot).setNextAction(action2);
    }

    @Test
    void testDispatcherStopsAtFirstMatchingTrigger() {
        // Arrange
        Behavior behavior1 = mock(Behavior.class);
        Action action1 = mock(Action.class);
        Behavior behavior2 = mock(Behavior.class);

        // Both triggers will evaluate to true
        when(behavior1.evaluate(any(), any(EvaluationContext.class))).thenReturn(true);
        when(behavior1.getAction()).thenReturn(action1);

        dispatcher.registerTrigger(behavior1);
        dispatcher.registerTrigger(behavior2);

        // Act
        dispatcher.evaluateTriggers(null);

        // Assert
        verify(mascot).setNextAction(action1); // Verify the first action was set
        verify(behavior2, never()).evaluate(any(), any(EvaluationContext.class)); // Verify the second trigger was not evaluated
    }

    @Test
    void testNoTriggersRegistered() {
        // Arrange
        assertEquals(0, dispatcher.getRegisteredCount());

        // Act
        dispatcher.evaluateTriggers(null);

        // Assert
        verify(mascot, never()).setNextAction(any(Action.class));
    }

    @Test
    void testClearRemovesAllTriggers() {
        // Arrange
        Behavior mockBehavior = mock(Behavior.class);
        dispatcher.registerTrigger(mockBehavior);
        assertEquals(1, dispatcher.getRegisteredCount());

        // Act
        dispatcher.clear();

        // Assert
        assertEquals(0, dispatcher.getRegisteredCount());

        // Act again to ensure no action is set
        dispatcher.evaluateTriggers(null);
        verify(mascot, never()).setNextAction(any(Action.class));
    }
}

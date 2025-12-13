package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorTest {

    @Mock
    private Action mockAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void evaluate_shouldReturnTrue_whenConditionIsMet() {
        // Arrange
        String condition = "mascot.state == \"idle\"";
        Behavior behavior = new Behavior("TestBehavior", mockAction, condition);

        Map<String, Object> vars = new HashMap<>();
        vars.put("mascot.state", "idle");
        EvaluationContext context = new EvaluationContext(vars);

        // Act
        boolean result = behavior.evaluate(null, context);

        // Assert
        assertTrue(result, "Behavior should evaluate to true when condition is met");
    }

    @Test
    void evaluate_shouldReturnFalse_whenConditionIsNotMet() {
        // Arrange
        String condition = "mascot.state == \"idle\"";
        Behavior behavior = new Behavior("TestBehavior", mockAction, condition);

        Map<String, Object> vars = new HashMap<>();
        vars.put("mascot.state", "running");
        EvaluationContext context = new EvaluationContext(vars);

        // Act
        boolean result = behavior.evaluate(null, context);

        // Assert
        assertFalse(result, "Behavior should evaluate to false when condition is not met");
    }

    @Test
    void getAction_shouldReturnConfiguredAction() {
        Behavior behavior = new Behavior("TestBehavior", mockAction, "true");
        assertEquals(mockAction, behavior.getAction());
    }
}
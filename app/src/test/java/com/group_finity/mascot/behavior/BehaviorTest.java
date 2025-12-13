package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.TriggerCondition;
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
        TriggerCondition.clearGlobalCache();
    }

    @Test
    void evaluate_shouldReturnTrue_whenConditionIsMet() {
        // Arrange
        String condition = "mascot.state == \"idle\"";
        Behavior behavior = new Behavior("TestBehavior", mockAction, condition);

        // mascot.state を解決できるように、ネストしたMap構造を作成する
        Map<String, Object> mascot = new HashMap<>();
        mascot.put("state", "idle");
        Map<String, Object> vars = new HashMap<>();
        vars.put("mascot", mascot);
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

        // mascot.state を解決できるように、ネストしたMap構造を作成する
        Map<String, Object> mascot = new HashMap<>();
        mascot.put("state", "running");
        Map<String, Object> vars = new HashMap<>();
        vars.put("mascot", mascot);
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

    @Test
    void evaluate_shouldHandleNoArgumentFunctionCall() {
        // Arrange
        // Math.random() は 0.0 以上 1.0 未満の値を返すため、常に true となる条件
        String condition = "Math.random() < 2.0";
        Behavior behavior = new Behavior("TestBehavior", mockAction, condition);
        EvaluationContext context = new EvaluationContext(new HashMap<>());

        // Act & Assert
        assertTrue(behavior.evaluate(null, context), "Math.random() should be parsed and evaluated correctly");
    }

    @Test
    void evaluate_shouldHandleFunctionCallWithArguments() {
        // Arrange
        // Math.max(10, 20) returns 20, so 20 > 15 is true
        String condition = "Math.max(10, 20) > 15";
        Behavior behavior = new Behavior("TestBehavior", mockAction, condition);
        EvaluationContext context = new EvaluationContext(new HashMap<>());

        // Act & Assert
        assertTrue(behavior.evaluate(null, context), "Math.max(10, 20) should return 20 and satisfy the condition");
    }
}
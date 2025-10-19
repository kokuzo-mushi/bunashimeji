package com.group_finity.mascot.trigger.expr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

public class ExpressionEngineTest {

    private ExpressionEngine engine;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        engine = new ExpressionEngine();
        // EvaluationContext(Map<String, Object>) コンストラクタを使用
        context = new EvaluationContext(new HashMap<>(), new DefaultTypeCoercion(), Mode.STRICT);
    }

    @Test
    void testSimpleArithmetic() {
        Object result = engine.evaluate("1 + 2 * 3", context);
        assertTrue(result instanceof Number);
        assertEquals(7L, ((Number) result).longValue());
    }

    @Test
    void testBooleanComparison() {
        Object result = engine.evaluate("5 > 3 && 2 < 4", context);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void testEquality() {
        Object result = engine.evaluate("'abc' === 'abc'", context);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void testVariableLookup() {
        context.setValue("x", 10L);
        context.setValue("y", 5L);
        Object result = engine.evaluate("x * y + 2", context);
        assertTrue(result instanceof Number);
        assertEquals(52L, ((Number) result).longValue());
    }

    @Test
    void testUnaryOperators() {
        context.setValue("flag", false);
        Object result = engine.evaluate("!flag", context);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void testParenthesesPrecedence() {
        Object result = engine.evaluate("(1 + 2) * 3", context);
        assertTrue(result instanceof Number);
        assertEquals(9L, ((Number) result).longValue());
    }

    @Test
    void testInvalidExpressionReturnsFalse() {
        Object result = engine.evaluate("1 + * 2", context);
        assertEquals(Boolean.FALSE, result);
    }
}

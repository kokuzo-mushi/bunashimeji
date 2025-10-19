package com.group_finity.mascot.trigger.expr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver;
import com.group_finity.mascot.trigger.expr.type.Mode;

public class ExpressionEngineTypeTest {

    private ExpressionEngine engine;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        engine = new ExpressionEngine();
        engine.setTypeResolver(new DefaultTypeResolver());
        engine.setTypeCoercion(new DefaultTypeCoercion());
        engine.setMode(Mode.STRICT);
        context = new EvaluationContext(new HashMap<>(), new DefaultTypeCoercion(), Mode.STRICT);
    }

    @Test
    void stringToNumberCoercion() {
        Object result = engine.evaluate("\"2\" + 3", context);
        assertEquals(5L, ((Number) result).longValue());
    }

    @Test
    void mixedComparison() {
        Object result = engine.evaluate("\"10.5\" < 11", context);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void booleanCoercion() {
        Object result = engine.evaluate("\"true\" && false", context);
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void stringConcatenation() {
        Object result = engine.evaluate("\"abc\" + 123", context);
        assertEquals("abc123", result);
    }

    @Test
    void nullCoercion() {
        Object result = engine.evaluate("null === null", context);
        assertEquals(Boolean.TRUE, result);
    }
}

package com.group_finity.mascot.trigger.expr.type;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DefaultTypeCoercion.
 *
 * Assumes Mode is the enum in package:
 *   com.group_finity.mascot.trigger.expr.type.Mode
 */
class DefaultTypeCoercionTest {

    private DefaultTypeCoercion coercion;

    @BeforeEach
    void setUp() {
        coercion = new DefaultTypeCoercion();
    }

    @Test
    void testCoerceToIntegerFromString() {
        Object out = coercion.coerceTo("42", Integer.class, Mode.STRICT);
        assertTrue(out instanceof Integer, "Expected Integer result");
        assertEquals(42, ((Integer) out).intValue());
    }

    @Test
    void testCoerceToIntegerFromDouble() {
        Object out = coercion.coerceTo(3.7, Integer.class, Mode.STRICT);
        assertTrue(out instanceof Integer, "Expected Integer result");
        assertEquals(3, ((Integer) out).intValue());
    }

    @Test
    void testCoerceToDoubleFromString() {
        Object out = coercion.coerceTo("42", Double.class, Mode.STRICT);
        assertTrue(out instanceof Double, "Expected Double result");
        assertEquals(42.0, ((Double) out).doubleValue(), 1e-9);
    }

    @Test
    void testCoerceToDoubleFromFloat() {
        Object out = coercion.coerceTo(3.5f, Double.class, Mode.STRICT);
        assertTrue(out instanceof Double, "Expected Double result");
        assertEquals(3.5, ((Double) out).doubleValue(), 1e-9);
    }

    @Test
    void testCoerceToBooleanFromStringAndNumber() {
        Object b1 = coercion.coerceTo("true", Boolean.class, Mode.STRICT);
        assertTrue(b1 instanceof Boolean && (Boolean)b1);

        Object b2 = coercion.coerceTo("false", Boolean.class, Mode.STRICT);
        assertTrue(b2 instanceof Boolean && !((Boolean)b2));

        Object b3 = coercion.coerceTo(1, Boolean.class, Mode.STRICT);
        assertTrue(b3 instanceof Boolean && (Boolean)b3);

        Object b4 = coercion.coerceTo(0, Boolean.class, Mode.STRICT);
        assertTrue(b4 instanceof Boolean && !((Boolean)b4));
    }

    @Test
    void testCoerceToString() {
        Object s1 = coercion.coerceTo("hello", String.class, Mode.STRICT);
        assertEquals("hello", s1);

        Object s2 = coercion.coerceTo(123, String.class, Mode.STRICT);
        assertEquals("123", s2);
    }

    @Test
    void testAlreadyCorrectTypeIsReturned() {
        Integer original = Integer.valueOf(99);
        Object ret = coercion.coerceTo(original, Integer.class, Mode.STRICT);
        // Some implementations may return same instance for boxed types; assert value equality at least
        assertEquals(original, ret);
    }

    @Test
    void testCannotCoerceThrows() {
        assertThrows(CoercionException.class, () -> {
            coercion.coerceTo(new Object(), Double.class, Mode.STRICT);
        });
    }

    @Test
    void testNullInputReturnsNullForWrapperInStrict() {
        // Based on the simple implementation that returns null for null input
        Object out = coercion.coerceTo(null, Integer.class, Mode.STRICT);
        assertNull(out);
    }
}

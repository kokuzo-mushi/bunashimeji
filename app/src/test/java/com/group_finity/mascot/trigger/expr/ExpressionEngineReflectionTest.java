package com.group_finity.mascot.trigger.expr;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reflection-based tests for ExpressionEngine + EvaluationContext.
 *
 * This test avoids compile-time dependency on EvaluationContext's exact API by:
 *  - creating EvaluationContext instances via reflection (trying multiple ctor shapes),
 *  - setting variables via reflection (trying getVariables(), setVariable(...), or direct field),
 *  - invoking ExpressionEngine.evaluate(...) via reflection.
 *
 * The goal is to validate parsing/evaluation behavior even if EvaluationContext constructors/methods differ.
 */
public class ExpressionEngineReflectionTest {

    private Class<?> engineClass;
    private Object engineInstance;
    private Method evaluateMethod; // reflective evaluate(String, EvaluationContext) or similar

    @BeforeEach
    void setUp() throws Exception {
        // load ExpressionEngine class and create instance via no-arg constructor
        engineClass = Class.forName("com.group_finity.mascot.trigger.expr.ExpressionEngine");
        try {
            Constructor<?> noArg = engineClass.getDeclaredConstructor();
            noArg.setAccessible(true);
            engineInstance = noArg.newInstance();
        } catch (NoSuchMethodException e) {
            // try other ctors if necessary (not expected)
            Constructor<?>[] ctors = engineClass.getDeclaredConstructors();
            if (ctors.length > 0) {
                ctors[0].setAccessible(true);
                // use nulls for params (best-effort)
                Object[] params = new Object[ctors[0].getParameterCount()];
                engineInstance = ctors[0].newInstance(params);
            } else {
                fail("No suitable constructor found for ExpressionEngine");
            }
        }

        // find evaluate method: prefer (String, EvaluationContext), else any (String, Object)
        Method found = null;
        for (Method m : engineClass.getMethods()) {
            if (m.getName().equals("evaluate") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == String.class) {
                found = m;
                break;
            }
        }
        if (found == null) {
            // try declared methods as fallback
            for (Method m : engineClass.getDeclaredMethods()) {
                if (m.getName().equals("evaluate") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == String.class) {
                    found = m;
                    found.setAccessible(true);
                    break;
                }
            }
        }
        evaluateMethod = found;
        if (evaluateMethod == null) {
            fail("Could not find evaluate(String, ...) method on ExpressionEngine");
        }
    }

    /**
     * Helper: create an EvaluationContext instance using several fallbacks.
     */
    private Object makeEvaluationContext() throws Exception {
        Class<?> ctxClass = Class.forName("com.group_finity.mascot.trigger.expr.eval.EvaluationContext");

        // 1) try no-arg ctor
        try {
            Constructor<?> c = ctxClass.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException ignored) { }

        // 2) try Map<String,Object> constructor
        try {
            Constructor<?> c = ctxClass.getDeclaredConstructor(Map.class);
            c.setAccessible(true);
            return c.newInstance(new HashMap<String, Object>());
        } catch (NoSuchMethodException ignored) { }

        // 3) try static factory methods: of(Map), create(Map)
        for (Method m : ctxClass.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1 && Map.class.isAssignableFrom(params[0])) {
                    return m.invoke(null, new HashMap<String, Object>());
                }
            }
        }

        // 4) try any accessible constructor with parameters, fill with nulls/defaults
        for (Constructor<?> c : ctxClass.getDeclaredConstructors()) {
            c.setAccessible(true);
            Object[] args = new Object[c.getParameterCount()];
            for (int i = 0; i < args.length; i++) args[i] = getDefaultForType(c.getParameterTypes()[i]);
            return c.newInstance(args);
        }

        throw new RuntimeException("Unable to instantiate EvaluationContext");
    }

    /** Helper: attempt to set a variable in the context via multiple strategies. */
    private boolean setContextVariable(Object ctx, String name, Object value) {
        Class<?> ctxClass = ctx.getClass();

        // A) try getVariables().put(name, value)
        try {
            Method getVars = ctxClass.getMethod("getVariables");
            Object vars = getVars.invoke(ctx);
            if (vars instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) vars;
                map.put(name, value);
                return true;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }

        // B) try setVariable(name, value) or putVariable(name, value)
        String[] tryNames = new String[] { "setVariable", "putVariable", "set", "put" };
        for (String nm : tryNames) {
            try {
                Method m = findMethodIgnoreTypes(ctxClass, nm, 2);
                if (m != null) {
                    m.invoke(ctx, name, value);
                    return true;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) { }
        }

        // C) try direct field "variables" or "vars"
        String[] fieldNames = new String[] { "variables", "vars", "values" };
        for (String fn : fieldNames) {
            try {
                Field f = ctxClass.getDeclaredField(fn);
                f.setAccessible(true);
                Object fld = f.get(ctx);
                if (fld == null) {
                    // try to set a new map
                    Map<String, Object> map = new HashMap<>();
                    map.put(name, value);
                    if (f.getType().isAssignableFrom(Map.class)) {
                        f.set(ctx, map);
                        return true;
                    }
                } else if (fld instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) fld;
                    map.put(name, value);
                    return true;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) { }
        }

        // D) give up
        return false;
    }

    /** Find a method by name with specified param count (loose matching) */
    private Method findMethodIgnoreTypes(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /** Provide a default value for constructor args when instantiating via reflection */
    private Object getDefaultForType(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == byte.class) return (byte) 0;
        if (t == short.class) return (short) 0;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == char.class) return '\0';
        return null;
    }

    @Test
    void testSimpleArithmeticAndVariables() throws Exception {
        Object ctx = makeEvaluationContext();
        boolean setOk1 = setContextVariable(ctx, "x", 10L);
        boolean setOk2 = setContextVariable(ctx, "y", 5L);

        if (!setOk1 || !setOk2) {
            fail("Could not set variables on EvaluationContext using any known strategy");
        }

        // invoke evaluate("x * y + 2", ctx) reflectively
        Object res = evaluateReflectively("x * y + 2", ctx);
        assertNotNull(res, "evaluate returned null");
        if (res instanceof Number) {
            long v = ((Number) res).longValue();
            assertEquals(52L, v);
        } else {
            fail("Unexpected result type: " + res.getClass());
        }
    }

    @Test
    void testBooleanExpression() throws Exception {
        Object ctx = makeEvaluationContext();
        boolean setOk = setContextVariable(ctx, "flag", false);
        if (!setOk) fail("Could not set variable 'flag'");

        Object res = evaluateReflectively("!flag", ctx);
        assertNotNull(res);
        if (res instanceof Boolean) {
            assertEquals(true, res);
        } else {
            fail("Unexpected result type for boolean expression: " + res.getClass());
        }
    }

    @Test
    void testInvalidExpressionThrowsOrReturnsFalse() throws Exception {
        Object ctx = makeEvaluationContext();
        try {
            Object res = evaluateReflectively("1 + * 2", ctx);
            // some implementations return Boolean.FALSE on parse error; accept either exception or Boolean.FALSE
            if (res instanceof Boolean) {
                assertEquals(Boolean.FALSE, res);
            } else {
                fail("Invalid expression did not throw and did not return Boolean.FALSE; returned: " + res);
            }
        } catch (InvocationTargetException ite) {
            // underlying engine threw — acceptable
            Throwable cause = ite.getCause();
            assertNotNull(cause);
        }
    }

    /* Helper to call engine.evaluate(String, ctx) reflectively */
    private Object evaluateReflectively(String expression, Object ctx) throws Exception {
        // use evaluateMethod
        evaluateMethod.setAccessible(true);
        return evaluateMethod.invoke(engineInstance, expression, ctx);
    }
}

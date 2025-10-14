package com.group_finity.mascot.trigger.expr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

public class ExprTriggerAdvancedTest {

    private boolean check(String expr, Map<String, Object> vars) {
        ExprTrigger trigger = new ExprTrigger(expr);
        EvaluationContext ctx = new EvaluationContext(vars);
        return trigger.check(ctx);
    }
    
    private boolean evaluate(String expr) {
        var vars = new java.util.HashMap<String, Object>();
        var ctx = new com.group_finity.mascot.trigger.expr.eval.EvaluationContext(
            vars,
            new com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion(),
            com.group_finity.mascot.trigger.expr.type.Mode.STRICT
        );

        // ✅ パッケージ修正 + parse() 経由
        var parser = new com.group_finity.mascot.trigger.expr.parser.ExpressionParser(expr);
        var node = parser.parse();

        Object result = node.evaluate(ctx);

        if (result instanceof Boolean b) {
            return b;
        }
        if (result instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return result != null;
    }


    @Test
    public void testArithmeticAndComparison() {
        assertTrue(check("1 + 2 * 3 == 7", Map.of()));
        assertTrue(check("(2 + 3) * 4 == 20", Map.of()));
        assertTrue(check("5 > 3 && 2 < 4", Map.of()));
        assertTrue(check("10 / 2 == 5", Map.of()));
        assertTrue(check("10 % 4 == 2", Map.of()));
    }

    @Test
    public void testStringEqualityAndInequality() {
        assertTrue(check("\"hello\" == \"hello\"", Map.of()));
        assertTrue(check("\"hello\" != \"world\"", Map.of()));
        assertTrue(check("state == \"jump\"", Map.of("state", "jump")));
        assertFalse(check("state == \"fall\"", Map.of("state", "jump")));
    }

    @Test
    public void testBooleanLogic() {
        assertTrue(check("true && true", Map.of()));
        assertFalse(check("true && false", Map.of()));
        assertTrue(check("true || false", Map.of()));
        assertFalse(check("!true && false", Map.of()));
        assertTrue(check("!(false || false)", Map.of()));
    }

    @Test
    public void testUnaryOperations() {
        assertTrue(check("-5 == 0 - 5", Map.of()));
        assertTrue(check("!false", Map.of()));
        assertFalse(check("!true", Map.of()));
        assertTrue(evaluate("-5 === -5"));
        assertTrue(evaluate("-(5) === -5"));
        assertTrue(evaluate("-(2 + 3) === -5"));
        assertTrue(evaluate("+5 === 5"));
        assertTrue(evaluate("+3.5 === 3.5"));
        assertTrue(evaluate("!false === true"));
        assertTrue(evaluate("!true === false"));

    }

    @Test
    public void testVariablesAndCoercion() {
        Map<String, Object> vars = Map.of(
            "a", 5,
            "b", 10.0,
            "flag", true,
            "name", "shimeji"
        );

        assertTrue(check("a + b == 15", vars));
        assertTrue(check("flag == true", vars));
        assertTrue(check("name == \"shimeji\"", vars));
        assertFalse(check("name == \"mushroom\"", vars));
        assertTrue(check("a < b && flag", vars));
    }

    @Test
    public void testNestedParentheses() {
        assertTrue(check("((1 + 2) * (3 + 4)) == 21", Map.of()));
        assertFalse(check("(2 + 3) * (4 + 5) == 40", Map.of()));
        assertTrue(check("!( (1 < 0) || (2 > 1 && 3 == 3) ) == false", Map.of()));
    }

    @Test
    public void testComplexMixedExpression() {
        Map<String, Object> vars = Map.of(
            "hp", 80,
            "maxHp", 100,
            "state", "idle",
            "isAlive", true
        );

        // (hp / maxHp < 1.0 && isAlive) || state == "jump"
        assertTrue(check("(hp / maxHp < 1.0 && isAlive) || state == \"jump\"", vars));

        vars = Map.of("hp", 100, "maxHp", 100, "state", "dead", "isAlive", false);
        assertFalse(check("(hp / maxHp < 1.0 && isAlive) || state == \"jump\"", vars));
    }
}

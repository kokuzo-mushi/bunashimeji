package com.group_finity.mascot.trigger.expr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * ExprTrigger の基本テスト。
 */
public class ExprTriggerTest {

    @Test
    void testSimpleTrueCondition() {
        Map<String, Object> vars = Map.of(
            "velocityY", 2.0,
            "onGround", false
        );
        EvaluationContext ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        ExprTrigger trigger = new ExprTrigger("velocityY > 0 && onGround == false");

        assertTrue(trigger.check(ctx), "条件が真として評価されるべき");
    }

    @Test
    void testSimpleFalseCondition() {
        Map<String, Object> vars = Map.of(
            "velocityY", -1.0,
            "onGround", true
        );
        EvaluationContext ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        ExprTrigger trigger = new ExprTrigger("velocityY > 0 && onGround == false");

        assertFalse(trigger.check(ctx), "条件が偽として評価されるべき");
    }

    @Test
    void testStringCondition() {
        Map<String, Object> vars = Map.of(
            "state", "jump"
        );
        EvaluationContext ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);

        ExprTrigger trigger = new ExprTrigger("state == \"jump\"");

        assertTrue(trigger.check(ctx));
    }
    
    @Test
    void testParentheses() {
        Map<String,Object> vars = Map.of("a", 2, "b", 3, "c", 4);
        EvaluationContext ctx = new EvaluationContext(vars, new DefaultTypeCoercion(), Mode.STRICT);
        ExprTrigger t1 = new ExprTrigger("(a + b) * c == 20"); // (2+3)*4 == 20
        assertTrue(t1.check(ctx));
    }

}

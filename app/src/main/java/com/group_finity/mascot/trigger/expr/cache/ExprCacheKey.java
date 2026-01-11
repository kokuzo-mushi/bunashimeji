package com.group_finity.mascot.trigger.expr.cache;

import com.group_finity.mascot.trigger.expr.ast.Expression;
import com.group_finity.mascot.trigger.expr.type.Mode;
import java.util.Objects;

public class ExprCacheKey {
    private final Expression ast;
    private final Mode mode;

    private ExprCacheKey(Expression ast, Mode mode) {
        this.ast = ast;
        this.mode = mode;
    }

    public static ExprCacheKey ofAst(Expression ast, Mode mode) {
        return new ExprCacheKey(ast, mode);
    }

    public Expression getAst() {
        return ast;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExprCacheKey that = (ExprCacheKey) o;
        return Objects.equals(ast, that.ast) && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ast, mode);
    }
}
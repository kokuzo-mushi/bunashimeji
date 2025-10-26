package com.group_finity.mascot.trigger.expr.cache;

import java.util.Objects;

import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * AST と Mode に基づくキャッシュキー（D-5 安定版）
 * - ofAst(...) を提供（toCanonicalString() が無い環境でも動く）
 * - equals/hashCode は文字列表現＋Mode のみ（identityHashCode 不使用）
 */
public final class ExprCacheKey {

    private final String canonicalAst;
    private final Mode mode;

    public ExprCacheKey(String canonicalAst, Mode mode) {
        this.canonicalAst = canonicalAst;
        this.mode = mode;
    }

    public String getCanonicalAst() { return canonicalAst; }
    public Mode getMode() { return mode; }

    /** AST+Mode からキー生成。toCanonicalString() が無い場合は toString() を使う。 */
    public static ExprCacheKey ofAst(ExpressionNode node, Mode mode) {
        if (node == null || mode == null) {
            throw new IllegalArgumentException("node or mode is null");
        }
        String rep;
        try {
            // toCanonicalString が無ければ例外に飛ぶ → toString() にフォールバック
            rep = node.getClass().getMethod("toCanonicalString").invoke(node).toString();
        } catch (Throwable ignore) {
            rep = String.valueOf(node); // node.toString()
        }
        return new ExprCacheKey(rep, mode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExprCacheKey)) return false;
        ExprCacheKey other = (ExprCacheKey) o;
        return Objects.equals(canonicalAst, other.canonicalAst) && mode == other.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalAst, mode);
    }

    @Override
    public String toString() {
        return "ExprCacheKey[" + canonicalAst + "," + mode + "]";
    }
}

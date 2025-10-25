package com.group_finity.mascot.trigger.expr.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.type.Mode;

/**
 * Unique key representing a specific expression AST + context dependency snapshot.
 * Does not rely on node-specific getters (uses toString() and class names).
 */
public final class ExprCacheKey {

    private final String astHash;
    private final String contextHash;
    private final Mode mode;
    private final int combinedHash;

    private ExprCacheKey(String astHash, String contextHash, Mode mode) {
        this.astHash = astHash;
        this.contextHash = contextHash;
        this.mode = mode;
        this.combinedHash = Objects.hash(astHash, contextHash, mode);
    }

    public static ExprCacheKey of(ExpressionNode ast, Map<String, Object> deps, Mode mode) {
        String astHash = computeAstHash(ast);
        String ctxHash = computeContextHash(deps);
        return new ExprCacheKey(astHash, ctxHash, mode);
    }

    /** === AST構造のハッシュ生成（getter非依存） === */
    private static String computeAstHash(ExpressionNode ast) {
        try {
            String canonical = (ast != null)
                ? buildAstString(ast)
                : "null";
            return sha1(canonical);
        } catch (Exception e) {
            return "ast-error";
        }
    }

    /**
     * ExpressionNode の構造を再帰的に文字列化。
     * 型名 + toString() + identityHashCode により安定的ハッシュを生成。
     */
    private static String buildAstString(ExpressionNode node) {
        if (node == null) return "null";
        StringBuilder sb = new StringBuilder(node.getClass().getSimpleName());

        // toString() 出力を安全に利用
        sb.append('(')
          .append(safeString(node.toString()))
          .append('@')
          .append(Integer.toHexString(System.identityHashCode(node)))
          .append(')');

        return sb.toString();
    }

    private static String safeString(String s) {
        return (s == null) ? "null" : s.replaceAll("\\s+", "");
    }

    /** === Contextハッシュ生成 === */
    private static String computeContextHash(Map<String, Object> deps) {
        String joined = deps.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + normalizeValue(e.getValue()))
                .collect(Collectors.joining(";"));
        return sha1(joined);
    }

    private static String normalizeValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number num)
            return new java.math.BigDecimal(num.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Boolean b) return b ? "1" : "0";
        if (value instanceof Collection<?> col)
            return col.stream().map(ExprCacheKey::normalizeValue).collect(Collectors.joining(","));
        return value.toString().trim();
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExprCacheKey other)) return false;
        return combinedHash == other.combinedHash &&
                astHash.equals(other.astHash) &&
                contextHash.equals(other.contextHash) &&
                mode == other.mode;
    }

    @Override
    public int hashCode() {
        return combinedHash;
    }

    public String astHash() { return astHash; }
    public String contextHash() { return contextHash; }
    public Mode getMode() { return mode; }
}

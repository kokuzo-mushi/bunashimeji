package com.group_finity.mascot.trigger.expr.type;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation: rules as discussed earlier.
 */
public final class DefaultTypeResolver implements TypeResolver {

    private final ConcurrentMap<Key, CoercionPlan> cache = new ConcurrentHashMap<>();

    @Override
    public CoercionPlan resolve(String operator, Class<?> leftType, Class<?> rightType, Mode mode) {
        Key key = new Key(operator, leftType, rightType, mode);
        return cache.computeIfAbsent(key, k -> computePlan(operator, leftType, rightType, mode));
    }

    private CoercionPlan computePlan(String op, Class<?> lType, Class<?> rType, Mode mode) {
        TypeKind l = kindOf(lType);
        TypeKind r = kindOf(rType);

        if (isUnary(op)) {
            if ("!".equals(op)) {
                return new CoercionPlan(Boolean.class, Boolean.class, Boolean.class);
            }
        }

        if (isArithmetic(op)) {
            if (l == TypeKind.NUMBER && r == TypeKind.NUMBER) {
                return new CoercionPlan(Double.class, Double.class, Double.class);
            }
            if ("+".equals(op) && (l == TypeKind.STRING || r == TypeKind.STRING)) {
                return new CoercionPlan(String.class, String.class, String.class);
            }
            if (mode != Mode.STRICT && (canBeNumber(lType) && canBeNumber(rType))) {
                return new CoercionPlan(Double.class, Double.class, Double.class);
            }
            throw new IllegalArgumentException("Operator " + op + " not applicable to " + l + " and " + r);
        }

        if (isComparison(op)) {
            if (l == TypeKind.NUMBER && r == TypeKind.NUMBER) {
                return new CoercionPlan(Double.class, Double.class, Boolean.class);
            }
            if (mode != Mode.STRICT && (canBeNumber(lType) && canBeNumber(rType))) {
                return new CoercionPlan(Double.class, Double.class, Boolean.class);
            }
            throw new IllegalArgumentException("Comparison requires numeric operands");
        }

        if ("==".equals(op) || "!=".equals(op)) {
            if (Objects.equals(lType, rType)) {
                return new CoercionPlan(Object.class, Object.class, Boolean.class);
            }
            if (bothAreNumbers(lType, rType)) {
                return new CoercionPlan(Double.class, Double.class, Boolean.class);
            }
            if (mode != Mode.STRICT) {
                return new CoercionPlan(String.class, String.class, Boolean.class);
            }
            return new CoercionPlan(Object.class, Object.class, Boolean.class);
        }

        if (isLogical(op)) {
            return new CoercionPlan(Boolean.class, Boolean.class, Boolean.class);
        }

        throw new IllegalArgumentException("Unknown operator: " + op);
    }

    private boolean isArithmetic(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op) || "%".equals(op);
    }

    private boolean isComparison(String op) {
        return "<".equals(op) || ">".equals(op) || "<=".equals(op) || ">=".equals(op);
    }

    private boolean isLogical(String op) {
        return "&&".equals(op) || "||".equals(op);
    }

    private boolean isUnary(String op) {
        return "!".equals(op);
    }

    private boolean bothAreNumbers(Class<?> a, Class<?> b) {
        return (a != null && Number.class.isAssignableFrom(a)) && (b != null && Number.class.isAssignableFrom(b));
    }

    private boolean canBeNumber(Class<?> cls) {
        if (cls == null) return true;
        if (Number.class.isAssignableFrom(cls)) return true;
        if (cls == String.class) return true;
        if (cls == Boolean.class || cls == boolean.class) return true;
        return false;
    }

    private TypeKind kindOf(Class<?> cls) {
        if (cls == null) return TypeKind.NULL;
        if (Number.class.isAssignableFrom(cls) || (cls.isPrimitive() && cls != boolean.class && cls != char.class)) return TypeKind.NUMBER;
        if (Boolean.class == cls || boolean.class == cls) return TypeKind.BOOLEAN;
        if (String.class == cls) return TypeKind.STRING;
        return TypeKind.OBJECT;
    }

    private static final class Key {
        private final String op;
        private final Class<?> l;
        private final Class<?> r;
        private final Mode m;
        Key(String op, Class<?> l, Class<?> r, Mode m) { this.op = op; this.l = l; this.r = r; this.m = m; }
        @Override public int hashCode() { return (op.hashCode()*31 + (l != null ? l.hashCode():0))*31 + (r != null ? r.hashCode():0); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key)o;
            return op.equals(k.op) && java.util.Objects.equals(l,k.l) && java.util.Objects.equals(r,k.r) && m == k.m;
        }
    }
}

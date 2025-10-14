package com.group_finity.mascot.trigger.expr.type;

import java.util.Objects;

public final class CoercionPlan {
    private final Class<?> leftTarget;
    private final Class<?> rightTarget;
    private final Class<?> resultType;

    public CoercionPlan(Class<?> leftTarget, Class<?> rightTarget, Class<?> resultType) {
        this.leftTarget = leftTarget;
        this.rightTarget = rightTarget;
        this.resultType = resultType;
    }

    public Class<?> leftTarget() { return leftTarget; }
    public Class<?> rightTarget() { return rightTarget; }
    public Class<?> resultType() { return resultType; }

    @Override
    public String toString() {
        return "CoercionPlan[left=" + leftTarget.getSimpleName() + ", right=" + rightTarget.getSimpleName() + ", result=" + resultType.getSimpleName() + "]";
    }

    @Override
    public int hashCode() { return Objects.hash(leftTarget, rightTarget, resultType); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoercionPlan)) return false;
        CoercionPlan p = (CoercionPlan)o;
        return Objects.equals(leftTarget, p.leftTarget) && Objects.equals(rightTarget, p.rightTarget) && Objects.equals(resultType, p.resultType);
    }
}

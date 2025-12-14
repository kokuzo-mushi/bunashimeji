package com.group_finity.mascot.trigger.expr.ast;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import java.lang.reflect.Method;
import java.util.Map;

public class MemberAccessExpression implements Expression {
    private final Expression target;
    private final String memberName;

    public MemberAccessExpression(Expression target, String memberName) {
        this.target = target;
        this.memberName = memberName;
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        Object targetVal = target.evaluate(context);
        if (targetVal == null) return null;

        if (targetVal instanceof Map) {
            return ((Map<?, ?>) targetVal).get(memberName);
        }
        
        // リフレクションによるプロパティアクセス (Getterメソッド優先)
        try {
            Class<?> clazz = targetVal.getClass();
            String capitalized = memberName.substring(0, 1).toUpperCase() + memberName.substring(1);
            
            // 1. getMemberName() を試す
            try {
                Method method = clazz.getMethod("get" + capitalized);
                return method.invoke(targetVal);
            } catch (NoSuchMethodException e1) {
                // 2. isMemberName() を試す (boolean用)
                try {
                    Method method = clazz.getMethod("is" + capitalized);
                    return method.invoke(targetVal);
                } catch (NoSuchMethodException e2) {
                    // 3. フィールド直接アクセスは今回は行わない（MascotクラスはGetter完備のため）
                    // 必要であれば clazz.getField(memberName).get(targetVal) を追加
                }
            }
        } catch (Exception e) {
            // アクセス失敗時は null として扱う
        }

        return null;
    }
}
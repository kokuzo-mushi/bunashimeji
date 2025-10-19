package com.group_finity.mascot.trigger.expr.type;

/**
 * デフォルトの TypeResolverFactory。
 * 通常の評価ロジックを提供する。
 */
public class DefaultTypeResolverFactory implements TypeResolverFactory {

    @Override
    public TypeResolver createResolver() {
        return new DefaultTypeResolver();
    }
}

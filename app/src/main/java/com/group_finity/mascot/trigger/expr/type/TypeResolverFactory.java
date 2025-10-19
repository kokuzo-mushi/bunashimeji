package com.group_finity.mascot.trigger.expr.type;

/**
 * TypeResolver の生成戦略を切り替えるためのファクトリ。
 * 
 * これにより、条件式やスクリプトの評価環境ごとに
 * 独自の型解決ロジックを差し込めるようになる。
 */
public interface TypeResolverFactory {

    /**
     * TypeResolver の新しいインスタンスを生成する。
     *
     * @return 新しい TypeResolver
     */
    TypeResolver createResolver();
}

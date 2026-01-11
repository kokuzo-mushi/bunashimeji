package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 設定ファイル (actions.xml, behaviors.xml) を読み込み、
 * アプリケーションで使用する Action と Behavior のリストを構築するクラス。
 */
public class Configuration {

    private final Map<String, Action> actions;
    private final List<Behavior> behaviors;

    public Configuration(Path actionsPath, Path behaviorsPath) {
        // 1. ActionBuilderを使ってアクション定義を読み込む
        ActionBuilder actionBuilder = new ActionBuilder();
        this.actions = actionBuilder.build(actionsPath);

        // 2. BehaviorBuilderを使ってビヘイビア定義を読み込む（アクションとの紐付けを含む）
        BehaviorBuilder behaviorBuilder = new BehaviorBuilder(this.actions);
        this.behaviors = behaviorBuilder.build(behaviorsPath);
    }

    public Map<String, Action> getActions() { return actions; }
    public List<Behavior> getBehaviors() { return behaviors; }
}
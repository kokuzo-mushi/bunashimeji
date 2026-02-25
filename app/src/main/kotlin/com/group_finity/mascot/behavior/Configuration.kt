package com.group_finity.mascot.behavior

import com.group_finity.mascot.action.Action
import com.group_finity.mascot.config.ConfigurationLoader
import java.nio.file.Path

/**
 * 設定ファイル (actions.xml/yaml, behaviors.xml/yaml) を読み込み、 アプリケーションで使用する Action と Behavior
 * のリストを構築するクラス。
 */
class Configuration(actionsPath: Path, behaviorsPath: Path) {

    val actions: Map<String, Action>
    val behaviors: List<Behavior>

    init {
        val configLoader = ConfigurationLoader()

        // 1. アクション定義の読み込み
        actions =
                if (configLoader.isYaml(actionsPath)) {
                    println("[INFO] Configuration: Loading Actions from YAML: $actionsPath")
                    val config = configLoader.loadMascotConfig(actionsPath)
                    val actionBuilder = ActionBuilder()
                    actionBuilder.build(config.actions)
                } else {
                    val actionBuilder = ActionBuilder()
                    actionBuilder.build(actionsPath)
                }

        // 2. ビヘイビア定義の読み込み
        behaviors =
                if (configLoader.isYaml(behaviorsPath)) {
                    println("[INFO] Configuration: Loading Behaviors from YAML: $behaviorsPath")
                    val config = configLoader.loadMascotConfig(behaviorsPath)
                    val behaviorBuilder = BehaviorBuilder(actions)
                    behaviorBuilder.build(config.behaviors)
                } else {
                    val behaviorBuilder = BehaviorBuilder(actions)
                    behaviorBuilder.build(behaviorsPath)
                }
    }
}

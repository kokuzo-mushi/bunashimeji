package com.group_finity.mascot.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * YAML設定ファイルのマッピング用データクラス。
 * 既存のドメインオブジェクト(Action/Behavior)とは分離し、
 * 純粋なデータ転送オブジェクト(DTO)として定義する。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MascotConfig(
    val actions: List<ActionConfig> = emptyList(),
    val behaviors: List<BehaviorConfig> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ActionConfig(
    val name: String,
    val type: String,
    val classPattern: String? = null, // Javaクラス名指定用
    // パラメータは任意のキーバリューを持つ可能性がある
    val params: Map<String, Any> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BehaviorConfig(
    val name: String,
    val frequency: Int,
    val conditions: List<String> = emptyList(),
    @JsonProperty("next") val nextBehavior: String? = null,
    val actions: List<String> = emptyList() // 実行するアクション名のリスト
)
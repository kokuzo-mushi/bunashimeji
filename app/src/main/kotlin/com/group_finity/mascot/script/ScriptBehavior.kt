package com.group_finity.mascot.script

import org.graalvm.polyglot.Value

/**
 * JavaScript の Generator 関数を制御し、マスコットの振る舞いを実行するクラス。
 * フレーム毎に tick() を呼び出すことで、スクリプトを少しずつ進めることができる。
 */
class ScriptBehavior(
    private val engine: ScriptEngine,
    private val mascot: Any, // テスト容易性のため Any としているが、実際は Mascot 型を渡す
    private val behaviorName: String
) {
    private var iterator: Value? = null
    private var waitFrames = 0
    private var finished = false

    init {
        // マスコットをJSのグローバル変数として公開
        // 注意: 複数のマスコットがいる場合、Contextを分ける必要がある (今後の課題)
        engine.put("mascot", mascot)
        start()
    }

    private fun start() {
        // Generator関数を呼び出してイテレータを取得
        iterator = engine.invokeFunction(behaviorName)
    }

    fun tick() {
        if (finished) return

        // 待機中の場合、カウントダウンして処理をスキップ
        if (waitFrames > 0) {
            waitFrames--
            return
        }

        val iter = iterator ?: return
        val nextMethod = iter.getMember("next")

        // JS実行 (次の yield まで)
        val result = nextMethod.execute()
        
        if (result.getMember("done").asBoolean()) {
            finished = true
        } else {
            val value = result.getMember("value")
            // yield された値が数値なら、そのフレーム数だけ待機する
            if (value.isNumber) {
                waitFrames = value.asInt()
            }
        }
    }

    fun isFinished(): Boolean = finished
}
package com.group_finity.mascot.script

import org.graalvm.polyglot.Engine

/**
 * GraalJS Engine を管理するシングルトンオブジェクト。 複数の ScriptEngine (Context) で Engine を共有することで、
 * JITコンパイル結果やコードキャッシュを共有し、メモリ効率とパフォーマンスを向上させる。
 */
object ScriptEngineManager {
    private val engine: Engine =
            Engine.newBuilder("js")
                    // インタプリタモード実行時の警告を抑制
                    .option("engine.WarnInterpreterOnly", "false")
                    .build()

    /** 共有エンジンを使用して新しい ScriptEngine インスタンスを作成する。 */
    fun createEngine(): ScriptEngine {
        return ScriptEngine(engine)
    }

    /** 変数を初期化した状態で ScriptEngine を作成する。 */
    fun createMascotContext(variables: Map<String, Any>): ScriptEngine {
        val scriptEngine = createEngine()
        variables.forEach { (key, value) -> scriptEngine.put(key, value) }
        return scriptEngine
    }

    fun close() {
        engine.close()
    }

    /** ファイルからスクリプトを読み込んでコンパイルする。 */
    fun loadScript(path: java.nio.file.Path): org.graalvm.polyglot.Source {
        val content = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8)
        return org.graalvm.polyglot.Source.newBuilder("js", content, path.fileName.toString())
                .mimeType("application/javascript")
                .build()
    }

    /** 文字列コンテンツからスクリプトをコンパイルする。 */
    fun loadScript(content: String, name: String): org.graalvm.polyglot.Source {
        return org.graalvm.polyglot.Source.newBuilder("js", content, name)
                .mimeType("application/javascript")
                .build()
    }
}

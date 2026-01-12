package com.group_finity.mascot.script

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import java.io.Closeable

/**
 * GraalJS をラップし、JavaScript コードを実行するためのエンジン。
 * マスコットの行動ロジック (Behavior) をスクリプトで記述するために使用する。
 */
class ScriptEngine(engine: Engine) : Closeable {
    val context: Context

    init {
        // JSコンテキストの初期化
        context = Context.newBuilder("js")
            .engine(engine)
            // Javaのオブジェクト・クラスへのアクセスを許可 (Interop用)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .build()
    }

    /**
     * JavaScript ソースコードを評価する。
     *
     * @param script 実行するJavaScriptコード
     * @return 実行結果 (Value)
     */
    fun evaluate(script: String): Value {
        return context.eval("js", script)
    }

    /**
     * Java オブジェクトを JavaScript のグローバル変数として公開する。
     *
     * @param name JS側での変数名
     * @param value 公開するオブジェクト
     */
    fun put(name: String, value: Any) {
        context.getBindings("js").putMember(name, value)
    }

    /**
     * グローバルスコープに定義された関数を実行する。
     *
     * @param name 関数名
     * @param args 引数
     * @return 実行結果 (Value)
     */
    fun invokeFunction(name: String, vararg args: Any): Value {
        val bindings = context.getBindings("js")
        return bindings.getMember(name).execute(*args)
    }

    override fun close() {
        context.close()
    }
}
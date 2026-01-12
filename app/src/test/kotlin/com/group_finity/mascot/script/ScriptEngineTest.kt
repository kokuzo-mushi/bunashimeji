package com.group_finity.mascot.script

import kotlin.jvm.JvmField
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScriptEngineTest {
    private lateinit var engine: ScriptEngine

    @BeforeEach
    fun setUp() {
        engine = ScriptEngineManager.createEngine()
    }

    @AfterEach
    fun tearDown() {
        engine.close()
    }

    @Test
    fun testEvaluateMath() {
        // 基本的な演算の確認
        val result = engine.evaluate("1 + 1")
        assertEquals(2, result.asInt())
    }

    @Test
    fun testEvaluateString() {
        // 文字列操作の確認
        val result = engine.evaluate("'Hello ' + 'World'")
        assertEquals("Hello World", result.asString())
    }

    @Test
    fun testJavaClassAccess() {
        // Javaの標準クラスにアクセスできるか確認
        // ScriptEngine で .allowHostClassLookup { true } が設定されているため成功するはず
        val result = engine.evaluate("java.lang.Math.max(10, 20)")
        assertEquals(20, result.asInt())
    }

    // テスト用のモッククラス (Java Interop確認用)
    // GraalJSからアクセスするためには public である必要があります
    class MockMascot {
        @JvmField var x: Int = 0
        @JvmField var y: Int = 0

        fun move(dx: Int, dy: Int) {
            x += dx
            y += dy
        }
    }

    @Test
    fun testJavaObjectInterop() {
        val mascot = MockMascot()

        // JavaオブジェクトをJSに渡す
        engine.put("mascot", mascot)

        // JS側でプロパティ操作とメソッド呼び出しを行う
        engine.evaluate("mascot.x = 100;")
        engine.evaluate("mascot.move(50, 10);")

        // 結果がJavaオブジェクトに反映されているか確認
        assertEquals(150, mascot.x)
        assertEquals(10, mascot.y)
    }

    @Test
    fun testGeneratorExecution() {
        // Generator関数 (コルーチン) を定義
        // yield で値を返しつつ処理を中断する
        engine.evaluate("""
            function* behavior() {
                yield 10;
                yield 20;
                return 30;
            }
        """.trimIndent())

        // 関数を呼び出してイテレータを取得
        val iterator = engine.invokeFunction("behavior")

        // 1回目の yield
        var next = iterator.getMember("next").execute()
        assertEquals(10, next.getMember("value").asInt())
        assertEquals(false, next.getMember("done").asBoolean())

        // 2回目の yield
        next = iterator.getMember("next").execute()
        assertEquals(20, next.getMember("value").asInt())
        assertEquals(false, next.getMember("done").asBoolean())

        // 完了 (return)
        next = iterator.getMember("next").execute()
        assertEquals(30, next.getMember("value").asInt())
        assertEquals(true, next.getMember("done").asBoolean())
    }

    @Test
    fun testScriptBehavior() {
        val mascot = MockMascot()

        // 動作確認用のスクリプト
        // 1. xを10進める
        // 2. 2フレーム待機 (yield 2)
        // 3. yを20進める
        engine.evaluate("""
            function* myBehavior() {
                mascot.move(10, 0);
                yield 2;
                mascot.move(0, 20);
            }
        """.trimIndent())

        val behavior = ScriptBehavior(engine, mascot, "myBehavior")

        // 1フレーム目: x+=10 が実行され、yield 2 で待機開始
        behavior.tick()
        assertEquals(10, mascot.x)
        assertEquals(0, mascot.y)
        assertEquals(false, behavior.isFinished())

        // 2フレーム目: 待機中 (残り1)
        behavior.tick()
        assertEquals(10, mascot.x) // 変化なし

        // 3フレーム目: 待機中 (残り0) -> 次回実行可能
        behavior.tick()
        assertEquals(10, mascot.x) // 変化なし

        // 4フレーム目: 再開 -> y+=20 が実行され、完了
        behavior.tick()
        assertEquals(10, mascot.x)
        assertEquals(20, mascot.y)
        assertEquals(true, behavior.isFinished())
    }
}

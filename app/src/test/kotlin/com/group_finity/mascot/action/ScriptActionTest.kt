package com.group_finity.mascot.action

import com.group_finity.mascot.Mascot
import com.group_finity.mascot.script.ScriptBehavior
import com.group_finity.mascot.script.ScriptEngineManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.JvmField

class ScriptActionTest {

    // JSから操作されるモックオブジェクト
    // GraalJSからフィールドにアクセスするためには public かつ @JvmField が必要
    class MockMascotModel {
        @JvmField var x: Int = 0
        @JvmField var y: Int = 0

        // JSから mascot.getX() で呼び出せるように public メソッドを追加
        fun getX(): Int {
            return x
        }

        fun setX(x: Int) {
            this.x = x
        }

        fun getY(): Int {
            return y
        }

        fun setY(y: Int) {
            this.y = y
        }
    }

    @Test
    fun testLoadAndExecuteScript(@TempDir tempDir: Path) {
        // 1. テスト用JSファイルの作成
        // 実際にはリソースファイルを使うことが多いが、ここでは動的に生成して loadScript の動作も確認する
        val scriptPath = tempDir.resolve("walk.js")
        Files.writeString(scriptPath, """
            function* walk() {
                mascot.setX(mascot.getX() + 10);
                yield 1;
                mascot.setX(mascot.getX() + 10);
                yield 1;
                mascot.setY(mascot.getY() - 5);
            }
        """.trimIndent())

        // 2. エンジンとモデルの準備
        val engine = ScriptEngineManager.createEngine()
        val model = MockMascotModel()
        
        try {
            // JS側にモデルを公開
            engine.put("mascot", model)

            // スクリプト読み込み
            val source = ScriptEngineManager.loadScript(scriptPath)
            engine.context.eval(source)

            // Behavior と Action の作成
            val behavior = ScriptBehavior(engine, model, "walk")
            val action = ScriptAction(behavior)
            
            // Action.execute に渡すダミーのマスコット
            // ScriptAction は内部で behavior が保持する model を操作するため、
            // execute の引数は使用されないが、型合わせのために Mockito でモックを作成する
            val dummyMascot = mock(Mascot::class.java)

            // --- 実行検証 ---

            // Frame 1: move(10, 0) -> yield 1
            assertTrue(action.hasNext())
            action.execute(dummyMascot)
            assertEquals(10, model.x)
            assertEquals(0, model.y)

            // Frame 2: yield 1 (待機)
            assertTrue(action.hasNext())
            action.execute(dummyMascot)
            assertEquals(10, model.x) // 変化なし

            // Frame 3: move(10, 0) -> yield 1
            assertTrue(action.hasNext())
            action.execute(dummyMascot)
            assertEquals(20, model.x)
            assertEquals(0, model.y)

            // Frame 4: yield 1 (待機)
            assertTrue(action.hasNext())
            action.execute(dummyMascot)
            assertEquals(20, model.x)

            // Frame 5: move(0, -5) -> 終了
            assertTrue(action.hasNext())
            action.execute(dummyMascot)
            assertEquals(20, model.x)
            assertEquals(-5, model.y)
            
            // 終了確認
            assertFalse(action.hasNext())

        } finally {
            engine.close()
        }
    }
}
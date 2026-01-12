package com.group_finity.mascot.script;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaScript Generator を使用した非同期ビヘイビアのテスト。
 */
class GeneratorBehaviorTest {

    private Mascot mascot;
    private ScriptEngine engine;
    private Context context;

    @BeforeEach
    void setUp() {
        // 1. マスコットの初期化
        mascot = new Mascot();
        mascot.setX(0);
        mascot.setY(0);
        mascot.setLookRight(false); // 初期状態は左向き

        // 2. スクリプトエンジンの準備
        Map<String, Object> globals = new HashMap<>();
        globals.put("mascot", mascot);

        engine = ScriptEngineManager.INSTANCE.createMascotContext(globals);
        context = engine.getContext();
        mascot.setJsContext(context);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void testGeneratorBehaviorExecution() throws Exception {
        // 1. ビヘイビアの作成とスクリプトのロード
        Behavior behavior = new Behavior("TestGen", 1, "true");

        // リソースからスクリプトファイルのパスを取得
        URL res = getClass().getResource("/behavior/example_behavior.js");
        Path scriptPath;
        if (res != null) {
            scriptPath = Path.of(res.toURI());
        } else {
            // フォールバック: リソースが見つからない場合、ソースパスを試行
            scriptPath = Path.of("src/test/resources/behavior/example_behavior.js");
        }

        assertTrue(scriptPath.toFile().exists(), "Test script file not found: " + scriptPath.toAbsolutePath());

        behavior.loadScript(scriptPath);

        // 2. アクションのインスタンス化
        Action action = behavior.instantiateAction(mascot);
        assertNotNull(action, "Action should be instantiated from JS generator");

        // --- Frame 1: Turn Right ---
        assertTrue(action.hasNext());
        action.execute(mascot);
        assertTrue(mascot.isLookRight(), "Mascot should look right after 1st yield");
        assertEquals(0, mascot.getX(), "Mascot should not move yet");

        // --- Frame 2-6: Move Right (5 times, +10px each) ---
        for (int i = 1; i <= 5; i++) {
            assertTrue(action.hasNext());
            action.execute(mascot);
            assertEquals(i * 10, mascot.getX(), "Mascot X should increase by 10 each frame");
        }
        assertEquals(50, mascot.getX());

        // --- Frame 7-9: Wait (3 frames) ---
        for (int i = 0; i < 3; i++) {
            assertTrue(action.hasNext());
            action.execute(mascot);
            assertEquals(50, mascot.getX(), "Mascot should wait (no movement)");
        }

        // --- Frame 10: Turn Left ---
        assertTrue(action.hasNext());
        action.execute(mascot);
        assertFalse(mascot.isLookRight(), "Mascot should look left");

        // --- End of Generator ---
        action.execute(mascot); // 最後の実行で done が検出される
        assertFalse(action.hasNext(), "Action should be finished");
    }
}
package com.group_finity.mascot.script;

import com.group_finity.mascot.Mascot;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * GraalJS スクリプトエンジンと Java オブジェクト (Mascot) の連携テスト。
 */
public class ScriptingTest {
    public static void main(String[] args) {
        try {
            System.out.println("=== GraalJS Scripting Test ===");

            // 1. マスコットの準備
            Mascot mascot = new Mascot();
            mascot.setX(100);
            mascot.setY(200);
            System.out.println("Initial Mascot State: X=" + mascot.getX() + ", Y=" + mascot.getY());

            // 2. スクリプトエンジンの準備
            // ScriptEngineManager manager = ScriptEngineManager.getInstance(); // JAVA
            // (Deleted)
            Map<String, Object> globals = new HashMap<>();
            globals.put("mascot", mascot);

            ScriptEngine engine = ScriptEngineManager.INSTANCE.createMascotContext(globals);
            Context context = engine.getContext();

            // 3. テスト用スクリプトファイルの作成
            File scriptFile = new File("test_script.js");
            try (FileWriter writer = new FileWriter(scriptFile)) {
                writer.write("console.log('[JS] Hello from GraalJS!');\n");
                writer.write("console.log('[JS] Current X: ' + mascot.getX());\n");
                writer.write("mascot.setX(mascot.getX() + 50);\n");
                writer.write("mascot.setLookRight(true);\n");
                writer.write("console.log('[JS] New X: ' + mascot.getX());\n");
            }

            // 4. スクリプトの実行
            System.out.println("Executing script...");
            Source source = ScriptEngineManager.INSTANCE.loadScript(scriptFile.toPath());
            context.eval(source);

            // 5. 結果の検証
            System.out.println("Final Mascot State: X=" + mascot.getX() + ", LookRight=" + mascot.isLookRight());

            if (mascot.getX() == 150 && mascot.isLookRight()) {
                System.out.println("SUCCESS: Script modified Java object correctly.");
            } else {
                System.out.println("FAILURE: Script did not modify Java object as expected.");
            }

            // クリーンアップ
            scriptFile.delete();
            engine.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
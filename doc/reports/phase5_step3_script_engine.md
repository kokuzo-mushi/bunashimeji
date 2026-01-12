# Phase 5 Step 3: Scripting Engine Integration 作業レポート

## 1. 概要
Phase 5 のスクリプトエンジン統合フェーズにおいて、`ScriptEngineTest` のコンパイルエラーが発生していました。
調査の結果、`src/main/java` に残存していた旧来の `ScriptEngineManager.java` が、`src/main/kotlin` の新しい `ScriptEngineManager.kt` オブジェクトと競合（シャドウイング）していることが判明しました。
本作業では、Java版の実装を削除し、Kotlin版に機能を統合することで問題を解決しました。

## 2. 実施内容

### 2.1 ScriptEngineManager の統合 (Java削除 / Kotlin強化)
旧実装 (`app/src/main/java/.../ScriptEngineManager.java`) を削除し、不足していた `loadScript` メソッドを新実装 (`app/src/main/kotlin/.../ScriptEngineManager.kt`) に移植しました。

#### [MODIFY] `ScriptEngineManager.kt`
```diff
+    /**
+     * ファイルからスクリプトを読み込んでコンパイルする。
+     */
+    fun loadScript(path: java.nio.file.Path): org.graalvm.polyglot.Source {
+        val content = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8)
+        return org.graalvm.polyglot.Source.newBuilder("js", content, path.fileName.toString())
+            .mimeType("application/javascript")
+            .build()
+    }
```

### 2.2 呼び出し元の修正
`Behavior` クラスおよびテストコードが、Java の `ScriptEngineManager.getInstance()` ではなく、Kotlin の `ScriptEngineManager.INSTANCE` を使用するように修正しました。

#### [MODIFY] `Behavior.java`
```diff
     public void loadScript(Path path) throws IOException {
-        this.scriptSource = ScriptEngineManager.getInstance().loadScript(path);
+        this.scriptSource = ScriptEngineManager.INSTANCE.loadScript(path);
     }
```

#### [MODIFY] `GeneratorBehaviorTest.java` / `BehaviorTest.java`
テストコードも新しい API に適合させました。`Context` を直接取得するのではなく、`ScriptEngine` ラッパーを経由するように変更しています。

```diff
-        manager = ScriptEngineManager.getInstance();
-        context = manager.createMascotContext(globals);
+        engine = ScriptEngineManager.INSTANCE.createMascotContext(globals);
+        context = engine.getContext();
```

### 2.3 ScriptEngine の改修
レガシーコード (`Behavior.java` 等) が GraalJS の生の `Context` オブジェクトを必要としていたため、`ScriptEngine` ラッパークラスにプロパティを公開しました。

#### [MODIFY] `ScriptEngine.kt`
```diff
-    private val context: Context
+    val context: Context
```

## 3. 検証結果
修正後、関連する全てのテストスイートを実行し、正常に動作することを確認しました。

- **対象テスト**: `ScriptEngineTest` (Kotlin), `GeneratorBehaviorTest` (Java), `ScriptingTest` (Java), `BehaviorTest` (Java)
- **コマンド**: `.\gradlew test --tests com.group_finity.mascot.script.* --tests com.group_finity.mascot.behavior.BehaviorTest`
- **結果**: **PASSED** (Exit Code 0)

これにより、Java/Kotlin 混在環境におけるスクリプトエンジンの基盤が統一され、コンパイルエラーが解消されました。

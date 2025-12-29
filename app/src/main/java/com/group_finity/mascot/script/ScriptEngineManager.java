package com.group_finity.mascot.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * GraalJS スクリプトエンジンの管理クラス。
 * 
 * <p>アーキテクチャ:
 * <ul>
 *   <li><b>Shared Engine:</b> JITコンパイル結果やコードキャッシュを共有し、メモリ効率とパフォーマンスを向上させます。</li>
 *   <li><b>Separate Contexts:</b> マスコット個体ごとに Context を分離し、変数のスコープを独立させます。</li>
 *   <li><b>Host Access:</b> Java の特定のクラスやメソッドへのアクセスを許可します。</li>
 * </ul>
 * </p>
 */
public class ScriptEngineManager {

    private static ScriptEngineManager instance;

    // 全インスタンスで共有するエンジン (JIT最適化の共有)
    private final Engine sharedEngine;

    // Java側からJSへのアクセス許可設定
    private final HostAccess hostAccess;

    private ScriptEngineManager() {
        // エンジンの初期化
        this.sharedEngine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false") // 開発中の警告抑制
                .build();

        // ホストアクセスの設定 (アノテーション @HostAccess.Export があるもののみ許可)
        this.hostAccess = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .allowListAccess(true) // 配列やリストへのアクセスを許可
                .allowMapAccess(true)  // Mapへのアクセスを許可 (JSオブジェクトのように扱えるようにする)
                .build();
    }

    public static synchronized ScriptEngineManager getInstance() {
        if (instance == null) {
            instance = new ScriptEngineManager();
        }
        return instance;
    }

    /**
     * マスコット個体用の新しいスクリプトコンテキストを作成します。
     * 
     * @param globalVariables コンテキスト初期化時に注入するグローバル変数 (例: "mascot" -> MascotInstance)
     * @return 初期化された Context
     */
    public Context createMascotContext(Map<String, Object> globalVariables) {
        Context context = Context.newBuilder("js")
                .engine(sharedEngine)
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(className -> {
                    // 許可するJavaクラスのパッケージを制限 (必要に応じて調整)
                    return className.startsWith("com.group_finity.mascot.") ||
                           className.startsWith("java.awt.Point") ||
                           className.startsWith("java.awt.Rectangle");
                })
                .build();

        // グローバル変数の注入
        Value bindings = context.getBindings("js");
        if (globalVariables != null) {
            for (Map.Entry<String, Object> entry : globalVariables.entrySet()) {
                bindings.putMember(entry.getKey(), entry.getValue());
            }
        }

        return context;
    }

    /**
     * ファイルからスクリプトを読み込んでコンパイルします。
     * 結果の Source オブジェクトはキャッシュ可能です。
     */
    public Source loadScript(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return Source.newBuilder("js", content, path.getFileName().toString())
                .mimeType("application/javascript")
                .build();
    }

    /**
     * リソースの解放
     */
    public void shutdown() {
        if (sharedEngine != null) {
            sharedEngine.close();
        }
    }
}
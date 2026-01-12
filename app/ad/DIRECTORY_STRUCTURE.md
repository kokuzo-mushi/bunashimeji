# Shimeji Neo ディレクトリ構造定義

プロジェクトの標準的なディレクトリ構成と、各パッケージの役割定義です。
新規ファイルを作成する際は、この構造に従って配置してください。

## 1. プロジェクトルート構成

```text
c:\bunashimeji\bunashimeji\
├── app\                        # アプリケーションモジュール
│   ├── build.gradle.kts        # ビルド設定
│   ├── src\
│   │   ├── main\
│   │   │   ├── java\           # 【既存】Javaソース (Action, Behavior, Native)
│   │   │   └── kotlin\         # 【新規】Kotlinソース (UI, Config, Extensions)
│   │   └── test\
│   │       ├── java\           # 【既存】Javaテスト
│   │       └── kotlin\         # 【新規】Kotlinテスト (JUnit 5)
├── conf\                       # 設定ファイル (actions.xml, behaviors.xml)
├── img\                        # 画像リソース
└── ...
```

## 2. パッケージ構成と役割 (app/src/main/...)

| パッケージ (`com.group_finity.mascot`) | 言語 | ソースパス | 役割 | 主なクラス |
| :--- | :--- | :--- | :--- | :--- |
| **`.ui`** | Kotlin | `src/main/kotlin` | **UI層 (Compose)**<br>アプリ起動、ウィンドウ描画 | `ShimejiApp.kt`<br>`MascotWindow.kt` |
| **`.config`** | Kotlin | `src/main/kotlin` | **設定・データ変換**<br>ファイル読み込み、XML/YAML変換 | `Configuration.kt`<br>`MascotConfigSchema.kt`<br>`XmlToYamlConverter.kt` |
| **`.nativeaccess`** | Java | `src/main/java` | **ネイティブ連携 (Panama)**<br>OS APIの呼び出し | `NativeWindowUtil.java` |
| **`.behavior`** | Java | `src/main/java` | **ドメインロジック**<br>マスコットの振る舞い構築 | `Behavior.java`<br>`Configuration.java` (旧) |
| **`.action`** | Java | `src/main/java` | **アクション定義**<br>個別の動作ロジック | `Action.java`<br>`WalkAction.java` |
| **`.trigger`** | Java | `src/main/java` | **イベントシステム**<br>トリガー評価 | `EventDispatcher.java` |

## 3. 注意事項

### Configuration クラスの重複について
現在、以下の2つの `Configuration` クラスが存在します。役割が異なるため注意してください。

1.  **`com.group_finity.mascot.config.Configuration` (Kotlin)**
    *   **役割**: 低レイヤーのファイル読み込み。XML/YAMLファイルをパースしてPOJO/Recordを返す。
    *   **場所**: `app/src/main/kotlin/.../config/Configuration.kt`

2.  **`com.group_finity.mascot.behavior.Configuration` (Java)**
    *   **役割**: ドメイン層の構築。読み込んだデータから `Action` や `Behavior` オブジェクトを生成し、依存関係を解決する。
    *   **場所**: `app/src/main/java/.../behavior/Configuration.java`

### テストファイルの配置

*   **Kotlinで書かれたテスト**: `app/src/test/kotlin/...` に配置する。
    *   例: `NativeWindowUtilTest.kt` は `app/src/test/kotlin/com/group_finity/mascot/nativeaccess/` に配置。
*   **Javaで書かれたテスト**: `app/src/test/java/...` に配置する。

---
Last Updated: 2026-01-11
```

### **最近追加したファイルの配置確認**

上記の定義に基づき、直近で作業したファイルの正しいパスは以下の通りです。

*   `app/src/main/kotlin/com/group_finity/mascot/config/XmlToYamlConverter.kt`
*   `app/src/main/kotlin/com/group_finity/mascot/config/MascotConfigSchema.kt`
*   `app/src/main/kotlin/com/group_finity/mascot/config/Configuration.kt`
*   `app/src/test/kotlin/com/group_finity/mascot/config/XmlToYamlConverterTest.kt`
*   `app/src/test/kotlin/com/group_finity/mascot/config/ConfigurationTest.kt`

今後はこの構造を厳守してコードを生成します。

<!--
[PROMPT_SUGGESTION]ディレクトリ構造が整理できたので、XmlToYamlConverterTest を実行して変換ロジックの確認に進みたい[/PROMPT_SUGGESTION]
[PROMPT_SUGGESTION]ShimejiApp.kt で、XMLファイルが存在してYAMLファイルが存在しない場合に、自動的に変換を実行するロジックを追加する[/PROMPT_SUGGESTION]

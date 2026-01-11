# Phase 5 Migration Plan: Swing to JetBrains Compose

**作成日**: 2026-01-10
**ステータス**: 計画策定完了
**ターゲット**: Phase 5 完了 (完全移行)

## 1. 目的
現在の Swing/AWT ベースのエントリーポイント (`Main.java`) を廃止し、PoC で検証された `ComposeLauncher.kt` を正式なアプリケーションのエントリーポイントに昇格させる。これにより、UIフレームワークを JetBrains Compose に統一し、コードベースの近代化を完了させる。

## 2. 現状のギャップ分析 (Gap Analysis)

`Main.java` には存在するが、現在の `ComposeLauncher.kt` に未実装の機能は以下の通り。

| 機能 | Main.java (Swing) | ComposeLauncher.kt (Compose) | 対応方針 |
| :--- | :--- | :--- | :--- |
| **システムトレイ** | `java.awt.SystemTray` | 未実装 | Compose の `Tray` コンポーザブルで再実装する。 |
| **ウィンドウ復帰** | `ThrownWindowInfo` クラスと復帰ループ | 未実装 | ロジックを `WindowRestorationManager` に抽出して移植する。 |
| **アイコン設定** | AWT `setIconImage` | 未実装 | Compose `Window` の `icon` プロパティで設定する。 |
| **終了処理** | `System.exit(0)` | `exitApplication()` | Compose のライフサイクルに合わせて調整する。 |
| **コマンドライン引数** | 未使用 (将来の拡張) | 未対応 | 必要に応じて `main(args)` で受け取る。 |

## 3. 移行ステップ (Migration Steps)

### Step 1: 機能の等価性確保 (Feature Parity)
1.  **システムトレイの実装**:
    -   「増やす」「あつまれ！」「一匹にする」「ばいばい」などのメニューを持つトレイアイコンを実装する。
2.  **ウィンドウ復帰ロジックの移植**:
    -   `Main.java` 内の `ThrownWindowInfo` および `checkAutoRestoreWindows` ロジックを、独立したクラス `WindowRestorationManager` に切り出す。
    -   Compose のメインループ (`LaunchedEffect`) からこのマネージャーを呼び出すようにする。
3.  **アプリケーションアイコンの適用**:
    -   ウィンドウおよびタスクバー、トレイアイコンに `shime1.png` (または専用アイコン) を適用する。

### Step 2: エントリーポイントの切り替え
1.  **ビルド設定の変更**:
    -   `app/build.gradle.kts` の `application { mainClass }` を `com.group_finity.mascot.poc.ComposeLauncherKt` に変更する。
    -   `runPoC` タスクを廃止し、標準の `run` タスクで Compose 版が起動するようにする。

### Step 3: レガシーコードの削除 (Cleanup)
1.  **Swing UI の削除**:
    -   `SettingsWindow.java`, `SkinSelectionWindow.java` を削除（Compose 版に移行済みのため）。
    -   `MascotWindow.java` (Swing実装) を削除、または `MascotView` インターフェースの参照実装としてアーカイブ。
2.  **Main.java の削除**:
    -   全てのロジックが移行されたことを確認後、`Main.java` を削除する。
3.  **パッケージのリネーム**:
    -   `com.group_finity.mascot.poc` パッケージの中身を `com.group_finity.mascot` (ルート) または `com.group_finity.mascot.ui` に移動し、"PoC" の名称を外す。

## 4. リスクと対策

*   **リスク**: `NativeWindowUtil` (Panama) と Compose の描画ループの競合。
    *   **対策**: PoC で検証済みだが、ウィンドウ数が増えた際（50体以上）のパフォーマンスを注視する。必要であれば `Dispatcher.IO` などでスレッドを分離する。
*   **リスク**: Windows 以外のプラットフォームでの挙動。
    *   **対策**: 現状は Windows 専用 (`user32.dll` 依存) だが、Compose 自体はクロスプラットフォームであるため、将来的に `NativeWindowUtil` の Linux/macOS 版実装を差し込むことで対応可能にする。

## 5. 完了条件

- `./gradlew run` で Compose 版アプリケーションが起動すること。
- 既存の Shimeji の全機能（増殖、投擲、設定、トレイ操作）が動作すること。
- `Main.java` がプロジェクトから削除されていること。
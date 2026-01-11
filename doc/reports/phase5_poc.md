# Phase 5 PoC Report: JetBrains Compose Animation Integration

## 概要
Phase 5 の初期概念実証 (PoC) として、JetBrains Compose Multiplatform アプリケーション内での **MascotManager の完全動作** と **アニメーション連携** を検証しました。
Shimeji Neo のコアロジック (`MascotManager`, `Action`, `Behavior`) を Kotlin DSL の Compose アプリケーションに統合し、透過ウィンドウ上でのキャラクターアニメーションを実現しました。

## 実装内容
- **ファイル**: `app/src/main/java/com/group_finity/mascot/poc/ComposeLauncher.kt`
- **アーキテクチャ統合**:
  - `MascotContext`, `Mascot`, `EventDispatcher`, `Configuration`, `ImageCache` を Kotlin 側で初期化。
  - `ComposeMascotAdapter` を実装し、`MascotView` インターフェース経由で `MascotManager` と UI を疎結合に連携。
- **アニメーションループ**:
  - Compose の `LaunchedEffect` 内でメインループを構築 (約60FPS)。
  - `MascotManager.tick()` を呼び出し、物理演算と行動決定を実行。
  - 結果の画像を `ImageBitmap` として Compose の `Image` コンポーザブルに反映。
  - 結果の座標を `NativeWindowUtil` (Project Panama) に渡し、物理ウィンドウ位置を更新。

## 検証結果
- **コンパイル**: 成功 (`gradlew runPoC`)。
- **実行**:
  - アプリケーションが起動し、Project Panama 経由で自身のウィンドウハンドル (HWND) を検出。
  - `MascotManager` が `Action` 遷移 (`StayAction` 等) をログ出力しており、AI ロジックが正常に稼働していることを確認。
  - 画像描画とウィンドウ移動が同期して行われていることを確認。
  - **マウス操作**: マスコットをドラッグして移動し、離すと物理演算で投げ出される挙動 (Grab & Throw) を確認。
  - **マルチウィンドウ**: 右クリックメニューから「増やす」を選択し、新しいマスコットウィンドウが生成され、独立して動作することを確認。
  - **設定画面 (Settings UI)**: ジェットブレインズ Compose で実装された設定画面から、重力・速度・各行動の有効/無効および頻度をリアルタイムに変更できることを確認。
  - **スキン選択 (Skin Selector)**: 起動時および「増やす」選択時に、`img` ディレクトリ内のサブフォルダをスキャンし、プレビュー画像をグリッド表示してスキンを選択できる画面を実装。

## 重要な成果
1.  **UI とロジックの分離実証**: `MascotView` インターフェースを挟むことで、AWT (`MascotWindow`) と Compose (`ComposeMascotAdapter`) の両方で同じ `MascotManager` を駆動できることが証明された。
2.  **Kotlin/Compose への移行パス**: 既存の Java コード資産 (`Action` や `Behavior`) を一切書き換えることなく、新しい UI フレームワークで利用可能であることが確認された。
3.  **インタラクションの拡張性**: Compose の `gestures` モディファイアと AWT のグローバル座標系 (`MouseInfo`) を組み合わせることで、簡単かつ堅牢にウィンドウドラッグを実装できた。
4.  **動的ウィンドウ管理**: Compose の `key` とデータリスト (`SnapshotStateList`) を組み合わせることで、従来の AWT ウィンドウ管理よりも宣言的かつシンプルに「増殖」機能を実装できた。

## 課題と今後の展望
- **マウス入力**: 現在は `MouseInfo` による位置取得のみ。クリックやドラッグイベントを Compose から `Mascot` に渡すブリッジが必要。
- **ウィンドウ識別**: マルチウィンドウ化に伴い、`NativeWindowUtil` が操作対象のウィンドウを正しく特定するために、ウィンドウタイトルによる識別ロジックを導入した。長期的には HWND をより直接的に管理する方法を検討する。


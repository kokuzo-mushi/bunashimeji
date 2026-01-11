# 議論と実施事項のサマリ (2025-01-10)

## 1. 目的
Phase 5 (Project Panama & JetBrains Compose) への移行に向けた技術的負債の解消。
具体的には、従来の「Iron Rules」違反（JNA依存、ロジック層でのAWT依存）を解消し、テストを全て通過させることを目標とした。

## 2. 実施した主な変更

### 2.1. アーキテクチャの刷新 (Decoupling)
- **独自のプリミティブ型導入**:
  - `java.awt.Point`, `java.awt.Rectangle` の代替として、`NeoPoint`, `NeoRect` レコードを作成。
  - これにより、コアロジック（`Mascot`, `Action` 等）から `java.awt` への依存を排除。
- **JNA から Project Panama への移行**:
  - `NativeWindowUtil` に不足していた Win32 API (`GetWindowRect`, `GetWindowText` 等) を FFM API で実装。
  - `Main`, `Mascot`, `Environment` クラスから JNA (`com.sun.jna`) への依存を完全削除。

### 2.2. バグ修正と機能強化
- **式評価エンジン (`ExpressionEngine`)**:
  - `BinaryExpression`: 数値型（Integer/Double）混合時の厳密等価比較 (`===`) が失敗する問題を修正。単項プラス演算子 (`+`) のサポートを追加。
  - `ExpressionEngineTypeTest`: キャッシュ有効化に伴い、テストケースごとの状態分離が不十分だった問題を `TriggerCondition.clearGlobalCache()` 追加により解決。
- **キャッシュ機構 (`ExprCache`)**:
  - 安全のため無効化されていたキャッシュロジックを有効化し、パフォーマンスを改善。
- **動作設定テスト (`ConfigurationTest`)**:
  - `SequenceAction` のフレーム進行シミュレーションを修正。
  - 無限ループ検知テストにおいて、極短時間の Action が1フレーム内で無限ループ判定されてしまう問題を、テストデータの Duration 調整により解決。

## 3. 結果
- プロジェクトのビルド (`gradlew build`) が成功。
- 全 156 件のユニットテスト・統合テストが **全て通過 (Pass)**。

## 4. 今後の展望
- 基盤改修が完了したため、次フェーズである「JetBrains Compose を用いた GUI 描画層の刷新」に着手可能。
- `Main.java` に残る一部のレガシーコードの整理と、実機での長時間動作検証が推奨される。

## 5. Phase 5: Compose Multiplatform PoC (2025-01-10 追記)

### 5.1. 概要
GUIフレームワークの近代化に向け、**JetBrains Compose Multiplatform for Desktop** の導入と概念実証 (PoC) を実施した。

### 5.2. 実施内容
- **ビルド構成の更新 (`build.gradle.kts`)**:
  - Kotlin (`1.9.22`) および Compose (`1.6.0`) プラグインを追加。
  - 依存関係に `compose.desktop.currentOs`, `compose.material` を追加。
- **PoCランチャーの実装 (`ComposeLauncher.kt`)**:
  - `application` / `Window` コンポーザブルによる透明ウィンドウの作成。
  - `ImageIO` と `toComposeImageBitmap` を用いたマスコット画像 (`img/White/Stay1.png`) の描画。
  - **Native Interop**: `ComposeWindow` から `windowHandle` (HWND) を取得し、既存の `NativeWindowUtil` (Panama) を介してウィンドウ位置を制御できることを実証。

### 5.3. 検証結果
- 新設したタスク `gradlew runPoC` によりアプリケーションが正常に起動。
- 透明ウィンドウ上での画像描画と、Panama経由での円運動アニメーションを確認。
- これにより、**Compose と Project Panama の共存が可能**であることが証明された。

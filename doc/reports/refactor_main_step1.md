# リファクタリングレポート: Main.java Step 1

## 目的
本ステップの目的は、内部状態管理とコア更新ループを抽出することで、`Main.java` の「God Class（神クラス）」状態を解消することです。これは Phase 5 (JetBrains Compose 統合) に向けた準備であり、Swing/AWT に依存する `Main` クラスから独立して `Mascot` インスタンスを管理できるようにします。

## 実施した変更

### 1. 新クラス: `MascotContext`
- **場所**: `app/src/main/java/com/group_finity/mascot/manager/MascotContext.java`
- **目的**: 内部クラス `private static class MascotInstance` の置き換え。
- **詳細**:
  - マスコットごとの全状態（`Mascot`, `MascotWindow`, `EventDispatcher`, `EvaluationContext` 等）をカプセル化。
  - 適切な Getter と Setter を提供。
  - `Main` クラスからの直接的なフィールドアクセスを排除し、疎結合化。

### 2. `Main.java` のリファクタリング
- **内部クラスの削除**: `MascotInstance` を削除。
- **フィールドの更新**: `List<MascotInstance> mascotInstances` を `List<MascotContext>` に変更。
- **メソッドの抽出**:
  - `run()` ループ内の巨大なロジックブロックを以下の private メソッドに抽出:
    ```java
    private void updateMascot(MascotContext instance, long tickCount, Map<String, Integer> mouseMap)
    ```
  - このメソッドの責務:
    - イベントディスパッチ (`evaluateTriggers`)
    - アクションの更新 (`mascot.tick()`)
    - ウィンドウ追従ロジック (Project Panama `applyWindowMove`)
    - 物理演算と衝突判定 (重力, 壁・天井判定)
    - 描画 (`mascotView.draw()`)
- **ヘルパーメソッドの更新**: `createMascot`, `removeMascot`, `getNearestMascot`, `gatherAllMascots`, `restoreToOne` を `MascotContext` を使用するように修正。

## 技術的メモとリスク
- **変数のスコープ**: `run` ループ内のローカル変数（例: `currentGravity`）が、正しく再定義されるか、`updateMascot` に引数として渡されるよう注意深く実装しました。
- **アクセス修飾子**: `updateMascot` は現状 `private` とし、ロジックを `Main` 内に留めつつ分離しています。
- **型安全性**: `double` 計算結果を `int` の Setter に渡す際の型不一致（キャスト漏れ）を修正しました。

## 次のステップ
- **検証**: アプリケーションを実行し、リファクタリング前と挙動が同一であることを確認する。
- **リファクタリング Step 2**: `updateMascot` のロジックを `Main.java` から完全に移動させる（例: `MascotContext` を受け取る `MascotManager` クラスへ）。
- **Compose 統合**: Phase 5 において、`MascotWindow` (AWT) の代わりに `MascotContext` を使用して Compose UI を駆動させる。

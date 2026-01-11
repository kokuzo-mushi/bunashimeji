# リファクタリングレポート: Main.java Step 2

## 目的
本ステップの目的は、マスコットのコア更新ループと関連ロジックを専用の `MascotManager` クラスに抽出することで、`Main.java` の分離をさらに進めることでした。これにより、`Main` はよりクリーンなエントリーポイントおよびコーディネーターへと変貌します。

## 実施した変更

### 1. 新クラス: `MascotManager`
- **場所**: `app/src/main/java/com/group_finity/mascot/manager/MascotManager.java`
- **責務**:
  - マスコット1体の1フレーム分のライフサイクル（Tick）管理。
  - イベントディスパッチ (`evaluateTriggers`) の処理。
  - 物理演算と衝突判定 (`Main.updateMascot` からロジックを移動)。
  - ウィンドウ追従処理 (Project Panama `applyWindowMove`)。
- **主要メソッド**:
  ```java
  public void tick(MascotContext instance, List<MascotContext> allMascots, 
                   NeoRect workArea, int gravity, long tickCount, 
                   Map<String, Integer> mouseMap, MemorySegment limitWindow)
  ```
- **ヘルパーメソッド**: `applyWindowMove`, `isSameWindow`, `getNearestMascot` をこのクラスへ移動しました。

### 2. `Main.java` のリファクタリング
- **依存関係**: `private final MascotManager mascotManager = new MascotManager();` を追加しました。
- **ループ処理**: `run()` ループ内の巨大な `updateMascot` 呼び出しを以下のように置き換えました:
  ```java
  mascotManager.tick(instance, currentInstances, new NeoRect(...), ...);
  ```
- **クリーンアップ**: 抽出済みのメソッド (`updateMascot`, `applyWindowMove`, `isSameWindow`) を `Main.java` から削除しました。
- **ファサードの維持**: 既存の `Action` クラス (例: `GatherAction`) との互換性を維持するため、`getNearestMascot` は `MascotManager` への委譲メソッドとして `Main` に復元しました。

## 技術的メモ
- **ワークエリア計算**: `MascotManager` は物理演算のために `limitWindow` のロジックを内部で処理し、有効なワークエリアを計算します。これにより、マスコットが一時的な制限に従いつつも、`Main.workArea` は一貫して（全画面を表すものとして）維持されます。
- **UI依存の排除**: `MascotManager` は純粋な Java ロジックであり、`MascotWindow` インターフェースを介する場合を除き、Swing UI 要素には依存しません。

## 次のステップ
- **Step 3**: `Environment` 関連ロジックを `MascotManager` または `MascotContext` に完全に移動する必要があるか、あるいは現在の状態で Phase 5 に十分か検証します。
- **Phase 5 PoC**: `MascotContext` と `MascotManager` を使用した JetBrains Compose 統合の PoC（概念実証）に進みます。

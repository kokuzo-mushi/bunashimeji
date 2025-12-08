# Shimeji Neo — 全体設計図・進捗フロー・課題・留意点（Gemini共有用）

最終更新: 2025-12-08 (Asia/Tokyo)

この文書は、ChatGPT 側で記憶している「Shimeji Neo」設計・開発の前提と、2025年10月前半までの統合サマリ（D-5 完了時点）をベースに、**Gemini に渡して再開発を進めるための引き継ぎ用**に整理したものです。

---

## 1. プロジェクト概要

- 名称: **Shimeji Neo**
- 目的: 既存の Shimeji 系デスクトップマスコットを、  
  **モジュール分割 + AST駆動の式評価 + イベント駆動アーキテクチャ**で再設計する。
- 重点:
  - 責務の明確化と拡張性
  - ルール/条件式の高速評価（キャッシュ）
  - EventQueue / Dispatcher / Trigger の一貫した連携
  - 将来的な Java 25・GraalVM 等の拡張余地
- リポジトリ: https://github.com/kokuzo-mushi/bunashimeji.git

---

## 2. 開発環境（固定前提）

- OS: Windows 11
- IDE: vscode
- Build: Gradle 9.x 系想定
- JDK: 21（Java 25 対応を見据えて設計・検討）

---

## 3. 基準ディレクトリ構造（構造基準版 v1）

```
app/
 ├─ src/
 │   ├─ main/
 │   │   ├─ java/
 │   │   │   ├─ com/group_finity/mascot/
 │   │   │   │   ├─ Main.java
 │   │   │   │   ├─ ShimejiApp.java
 │   │   │   │   └─ trigger/
 │   │   │   │       ├─ Trigger.java
 │   │   │   │       ├─ TriggerCondition.java
 │   │   │   │       └─ expr/
 │   │   │   │           ├─ ExpressionEngine.java
 │   │   │   │           ├─ ExprEvaluator.java
 │   │   │   │           ├─ ExprTrigger.java
 │   │   │   │           ├─ eval/EvaluationContext.java
 │   │   │   │           ├─ node/
 │   │   │   │           │   ├─ BinaryExpressionNode.java
 │   │   │   │           │   ├─ ExpressionNode.java
 │   │   │   │           │   ├─ LiteralNode.java
 │   │   │   │           │   ├─ UnaryExpressionNode.java
 │   │   │   │           │   └─ VariableNode.java
 │   │   │   │           ├─ parser/ExpressionParser.java
 │   │   │   │           └─ type/
 │   │   │   │               ├─ CoercionException.java
 │   │   │   │               ├─ CoercionPlan.java
 │   │   │   │               ├─ DefaultTypeCoercion.java
 │   │   │   │               ├─ DefaultTypeResolver.java
 │   │   │   │               ├─ Mode.java
 │   │   │   │               ├─ TypeCoercion.java
 │   │   │   │               ├─ TypeKind.java
 │   │   │   │               └─ TypeResolver.java
 │   │   │   └─ org/example/App.java
 │   │   └─ resources/
 │   │       ├─ behavior/
 │   │       ├─ config/
 │   │       ├─ images/
 │   │       └─ sounds/
 │   └─ test/
 │       ├─ java/
 │       │   ├─ com/group_finity/mascot/trigger/expr/
 │       │   │   ├─ ExprTriggerAdvancedTest.java
 │       │   │   └─ ExprTriggerTest.java
 │       │   ├─ com/group_finity/mascot/trigger/expr/type/DefaultTypeCoercionTest.java
 │       │   └─ org/example/AppTest.java
 │       └─ resources/
 └─ tree.txt
```

---

## 4. 全体アーキテクチャ

詳細は ARCHITECTURE.md を参照。

---

## 5. 開発ロードマップ

詳細は ROADMAP.md を参照。

---

## 6. D-5 フェーズ（評価キャッシュ）統合結果

### 6.1 目的
- ExprCache を導入し、TriggerCondition の式評価をキャッシュ化  
  - 同一条件式の再評価の高速化  
  - 依存変数追跡による正しいキャッシュ無効化

### 6.2 問題と原因（当時の DeepResearch 要点）

1) **Step 2 失敗（常にキャッシュ一致扱いの揺れ）**  
- `ctx.clearAccessLog()` を  
  キャッシュチェック前に実行して依存スナップショットが空化

2) **Step 3 失敗（変数更新を追跡できない）**  
- EvaluationContext が外部 `vars` をコピー保持しており  
  変数更新が Context に反映されない

3) **Step 5 性能逆転（キャッシュなのに遅い）**  
- 毎回キャッシュミス → 比較オーバーヘッドが上乗せ

### 6.3 修正方針（D-5 完了版で合意された内容）

- `TriggerCondition.evaluate()` 内で  
  **clearAccessLog() は再評価時のみに呼ぶ**
- キャッシュHIT判定は  
  **Mode(STRICT/LOOSE) に応じて currentDeps を生成**
- `EvaluationContext` は  
  **vars をコピーせず参照共有**へ
- これにより Step 2〜5 の  
  **キャッシュ一致 / 依存追跡 / 性能傾向が安定**

---

## 7. 現在のタスクリスト（Phase 1 進行中）

Phase 1「基盤の安定化と回復」を完了させるための当面のタスクリスト。

1.  **`EventDispatcher` への評価ロジック実装**:
    - `pollAndDispatch()` に代わる、トリガーを評価・発火させる新しいメソッド（例: `evaluateTriggers`）を実装する。
2.  **テストカバレッジの回復と再設計**:
    - `build.gradle` に `Mockito` への依存を追加する。
    - `EventDispatcherTest` をはじめとする無効化されたテストを、Mockito を用いて再設計し、有効化する。

---

## 8. これからの開発の留意点（Gemini で作業する際の指針）

### 8.1 変更戦略
- **最小修正 → テスト通過 → 次の最小修正**の順  
- 1コミットで  
  - 「責務変更」  
  - 「仕様追加」  
  - 「性能改善」  
  を混ぜない

### 8.2 テスト駆動の優先度
- ExprTrigger / DefaultTypeCoercion / ExprCacheIntegrationTest を  
  **破壊しないことが最優先**
- 性能系検証は  
  - まず正しさの担保  
  - 次にメトリクス改善  
  の順で実施

### 8.3 STRICT/LOOSE の二重系統管理
- 同じ式でも  
  - 型変換許容度  
  - 依存変数の取り扱い  
  が異なる可能性
- キャッシュキー設計や deps 比較は  
  **Mode を必ず含む前提**で統一

### 8.4 将来拡張（GraalVM/JS 等）への余白
- ExpressionEngine を  
  - AST evaluator  
  - 代替スクリプトエンジン（将来）  
  の差し替えポイントとして維持
- 依存追跡の共通IFを  
  「評価エンジン横断」で用意できるか検討

---

## 9. Gemini 用 “引き継ぎショートプロンプト”

以下をそのまま Gemini に貼り付けて使える形にしています。

```
あなたは上級Javaアーキテクト兼コードレビュアーです。
対象は Java + Gradle 環境で再設計中のデスクトップマスコット
「Shimeji Neo」です。

前提:
- Windows 11
- Eclipse (Pleiades 日本語版)
- JDK 21
- 将来的に Java 25 / GraalVM 等の拡張も視野
- リポジトリ: https://github.com/kokuzo-mushi/bunashimeji.git

目的:
上記の開発ロードマップ（Phase 1〜4）に従い、Shimeji Neo の開発を推進する。
当面は Phase 1「基盤の安定化と回復」の完了を目指す。

重要制約:
- 既存APIの破壊を最小化
- 署名変更・フィールド削除・コンストラクタ追加は慎重
- 変更の影響範囲と呼び出し元整合を必ず説明

依頼:
- 現在のタスクリスト（セクション7）を達成するための具体的な実装方針とコードを提案する。
- 必要なら疑似コード/パッチ単位で案を示す
```

---

## 10. 現状サマリー（超短縮）

- Shimeji Neo は **イベント駆動 + AST式評価** を中核に再設計を進めている。
- 開発は4つのフェーズに分割され、現在は **Phase 1: 基盤の安定化と回復** に着手している。
- 当面の目標は、`EventDispatcher` の評価ロジックを実装し、`Mockito` を使ってテストカバレッジを回復させることである。
- 改修は引き続き **API破壊最小・小刻み変更・テスト最優先**で進める。

---

以上。

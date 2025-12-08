# Shimeji Neo — 全体設計図・進捗フロー・課題・留意点（Gemini共有用）

最終更新: 2025-12-07 (Asia/Tokyo)

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
- IDE: Eclipse（Pleiades 日本語版）
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

## 4. 全体アーキテクチャ（概念図）

### 4.1 コアレイヤ

1) **Expression サブシステム（AST駆動）**  
- ExpressionParser → AST(ExpressionNode) → ExprEvaluator  
- EvaluationContext が変数・型変換・評価モードを司る  
- Mode: STRICT / LOOSE

2) **Trigger サブシステム**  
- Trigger は「イベント発火の条件」を抽象化  
- TriggerCondition が条件式の評価/キャッシュを担当  
- ExprTrigger など式ベースのトリガーが中心

3) **Event サブシステム**  
- EventQueue：イベントの保持と優先度/同時実行制御の入口  
- EventDispatcher：イベント登録/発火/Trigger連携  
- EventWorkerPool 等で非同期処理を想定

---

## 5. 進捗フロー（Phases）

### 5.1 2025年10月前半までの到達点

- **EventQueue**: 実装・確認済み  
  - 今後: 優先度・同時実行制御の増強
- **EventDispatcher**: 登録/発火処理の整理を進行  
  - Trigger との連携方式を検討
- **Trigger 構造**:  
  - クリック/時間/状態変化などタイプ比較と設計の方向付け
- **式評価（ExprEvaluator）**:  
  - `===`・単項`+`対応  
  - Long昇格抑制  
  - 型変換整理  
  - 既存テスト通過

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

## 7. 現在の課題（D-6 以降の設計論点）

### 7.1 キャッシュ統計の精度と活用
- CacheStatsTracker の改善  
- hit/miss の粒度  
- 「高速化に寄与しているか」を測る指標の定義

### 7.2 差分再評価（Incremental Evaluation）
- 依存変数の差分を用いた再評価ショートカット
- AST変化/式文字列変化の検出と  
  キャッシュ無効化ルール

### 7.3 EventDispatcher と Trigger の最終統合
- Dispatcher が  
  - Trigger からの発火イベントをどう正規化するか  
  - Queue へどの粒度で積むか  
- 例:  
  - 「状態変化イベント」  
  - 「時間イベント」  
  - 「ユーザー入力イベント」  
  を統一的に扱う EventType/Envelope 設計

### 7.4 API 破壊リスク管理
- 過去に「コンパイルエラー修正の副作用で  
  評価ロジックが崩壊」した経緯がある前提
- そのため:
  - 既存公開APIの署名変更は極力避ける
  - フィールド削除/コンストラクタ増設は慎重に
  - 変更時は呼び出し元整合を必ず同時に確認

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
1) EventQueue / EventDispatcher / Trigger の統合設計を
   “AST駆動の式評価/キャッシュ”と矛盾なく整理する
2) TriggerCondition + ExprCache の
   依存変数追跡と性能最適化を D-6 方針として具体化する

重要制約:
- 既存APIの破壊を最小化
- 署名変更・フィールド削除・コンストラクタ追加は慎重
- 変更の影響範囲と呼び出し元整合を必ず説明

依頼:
- 現状設計の弱点と矛盾点を列挙
- D-6 で最小リスクで効果が大きい改善順を提案
- 必要なら疑似コード/パッチ単位で案を示す
```

---

## 10. ここまでの要約（超短縮）

- Shimeji Neo は  
  **イベント駆動 + AST式評価 + キャッシュ**を中核に  
  デスクトップマスコットを再設計するプロジェクト。
- 2025年10月時点で  
  - EventQueue の基盤  
  - ExprEvaluator の強化  
  - TriggerCondition の評価キャッシュ（D-5）  
  が安定。
- 次は D-6 として  
  **キャッシュ統計の高度化 / 差分再評価 /  
  EventDispatcher×Trigger の最終統合**が主戦場。
- 改修は  
  **API破壊最小・小刻み変更・テスト最優先**で進める。

---

以上。

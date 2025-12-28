# Phase 3 完了レポート: レンダリングとパフォーマンスの最適化

## 1. 概要
本フェーズでは、Shimeji Neo のコアとなるレンダリングエンジンと実行ループの刷新を行いました。
Java 21 の最新機能を活用し、Windows 11 上で 60FPS の滑らかなアニメーションと、多数のマスコットを表示してもカクつかない低遅延な動作を実現しました。

## 2. 実装された主要技術

### 2.1 Direct3D パイプラインの強制有効化
- **設定**: JVM起動オプションに `-Dsun.java2d.d3d=true` を追加。
- **効果**: Java 2D の描画処理が GPU (Direct3D) でハードウェアアクセラレーションされるようになりました。これにより CPU 負荷が大幅に低減しました。
- **補足**: `ImageCache` にて画像を読み込む際、`BufferedImage.TYPE_INT_ARGB_PRE` (Pre-multiplied Alpha) に変換することで、GPU への転送効率を最大化しています。

### 2.2 Generational ZGC の採用
- **設定**: `-XX:+UseZGC -XX:+ZGenerational` を有効化。
- **効果**: Java 21 で導入された世代別 ZGC により、GC 停止時間が 1ミリ秒以下に抑えられています。マスコットの数が増えても「プチフリーズ」が発生しません。

### 2.3 高精度 60FPS メインループ
- **実装**: `Thread.sleep(30)` の固定待機から、`System.nanoTime()` を用いた可変スリープ方式に変更。
- **ロジック**:
  ```java
  long OPTIMAL_TIME = 1000000000 / 60; // 1フレーム約16.6ms
  long elapsed = System.nanoTime() - startTime;
  long wait = OPTIMAL_TIME - elapsed;
  if (wait > 0) Thread.sleep(wait / 1000000);
  ```
- **調整**: ループの高速化に伴い、重力加速度 (`GRAVITY`) を `3` から `1` に、アクションの移動速度や初速を約半分に調整し、自然な挙動を維持しました。

### 2.4 GraalJS による非同期ビヘイビア制御
- **実装**: `Behavior` クラスにて JavaScript Generator (`function*`) をサポート。
- **仕組み**:
  - マスコットごとに独立した GraalJS `Context` を保持。
  - `yield` を使用してフレームごとの処理中断・再開を記述可能に。
  - Java 側の変数をセキュアに JS 側へ注入 (`HostAccess.EXPLICIT`)。

## 3. 動作検証結果

- **描画**: マスコットの輪郭が綺麗に透過され、ティアリングや点滅のない安定した表示を確認。
- **物理演算**: 壁や床への着地判定、ウィンドウ追従が 60FPS で正確に動作。
- **負荷**: アイドル時の CPU 使用率は低く抑えられており、ZGC の恩恵でメモリ使用量のスパイクも見られません。

## 4. 今後の課題と展望 (Phase 4)

- **パッケージング**: `jpackage` を使用して、JRE を同梱したインストーラ (MSI/EXE) を作成する。
- **配布**: ユーザーが簡単にインストール・実行できる形式にまとめる。
- **設定UI**: XML を直接編集せずとも挙動を変更できる GUI の整備（現在の `SettingsWindow` の拡張）。

---
**結論**: Phase 3 の目標である「Rendering & Performance」は達成されました。Shimeji Neo は現在、高性能かつ拡張性の高いデスクトップマスコットエンジンとして稼働しています。
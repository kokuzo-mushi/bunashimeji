# Shimeji Neo (Bunashimeji)

**Shimeji Neo** は、デスクトップ上を動き回るマスコットアプリケーション「Shimeji」の次世代版プロジェクトです。
最新のJava技術（Java 21, Project Panama）を採用し、パフォーマンスと保守性を大幅に向上させています。

## 🚀 特徴

*   **Modern Java**: Java 21 (LTS) を採用し、Virtual Threads や Record などの最新機能を活用。
*   **High Performance**: Project Panama (FFM API) による効率的なネイティブ連携と、Active Rendering による滑らかな描画。
*   **AI-Native Development**: Google AntiGravity などのAI IDEでの開発を前提とした、エージェントフレンドリーな設計。

## 🛠 技術スタック

*   **言語**: Java 21 (Preview features enabled)
*   **ビルドツール**: Gradle (Kotlin DSL)
*   **GUI**: AWT (Window) + Active Rendering (BufferStrategy)
*   **Native Interop**: Project Panama (Foreign Function & Memory API)
*   **Scripting**: GraalJS

## 📂 ディレクトリ構成

*   `app/`: アプリケーションのソースコード
*   `img/`: マスコットの画像リソース
*   `ad/`: アーキテクチャ設計書およびAIエージェント向けルール (`Rules`, `Workflow`)
*   `conf/`: 設定ファイル

## 💻 開発環境のセットアップ

### 必須要件
*   JDK 21 (GraalVM 推奨)
*   Gradle 8.x

### 推奨IDE
*   **Google AntiGravity** (または VS Code, IntelliJ IDEA)
    *   本プロジェクトはAIエージェントによる開発支援を最適化するため、`.antigravityignore` や `.editorconfig` を完備しています。

### ビルドと実行

```bash
# ビルド
./gradlew build

# 実行
./gradlew run
```

## 📖 ドキュメント

詳細な設計や開発ルールについては、`ad/` ディレクトリ内のドキュメントを参照してください。

*   `ad/ARCHITECTURE.md`: システムアーキテクチャ概要
*   `ad/memory.md`: 開発上の重要な制約事項（Iron Rules）
*   `ad/workspace_rules.md`: AIエージェント向け行動指針

## 📄 ライセンス

(TBD)
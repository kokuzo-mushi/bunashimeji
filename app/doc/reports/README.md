# Shimeji Neo Project Reports & Research Archive

このディレクトリには、プロジェクトの各フェーズ完了時のレポートおよび、技術選定の根拠となった調査資料（Research Archive）が格納されています。

## 📂 Reports

*   **[Phase 4 Completion Report](phase4_completion_report.md)**
    *   Phase 1〜4（基盤刷新からパッケージングまで）の完了報告書。
    *   採用された技術スタック（Java 21, Panama, Active Rendering）と実装成果のサマリ。

## 📂 Research Archive (`research_archive/`)

過去のフェーズで実施された技術調査および設計メモのアーカイブです。
現在の実装の「なぜそうなっているか」を知るための参照資料として保持しています。

### Phase 1: Native Integration
*   **Shimeji DPI & Native Integration Research.md**
    *   Windows 11 における Per-Monitor V2 DPI 対応の調査。
    *   JNA から Project Panama への移行によるパフォーマンス改善効果。
    *   `SetWindowPos` や `UpdateLayeredWindow` を用いたウィンドウ制御の最適解。

### Phase 2: Scripting & Logic
*   **Shimeji Neo Scripting Modernization Design.md**
    *   GraalJS の採用理由と、Java アプリケーションへの組み込みパターン。
    *   JavaScript Generators (`function*`) を用いた非同期ビヘイビア（コルーチン）の設計。
    *   セキュリティサンドボックス（`HostAccess.EXPLICIT`）の設計。

### Phase 3: Rendering
*   **Shimeji Neo レンダリング最適化調査.md**
    *   Swing タイマーを廃止し、`BufferStrategy` を用いた Active Rendering への移行。
    *   Direct3D パイプラインの有効化と、VolatileImage による VRAM キャッシュ活用。
    *   Generational ZGC による GC 停止時間の短縮（1ms以下）。

### Phase 4: Packaging
*   **Shimeji Neo Packaging & Distribution.md**
    *   `jpackage` と WiX Toolset を用いた MSI インストーラ作成手順。
    *   GitHub Actions による CI/CD パイプライン構築。
    *   ハイブリッドランタイム（カスタムJRE + 依存JAR）の構成。

### Features & Fixes
*   **Smooth Wall-Ceiling Transition Design.md**
    *   壁から天井へ移動する際の「Kinematic Corner Transition」ロジック。
    *   物理演算を一時的に無効化し、幾何学的な円弧軌道で補間する手法について。
*   **マスコットの座標計算バグ修正.md**
    *   物理ピクセルと論理ピクセルの変換ミスによる座標ズレの修正記録。

---

## ℹ️ Note on Active Documents

現在進行形のアーキテクチャ設計やロードマップについては、`app/ad/` ディレクトリを参照してください。

*   `app/ad/ARCHITECTURE.md`: 最新のシステムアーキテクチャ
*   `app/ad/ROADMAP.md`: 今後の開発計画
*   `app/ad/modernization_audit.md`: 近代化監査報告書（Phase 5の指針）
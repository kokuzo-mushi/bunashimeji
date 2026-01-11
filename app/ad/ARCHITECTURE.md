# Shimeji Neo — Architecture Design Document

**Status**: Draft / Active
**Last Updated**: 2026-01-10

## 1. Architectural Overview

Shimeji Neo (Bunashimeji) は、**イベント駆動型 (Event-Driven)** かつ **エージェント指向 (Agent-Oriented)** のデスクトップマスコットアプリケーションです。
従来のShimejiの「XML定義による柔軟性」を維持しつつ、Java 21の最新機能を用いてパフォーマンスと保守性を根本から刷新しています。

### Design Philosophy
1.  **Performance First**: 50体以上のマスコットを60FPSで動作させるため、GC負荷の低い設計（ZGC, Primitive Records）と高効率なネイティブ連携（Panama）を徹底する。
2.  **Modern Java**: Java 21 (LTS) の機能を前提とし、レガシーなJava 6/8時代のイディオム（無名内部クラスの多用、可変なJavaBeans）を排除する。
3.  **Safety**: メモリ安全性（FFM API）と外部入力の安全性（Secure XML/Zip processing）を確保する。

---

## 2. Core Subsystems

### 2.1. The Mascot Entity
マスコットは単なる画像ではなく、以下の要素を持つ自律的なエージェントとしてモデル化されています。

*   **State (Model)**: 座標 (`Point`), 速度 (`Vector`), 現在のアクション (`Action`), 内部変数 (`Variables`).
*   **Appearance (View)**: 現在のポーズ (`Image`), ウィンドウ位置 (`Window`).
*   **Brain (Controller)**: 次の行動を決定するロジック (`Behavior`, `Script`).

### 2.2. Event & Trigger System
マスコットの行動決定は、毎フレーム評価されるイベントシステムによって駆動されます。

*   **EventDispatcher**: システムティックやユーザー入力（マウスホバー、クリック）を監視。
*   **Trigger**: 「マウスが重なった」「壁にぶつかった」などの条件を評価。
*   **Behavior**: トリガーが発火した際に実行すべき `Action` を選択。

### 2.3. Action & Physics Engine
物理演算とアニメーションを統合したシステムです。

*   **Active Rendering Loop**: `BufferStrategy` を用いた独自のレンダリングループで、OSの再描画イベントに依存せず60FPSを維持します。
*   **Hybrid Physics**:
    *   **Dynamic Mode**: 重力、摩擦、壁判定を行う通常の物理演算。
    *   **Kinematic Mode**: 壁・天井の遷移時 (`CornerTurn`) など、物理演算を一時的に無効化し、幾何学的な軌道計算 (`CornerMath`) に従って移動するモード。

### 2.4. Native Interface (Project Panama)
OSネイティブ機能へのアクセスは、JNAではなく **Foreign Function & Memory (FFM) API** を使用します。

*   **Window Management**: `User32.dll` (Windows) などのAPIを直接呼び出し、ウィンドウ移動 (`SetWindowPos`) や透明化 (`UpdateLayeredWindow`) を高速に行います。
*   **Environment Sensing**: マウスカーソル位置、アクティブウィンドウの矩形、タスクバーの位置などをリアルタイムに取得します。

---

## 3. Data Flow

```mermaid
graph TD
    Input[User Input / Timer] --> Dispatcher[EventDispatcher]
    Dispatcher -->|Evaluate| Trigger[Trigger Conditions]
    Trigger -->|Select| Behavior[Behavior System]
    Behavior -->|Execute| Action[Action (Walk, Fall, etc.)]
    
    subgraph Physics Loop
        Action -->|Update Pos| Physics[Physics Engine]
        Physics -->|Check Collision| Environment[Environment Sensing]
        Physics -->|Apply| MascotState[Mascot State]
    end
    
    MascotState -->|Render| Window[AWT Window / Panama]
```
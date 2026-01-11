# Legacy Code Migration Notes for Plan B (Window Throwing 2.0)

既存コード (`ThrowAction.java` v1, `Main.java`) から引き継ぐべき重要な技術仕様と制約事項のまとめ。

## 1. ネイティブウィンドウ操作 (Native Access)
*   **`NativeWindowUtil` の使用義務**:
    *   ウィンドウの移動、矩形取得、可視判定には必ず `com.group_finity.mascot.nativeaccess.NativeWindowUtil` を使用すること。
    *   ❌ **禁止**: JNA (`User32.INSTANCE`) をアクションクラス内で直接使用してはならない（Project Panama への移行方針のため）。
    *   ✅ **推奨**: `NativeWindowUtil.moveWindow(hwnd, x, y, w, h, true)` (repaint=true) を使用する。

## 2. ウィンドウ復帰システム (Restoration System)
*   **`WindowRestorationManager` への登録**:
    *   ウィンドウを画面外に投げ捨てた際は、必ず `WindowRestorationManager.getInstance().addThrownWindow(...)` を呼び出して登録すること。
    *   これにより、一定時間後にウィンドウが自動的にデスクトップへ復帰する機能が担保される。

## 3. マスコットの状態管理 (Mascot State)
*   **アニメーション進行**:
    *   アクションの `execute` メソッド内で `animation.tick(msec)` を呼び出す必要がある（通常は 16ms または 40ms）。
*   **物理フラグの信頼**:
    *   `mascot.isHittingLeftWall()`, `mascot.isGrounded()` などのフラグは、`MascotManager` によって毎フレーム更新されているため、アクション内での再計算は不要。これらのフラグを信頼してロジックを組むこと。

## 4. 環境認識 (Environment Sensing)
*   **現状の課題**:
    *   既存の `Environment.findTargetWindow` は判定が緩く、最小化されたウィンドウや巨大な背景ウィンドウを誤検知する。
*   **Plan B での要件**:
    *   以下のフィルタリングを実装する必要がある：
        1.  **可視性**: `NativeWindowUtil.isWindowVisible()` かつ `!NativeWindowUtil.isIconic()`。
        2.  **サイズ**: 画面サイズ以上のウィンドウは除外（デスクトップ誤爆防止）。
        3.  **到達可能性**: 壁の向こう側にあるウィンドウは除外。

## 5. 座標系
*   マスコットの座標 (`mascot.getX()`, `getY()`) は論理座標系（DPI考慮済み）として扱われる。`NativeWindowUtil` は内部で物理座標変換を行うため、アクション側で座標変換を意識する必要はない。
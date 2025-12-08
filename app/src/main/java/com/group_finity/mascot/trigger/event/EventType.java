package com.group_finity.mascot.trigger.event;

/**
 * 発生しうるイベントの種別を定義します。
 * EventDispatcher はこの型を見て、関心を持つ Trigger にのみイベントを配送します。
 */
public enum EventType {
    /**
     * システムの定期的な時間経過通知。
     * Payload: Long (前回ティックからの経過ミリ秒)
     */
    SYSTEM_TICK,

    /**
     * マスコットの内部状態が変化した。
     * 例: アクションの変更、座標の更新など。
     * Payload: StateChangeEvent
     */
    MASCOT_STATE_CHANGED,

    /**
     * 外部環境が変化した。
     * 例: アクティブウィンドウの変更、マウスカーソル位置の更新など。
     * Payload: StateChangeEvent
     */
    ENVIRONMENT_CHANGED,

    /**
     * ユーザーによるマウス操作（クリックなど）。
     * Payload: java.awt.Point (クリック座標)
     */
    MOUSE_CLICK
}
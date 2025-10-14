package com.group_finity.mascot.trigger.expr.type;

/**
 * 型変換の厳密さを制御するモード。
 */
public enum Mode {
    /** 変換不能なら例外を投げる */
    STRICT,
    /** 失敗時に警告を出してフォールバック値を返す */
    WARN,
    /** 可能な限り寛容に変換を試みる */
    LENIENT
}

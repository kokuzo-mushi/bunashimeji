package com.group_finity.mascot.event;

/**
 * 状態変化イベントのペイロード。
 * どのプロパティが、どのように変化したかを保持します。
 */
public record StateChangeEvent(String propertyName, Object oldValue, Object newValue) {
}
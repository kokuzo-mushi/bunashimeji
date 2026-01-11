package com.group_finity.mascot.trigger.util;

import com.group_finity.mascot.trigger.event.EventType;

import java.util.EnumSet;
import java.util.Set;

/**
 * 式に含まれる変数名から、依存するEventTypeをマッピングするユーティリティ。
 */
public final class VariableToEventTypeMapper {

    private static final Set<String> TICK_VARIABLES = Set.of("tick", "time");

    private VariableToEventTypeMapper() {
        // private constructor for utility class
    }

    /**
     * 変数名のセットを、それが依存するEventTypeのセットに変換する。
     *
     * @param variableNames 式から収集された変数名のセット
     * @return 購読すべきEventTypeのセット
     */
    public static Set<EventType> map(Set<String> variableNames) {
        if (variableNames == null || variableNames.isEmpty()) {
            // 変数がなければ、定数式 ("true" など)。どのイベントにも依存しない。
            return EnumSet.noneOf(EventType.class);
        }

        final Set<EventType> eventTypes = EnumSet.noneOf(EventType.class);

        for (final String varName : variableNames) {
            if (varName.startsWith("mascot.")) {
                eventTypes.add(EventType.MASCOT_STATE_CHANGED);
            } else if (varName.startsWith("window.") || varName.startsWith("ie.")) {
                eventTypes.add(EventType.ENVIRONMENT_CHANGED);
            } else if (TICK_VARIABLES.contains(varName)) {
                eventTypes.add(EventType.SYSTEM_TICK);
            }
            // ここに他のルールを追加 (例: mouse. -> MOUSE_CLICK)
        }

        return eventTypes;
    }
}
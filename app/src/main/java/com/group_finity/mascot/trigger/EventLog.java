package com.group_finity.mascot.trigger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * EventLog — イベント発火情報を保持する構造体。
 * Dispatcherが発火時に生成し、キューや外部ロガーに渡す。
 */
public class EventLog {

    private static long nextId = 1L;

    private final long id;
    private final LocalDateTime timestamp;
    private final String triggerName;
    private final Map<String, Object> contextSnapshot;
    private final boolean success;
    private final long elapsedNanos;

    public EventLog(String triggerName, Map<String, Object> contextSnapshot, boolean success, long elapsedNanos) {
        this.id = nextId++;
        this.timestamp = LocalDateTime.now();
        this.triggerName = triggerName;
        this.contextSnapshot = contextSnapshot;
        this.success = success;
        this.elapsedNanos = elapsedNanos;
    }

    public long getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public Map<String, Object> getContextSnapshot() {
        return contextSnapshot;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return "[EventLog #" + id + "] " + fmt.format(timestamp) +
                " | Trigger=" + triggerName +
                " | Success=" + success +
                " | Elapsed=" + (elapsedNanos / 1_000_000.0) + "ms" +
                " | Context=" + contextSnapshot;
    }
}

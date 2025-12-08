package com.group_finity.mascot.trigger.event;

import java.time.Instant;
import java.util.Objects;

/**
 * システム内で発生したすべてのイベントを正規化してラップするコンテナ。
 * EventDispatcher はこのオブジェクトをキューイングし、各 Trigger に渡します。
 *
 * @param <T> イベント固有のペイロードの型
 */
public final class EventEnvelope<T> {

    private final EventType type;
    private final T payload;
    private final Instant timestamp;
    private final Object source; // イベント発生源 (例: Mascotインスタンス, WindowManager)

    public EventEnvelope(EventType type, T payload, Object source) {
        this.type = Objects.requireNonNull(type, "Event type cannot be null");
        this.payload = payload; // Payload can be null
        this.source = Objects.requireNonNull(source, "Event source cannot be null");
        this.timestamp = Instant.now();
    }

    public EventType getType() {
        return type;
    }

    public T getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Object getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "EventEnvelope{" +
                "type=" + type +
                ", payload=" + payload +
                ", timestamp=" + timestamp +
                ", source=" + source.getClass().getSimpleName() +
                '}';
    }
}
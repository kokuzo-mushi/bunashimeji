package com.group_finity.mascot.trigger.event;

public class EventEnvelope<T> {
    private final EventType type;
    private final T payload;
    private final Object source;

    public EventEnvelope(EventType type, T payload, Object source) {
        this.type = type;
        this.payload = payload;
        this.source = source;
    }

    public EventType getType() {
        return type;
    }

    public T getPayload() {
        return payload;
    }

    public Object getSource() {
        return source;
    }
}
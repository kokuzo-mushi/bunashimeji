package com.group_finity.mascot.trigger.event;

public class StateChangeEvent {
    private final String propertyName;
    private final Object oldValue;
    private final Object newValue;

    public StateChangeEvent(String propertyName, Object oldValue, Object newValue) {
        this.propertyName = propertyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }
}
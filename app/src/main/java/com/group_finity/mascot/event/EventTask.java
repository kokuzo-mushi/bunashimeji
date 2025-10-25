package com.group_finity.mascot.event;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 優先度付きイベントタスク。
 * EventQueueに格納され、EventWorkerによって実行される。
 */
public class EventTask implements Comparable<EventTask>, Runnable {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    public enum Priority {
        HIGH(3), MEDIUM(2), LOW(1);
        private final int level;
        Priority(int level) { this.level = level; }
        public int level() { return level; }
    }

    private final long id = COUNTER.incrementAndGet();
    private final Instant createdAt = Instant.now();
    private final Priority priority;
    private final Runnable action;

    private volatile boolean executed = false;

    public static final EventTask POISON = new EventTask(() -> {}, Priority.LOW);

    public EventTask(Runnable action, Priority priority) {
        this.action = action;
        this.priority = priority;
    }

    @Override
    public void run() {
        try {
            if (!executed) {
                action.run();
            }
        } finally {
            executed = true;
        }
    }

    @Override
    public int compareTo(EventTask other) {
        // 優先度が高い方を先に処理
        int diff = Integer.compare(other.priority.level(), this.priority.level());
        if (diff != 0) return diff;
        // 同一優先度内では古いタスクを優先
        return Long.compare(this.id, other.id);
    }

    public boolean isExecuted() {
        return executed;
    }

    public Priority getPriority() {
        return priority;
    }

    public long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "EventTask{id=" + id + ", priority=" + priority + ", executed=" + executed + "}";
    }
}

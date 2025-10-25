package com.group_finity.mascot.log;


import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


public final class EventLogRecord {
private static final AtomicLong SEQ = new AtomicLong(0);


public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }


public final long seq;
public final Instant timestamp;
public final String source;
public final String trigger;
public final boolean success;
public final long elapsedNanos;
public final Level level;
public final Map<String, Object> context;


public EventLogRecord(String source, String trigger, boolean success, long elapsedNanos, Level level, Map<String,Object> context) {
this.seq = SEQ.getAndIncrement();
this.timestamp = Instant.now();
this.source = source;
this.trigger = trigger;
this.success = success;
this.elapsedNanos = elapsedNanos;
this.level = level == null ? Level.INFO : level;
this.context = context;
}
}
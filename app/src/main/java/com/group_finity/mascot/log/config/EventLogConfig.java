package com.group_finity.mascot.log.config;


import java.time.Duration;


public final class EventLogConfig {
public final int capacity;
public final int maxBatch;
public final Duration flushInterval;
public final DropPolicy dropPolicy;


public enum DropPolicy { BLOCK, DROP_NEWEST, DROP_OLDEST }


public EventLogConfig(int capacity, int maxBatch, Duration flushInterval, DropPolicy dropPolicy){
this.capacity = capacity;
this.maxBatch = maxBatch;
this.flushInterval = flushInterval;
this.dropPolicy = dropPolicy;
}


public static EventLogConfig defaultConfig(){
return new EventLogConfig(8192, 256, Duration.ofMillis(50), DropPolicy.DROP_OLDEST);
}
}
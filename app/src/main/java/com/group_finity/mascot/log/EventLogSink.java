package com.group_finity.mascot.log;


import java.util.List;


public interface EventLogSink extends AutoCloseable {
void writeBatch(List<EventLogRecord> batch) throws Exception;
default void close() throws Exception { /* no-op */ }
}
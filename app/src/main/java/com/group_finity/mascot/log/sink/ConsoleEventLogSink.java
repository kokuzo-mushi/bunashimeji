package com.group_finity.mascot.log.sink;


import java.time.format.DateTimeFormatter;
import java.util.List;

import com.group_finity.mascot.log.EventLogRecord;
import com.group_finity.mascot.log.EventLogSink;


public final class ConsoleEventLogSink implements EventLogSink {
private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_INSTANT;
@Override public void writeBatch(List<EventLogRecord> batch){
for (var r : batch){
System.out.printf("[EventLog] %s | #%d | %s | Trigger=%s | Success=%s | %.3fms | Ctx=%s%n",
FMT.format(r.timestamp), r.seq, r.source, r.trigger, r.success, r.elapsedNanos/1_000_000.0, r.context);
}
}
}
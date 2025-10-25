package com.group_finity.mascot.log.sink;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.group_finity.mascot.log.EventLogRecord;
import com.group_finity.mascot.log.EventLogSink;


public final class RotatingFileEventLogSink implements EventLogSink {
private final Path dir;
private final long rotateBytes;
private BufferedWriter out;
private Path current;


public RotatingFileEventLogSink(Path dir, long rotateBytes) throws IOException {
this.dir = dir; this.rotateBytes = rotateBytes;
Files.createDirectories(dir);
roll();
}


private void roll() throws IOException {
if (out != null) out.close();
String name = "eventlog-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log";
current = dir.resolve(name);
out = Files.newBufferedWriter(current, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
}


@Override public void writeBatch(List<EventLogRecord> batch) throws IOException {
for (var r : batch){
out.write(String.format("%s\t#%d\t%s\t%s\t%s\t%.3f\t%s%n",
r.timestamp, r.seq, r.level, r.source, r.trigger, r.elapsedNanos/1_000_000.0, r.context));
}
out.flush();
if (Files.size(current) > rotateBytes) roll();
}


@Override public void close() throws IOException { if (out != null) out.close(); }
}
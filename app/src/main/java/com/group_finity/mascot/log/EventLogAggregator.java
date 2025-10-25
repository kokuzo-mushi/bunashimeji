package com.group_finity.mascot.log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.group_finity.mascot.log.config.EventLogConfig;

public final class EventLogAggregator implements AutoCloseable {
private final EventLogConfig cfg;
private final EventLogSink sink;
private final ArrayBlockingQueue<EventLogRecord> q;
private final Thread thread;
private volatile boolean running = true;


public EventLogAggregator(EventLogConfig cfg, EventLogSink sink){
this.cfg = cfg; this.sink = sink;
this.q = new ArrayBlockingQueue<>(cfg.capacity);
this.thread = new Thread(this::runLoop, "EventLog-Aggregator");
this.thread.setDaemon(true);
}


public void start(){ thread.start(); }


public boolean offer(EventLogRecord r){
return switch (cfg.dropPolicy){
case BLOCK -> { try { q.put(r); yield true; } catch (InterruptedException e){ Thread.currentThread().interrupt(); yield false; } }
case DROP_NEWEST -> q.offer(r);
case DROP_OLDEST -> offerDropOldest(r);
};
}


private boolean offerDropOldest(EventLogRecord r){
if (q.offer(r)) return true;
q.poll();
return q.offer(r);
}


private void runLoop(){
final List<EventLogRecord> batch = new ArrayList<>(cfg.maxBatch);
final long nanos = cfg.flushInterval.toNanos();
try {
while (running || !q.isEmpty()){
EventLogRecord first = q.poll(nanos, TimeUnit.NANOSECONDS);
if (first != null) batch.add(first);
q.drainTo(batch, cfg.maxBatch - batch.size());
if (!batch.isEmpty()){
try { sink.writeBatch(batch); }
catch (Exception e){ /* TODO: metrics */ }
finally { batch.clear(); }
}
}
} catch (InterruptedException ie){ Thread.currentThread().interrupt(); }
finally {
final List<EventLogRecord> rest = new ArrayList<>();
q.drainTo(rest);
if (!rest.isEmpty()) try { sink.writeBatch(rest); } catch (Exception ignored) {}
try { sink.close(); } catch (Exception ignored) {}
}
}


@Override public void close(){ stop(Duration.ofSeconds(2)); }


public void stop(Duration grace){
running = false;
try { thread.join(grace.toMillis()); } catch (InterruptedException e){ Thread.currentThread().interrupt(); }
}
}
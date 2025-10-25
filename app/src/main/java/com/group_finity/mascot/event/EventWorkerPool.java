package com.group_finity.mascot.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.group_finity.mascot.log.EventLog;
import com.group_finity.mascot.log.EventLogRecord;

/**
 * EventWorkerPool (D-4d)
 * - 起動／停止ログを EventLogAggregator に記録
 */
public class EventWorkerPool {

    private final PriorityBlockingQueue<EventTask> internalQueue; // nullならexternal
    private final BlockingQueue<EventTask> queue;
    private final List<EventWorker> workers = new ArrayList<>();
    private final int poolSize;
    private volatile boolean running = true;

    /** 内製キューで起動 */
    public EventWorkerPool(int poolSize) {
        this.poolSize = poolSize;
        this.internalQueue = new PriorityBlockingQueue<>();
        this.queue = this.internalQueue;
        startWorkers();
        EventLog.record("EventWorkerPool", "Startup", true, 0L,
                EventLogRecord.Level.INFO, Map.of("workers", poolSize));
        System.out.printf("[EventWorkerPool] Started with %d workers.%n", poolSize);
    }

    /** 外部キュー利用（テスト・互換用） */
    public EventWorkerPool(BlockingQueue<EventTask> externalQueue, int poolSize) {
        this.poolSize = poolSize;
        this.internalQueue = null;
        this.queue = externalQueue;
        startWorkers();
        EventLog.record("EventWorkerPool", "Startup", true, 0L,
                EventLogRecord.Level.INFO, Map.of("workers", poolSize, "mode", "external"));
        System.out.printf("[EventWorkerPool] Started with %d workers (external queue).%n", poolSize);
    }

    private void startWorkers() {
        for (int i = 0; i < poolSize; i++) {
            EventWorker w = new EventWorker(queue, "Worker-" + (i + 1));
            workers.add(w);
        }
    }

    /** タスク投入 */
    public void submit(EventTask task) {
        if (running && task != null) {
            queue.offer(task);
        }
    }

    /** 停止要求 */
    public void shutdown() {
        running = false;
        for (EventWorker w : workers) {
            w.shutdown();
        }
        EventLog.record("EventWorkerPool", "Shutdown", true, 0L,
                EventLogRecord.Level.INFO, Map.of("workers", poolSize));
        System.out.println("[EventWorkerPool] Shutdown requested.");
    }

    /** 停止待機 */
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        boolean ok = true;
        for (EventWorker w : workers) {
            ok &= w.awaitTermination(timeout, unit);
        }
        if (ok) {
            EventLog.record("EventWorkerPool", "Termination", true, 0L,
                    EventLogRecord.Level.INFO, Map.of("workers", poolSize));
            System.out.println("[EventWorkerPool] All workers terminated safely.");
        }
        return ok;
    }

    public int getPoolSize() { return poolSize; }
    public boolean isRunning() { return running; }
    public BlockingQueue<EventTask> getQueue() { return queue; }
}

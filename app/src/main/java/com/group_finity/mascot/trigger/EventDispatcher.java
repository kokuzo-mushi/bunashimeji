package com.group_finity.mascot.trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import com.group_finity.mascot.event.EventTask;
import com.group_finity.mascot.event.EventWorkerPool;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;

/**
 * EventDispatcher (D-4d 修正版)
 * - Trigger評価結果をEventQueueに直接enqueue
 * - 既存のEventLogコンストラクタに適合
 */
public class EventDispatcher {

    private final List<Trigger> triggers = new ArrayList<>();
    private final EvaluationContext context;
    private final EventQueue eventQueue;
    private final EventWorkerPool pool;

    /** 標準コンストラクタ（プールサイズ2） */
    public EventDispatcher(EvaluationContext context, EventQueue queue) {
        this(context, queue, 2);
    }

    /** プールサイズ指定版 */
    public EventDispatcher(EvaluationContext context, EventQueue queue, int poolSize) {
        this.context = context;
        this.eventQueue = queue;
        this.pool = new EventWorkerPool(poolSize);
    }

    /** 外部キュー利用（テスト用） */
    public EventDispatcher(BlockingQueue<EventTask> externalQueue) {
        this.context = null;
        this.eventQueue = null;
        this.pool = new EventWorkerPool(externalQueue, 1);
    }

    /** Runnableを直接ディスパッチ */
    public void dispatch(Runnable action, EventTask.Priority priority) {
        if (action == null) return;
        EventTask task = new EventTask(action, priority);
        pool.submit(task);
        System.out.printf("[EventDispatcher] Direct dispatch: %d (priority=%s)%n", task.getId(), task.getPriority());
    }

    /** Trigger登録 */
    public void registerTrigger(Trigger trigger) {
        if (trigger != null) triggers.add(trigger);
    }

    /** pollAndDispatch: 成功トリガーを非同期で実行 + EventLogへ出力 */
    public void pollAndDispatch() {
        if (context == null || eventQueue == null) {
            System.err.println("[EventDispatcher] pollAndDispatch skipped (context or eventQueue is null)");
            return;
        }

        for (Trigger trigger : triggers) {
            long start = System.nanoTime();
            boolean success = false;
            
            try {
                success = trigger.check(context);
            } catch (Exception e) {
                System.err.println("[EventDispatcher] Trigger check error: " + e.getMessage());
                e.printStackTrace();
            }
            
            long elapsed = System.nanoTime() - start;

            // ★ 修正: 既存のEventLogコンストラクタを使用
            Map<String, Object> snapshot = context.getVariablesSnapshot();
            EventLog log = new EventLog(
                trigger.toString(),
                snapshot,
                success,
                elapsed
            );
            
            // EventQueueに直接enqueue（同期的）
            eventQueue.enqueue(log);

            if (success) {
                // Snapshotを固定化してWorkerへ
                final EvaluationContext snapshotCtx = context.snapshotImmutable();
                EventTask task = new EventTask(() -> {
                    try {
                        trigger.execute(snapshotCtx);
                    } catch (Exception e) {
                        System.err.println("[EventDispatcher] Task execution error: " + e.getMessage());
                        e.printStackTrace();
                        
                        // 実行エラーも記録
                        EventLog errorLog = new EventLog(
                            trigger.toString() + " (execution)",
                            Map.of("error", e.getMessage()),
                            false,
                            0L
                        );
                        eventQueue.enqueue(errorLog);
                    }
                }, EventTask.Priority.MEDIUM);

                pool.submit(task);
                System.out.printf("[EventDispatcher] Trigger fired and submitted: %s%n", trigger);
            } else {
                System.out.printf("[EventDispatcher] Trigger skipped: %s%n", trigger);
            }
        }
    }

    public int getRegisteredCount() { 
        return triggers.size(); 
    }
    
    public void clear() { 
        triggers.clear(); 
    }

    public void shutdownWorkers() {
        if (eventQueue != null) {
            EventLog shutdownLog = new EventLog(
                "EventWorkerPool.Shutdown",
                Map.of("workers", pool.getPoolSize()),
                true,
                0L
            );
            eventQueue.enqueue(shutdownLog);
        }
        pool.shutdown();
    }

    public void awaitWorkers(long timeoutMillis) {
        pool.awaitTermination(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
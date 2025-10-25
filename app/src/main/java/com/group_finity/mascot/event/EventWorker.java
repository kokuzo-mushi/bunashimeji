package com.group_finity.mascot.event;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 非同期イベント実行用のワーカークラス。
 * EventQueue（PriorityBlockingQueue）からEventTaskを取り出し実行する。
 */
public class EventWorker implements Runnable {

    private final BlockingQueue<EventTask> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;
    private final String name;

    public EventWorker(BlockingQueue<EventTask> queue, String name) {
        this.queue = queue;
        this.name = name;
        this.thread = new Thread(this, name);
        this.thread.start();
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                EventTask task = queue.take();
                if (task == EventTask.POISON) {
                    break; // 安全終了信号
                }

                try {
                    task.run();
                } catch (Throwable t) {
                    System.err.printf("[%s] Error executing task %s: %s%n",
                            name, task, t.getMessage());
                    t.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.printf("[%s] Worker terminated safely.%n", name);
        }
    }

    /** 安全な停止要求を送信 */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            queue.offer(EventTask.POISON);
        }
    }

    /** 停止完了を待機（タイムアウト付き） */
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            thread.join(unit.toMillis(timeout));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !thread.isAlive();
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getName() {
        return name;
    }
}

package com.group_finity.mascot.trigger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.group_finity.mascot.event.EventTask;

/**
 * D-3c互換版 EventQueue
 * 
 * - EventLog専用APIは従来通り型安全
 * - 非同期EventTask用APIはObject扱いで併存
 */
public class EventQueue {

    // 内部キューはObject汎用だが、外部APIで型制御
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

    /** EventLogを登録（従来仕様） */
    public void enqueue(EventLog log) {
        if (log != null) {
            queue.offer(log);
            System.out.println("[EventQueue] Enqueued: " + log);
        }
    }

    /** 非同期EventTaskを登録（D-3系統） */
    public void offer(EventTask task) {
        if (task != null) {
            queue.offer(task);
            System.out.println("[EventQueue] Offered EventTask: " + task);
        }
    }

    /** EventLogを取り出す（型安全版: Main用） */
    public EventLog poll() {
        Object item = queue.poll();
        if (item instanceof EventLog log) {
            return log;
        }
        // EventTaskなど別型はスキップして再帰的に探す
        if (item != null) {
            return poll();
        }
        return null;
    }

    /** 汎用Object版（Worker等で使用） */
    public Object take() throws InterruptedException {
        return queue.take();
    }

    /** 空判定 */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** 残件数 */
    public int size() {
        return queue.size();
    }

    /** 全削除 */
    public void clear() {
        queue.clear();
    }
}

package com.group_finity.mascot.event;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.EventDispatcher;

/**
 * Shimeji Neo: EventDispatcher + EventWorker 連携テスト
 * フェーズ D-3c 統合検証
 */
public class EventSystemTest {

    @Test
    public void testEventWorkerIntegration() throws Exception {
        PriorityBlockingQueue<EventTask> queue = new PriorityBlockingQueue<>();
        EventWorker worker = new EventWorker(queue, "Worker-1");
        EventDispatcher dispatcher = new EventDispatcher(queue);

        System.out.println("=== Shimeji Neo - EventWorker Integration Test (JUnit) ===");

        // タスク登録（優先度混在）
        dispatcher.dispatch(() -> log("Task-A [LOW] executed"), EventTask.Priority.LOW);
        dispatcher.dispatch(() -> log("Task-B [HIGH] executed"), EventTask.Priority.HIGH);
        dispatcher.dispatch(() -> log("Task-C [MEDIUM] executed"), EventTask.Priority.MEDIUM);
        dispatcher.dispatch(() -> log("Task-D [HIGH] executed"), EventTask.Priority.HIGH);

        // 処理待機
        Thread.sleep(500);
        worker.shutdown();
        worker.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("=== JUnit Test Completed ===");
    }

    private static void log(String msg) {
        System.out.printf("[%s] %s%n", Thread.currentThread().getName(), msg);
    }
}

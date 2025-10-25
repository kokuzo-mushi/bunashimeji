package com.group_finity.mascot.event;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Shimeji Neo: EventWorkerPool 並列動作テスト
 * フェーズ D-4a - スレッド並列検証
 */
public class EventWorkerPoolTest {

    @Test
    public void testParallelExecutionAndShutdown() throws Exception {
        System.out.println("=== Shimeji Neo - EventWorkerPool Parallel Test ===");

        EventWorkerPool pool = new EventWorkerPool(3);

        // タスク登録（優先度混在）
        pool.submit(new EventTask(() -> log("Task-A [LOW] executed"), EventTask.Priority.LOW));
        pool.submit(new EventTask(() -> log("Task-B [HIGH] executed"), EventTask.Priority.HIGH));
        pool.submit(new EventTask(() -> log("Task-C [MEDIUM] executed"), EventTask.Priority.MEDIUM));
        pool.submit(new EventTask(() -> log("Task-D [HIGH] executed"), EventTask.Priority.HIGH));
        pool.submit(new EventTask(() -> log("Task-E [LOW] executed"), EventTask.Priority.LOW));
        pool.submit(new EventTask(() -> log("Task-F [MEDIUM] executed"), EventTask.Priority.MEDIUM));

        // 少し待機してからシャットダウン
        Thread.sleep(1000);
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("=== EventWorkerPool Test Completed ===");
    }

    private static void log(String msg) {
        System.out.printf("[%s] %s%n", Thread.currentThread().getName(), msg);
    }
}

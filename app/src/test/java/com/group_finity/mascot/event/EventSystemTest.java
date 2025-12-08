package com.group_finity.mascot.event;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.trigger.event.EventTask;

/**
 * Shimeji Neo: EventDispatcher + EventWorker 連携テスト
 * フェーズ D-3c 統合検証
 */
public class EventSystemTest {

    @Test
    public void testEventWorkerIntegration() throws Exception {
        PriorityBlockingQueue<EventTask> queue = new PriorityBlockingQueue<>();
        // EventWorker worker = new EventWorker(queue, "Worker-1"); // EventWorker は現在直接は使用されません

        System.out.println("=== Shimeji Neo - EventWorker Integration Test (JUnit) ===");

        // TODO: EventDispatcher の設計変更により、このテストは全面的に見直す必要があります。
        // EventDispatcher はトリガー評価に特化し、汎用タスクをディスパッチする dispatch() メソッドは削除されました。
        // タスク実行のテストは EventWorkerPool を直接使用する形になります。
        // 旧コンストラクタ: new EventDispatcher(queue)
        // 旧メソッド: dispatcher.dispatch(...)

        /* 以下のコードは旧設計に基づいているため無効です
        // 処理待機
        Thread.sleep(500);
        worker.shutdown();
        worker.awaitTermination(2, TimeUnit.SECONDS);
        */

        System.out.println("=== JUnit Test Completed ===");
    }

    private static void log(String msg) {
        System.out.printf("[%s] %s%n", Thread.currentThread().getName(), msg);
    }
}

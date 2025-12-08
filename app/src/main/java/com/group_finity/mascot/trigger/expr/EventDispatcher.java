package com.group_finity.mascot.trigger;

import com.group_finity.mascot.trigger.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * イベントを非同期に処理し、適切なトリガーを発火させる中心的なクラス。
 * <p>
 * 1. イベントソースから {@link #dispatchEvent(EventEnvelope)} でイベントを受け取る。
 * 2. 内部のキューにイベントを格納する。
 * 3. ワーカースレッドがキューからイベントを取り出し、処理する。
 * 4. イベントの {@link EventType} に基づき、そのイベントを購読しているトリガーのみを効率的に選別する。
 * 5. 選別された各トリガーの {@link Trigger#check(EventEnvelope, EvaluationContext)} を呼び出し、条件を満たせば発火処理を行う。
 */
public class EventDispatcher implements Runnable {

    private final Map<EventType, List<Trigger>> triggerMap = new ConcurrentHashMap<>();
    private final BlockingQueue<EventEnvelope<?>> eventQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Thread workerThread;

    // TODO: EvaluationContext のインスタンス管理方法を決定する必要がある。
    // ここでは仮にコンストラクタで受け取る形にする。
    private final EvaluationContext evaluationContext;

    public EventDispatcher(EvaluationContext evaluationContext) {
        this.evaluationContext = evaluationContext;
        this.workerThread = new Thread(this, "EventDispatcher-Worker");
    }

    /**
     * ディスパッチャを起動する。
     * 起動後はイベントの受け付けと処理が開始される。
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread.start();
            System.out.println("EventDispatcher started.");
        }
    }

    /**
     * ディスパッchaを安全に停止する。
     * ワーカースレッドに割り込みをかけ、終了を待つ。
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            workerThread.interrupt(); // キューで待機中のスレッドを起こす
            System.out.println("EventDispatcher shutdown requested.");
            try {
                // スレッドの終了を待つ
                workerThread.join(5000); // 5秒のタイムアウト
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 新しいトリガーをディスパッチャに登録する。
     * トリガーが購読するイベントタイプに応じて、内部のマップに登録される。
     *
     * @param trigger 登録するトリガー
     */
    public void registerTrigger(Trigger trigger) {
        if (trigger == null) return;
        Set<EventType> subscribedTypes = trigger.getSubscribedEventTypes();
        if (subscribedTypes == null || subscribedTypes.isEmpty()) {
            System.err.println("Warning: Trigger " + trigger.getClass().getSimpleName() + " subscribes to no events.");
            return;
        }

        for (EventType type : subscribedTypes) {
            // スレッドセーフなリストを取得または新規作成してトリガーを追加
            triggerMap.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(trigger);
        }
        System.out.println("Registered trigger: " + trigger.getClass().getSimpleName() + " for " + subscribedTypes);
    }

    /**
     * 登録済みのトリガーを解除する。
     *
     * @param trigger 解除するトリガー
     */
    public void unregisterTrigger(Trigger trigger) {
        if (trigger == null) return;
        Set<EventType> subscribedTypes = trigger.getSubscribedEventTypes();
        if (subscribedTypes == null) return;

        for (EventType type : subscribedTypes) {
            List<Trigger> triggers = triggerMap.get(type);
            if (triggers != null) {
                triggers.remove(trigger);
            }
        }
        System.out.println("Unregistered trigger: " + trigger.getClass().getSimpleName());
    }

    /**
     * システムのどこからでもイベントを発行するためのメソッド。
     * イベントは内部キューに追加され、ワーカースレッドによって非同期に処理される。
     *
     * @param event 発行するイベント
     */
    public void dispatchEvent(EventEnvelope<?> event) {
        if (!running.get() || event == null) {
            return;
        }
        if (!eventQueue.offer(event)) {
            System.err.println("Warning: Event queue is full. Dropping event: " + event);
        }
    }

    @Override
    public void run() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                EventEnvelope<?> event = eventQueue.take();
                processEvent(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error during event processing: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("EventDispatcher worker thread finished.");
    }

    private void processEvent(EventEnvelope<?> event) {
        List<Trigger> interestedTriggers = triggerMap.get(event.getType());
        if (interestedTriggers == null || interestedTriggers.isEmpty()) {
            return;
        }

        for (Trigger trigger : interestedTriggers) {
            try {
                if (trigger.check(event, evaluationContext)) {
                    handleTriggerFired(trigger, event);
                }
            } catch (Exception e) {
                System.err.println("Error checking trigger " + trigger.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleTriggerFired(Trigger firedTrigger, EventEnvelope<?> causingEvent) {
        // TODO: ここにアクション実行ロジックを実装する (例: mascot.performAction(...))
        System.out.printf("[EVENT FIRED] Trigger: %s, Caused by: %s%n",
                firedTrigger.getClass().getSimpleName(), causingEvent.getType());
    }
}
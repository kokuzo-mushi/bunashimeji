package com.group_finity.mascot.trigger;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.type.NeoPoint;
import com.group_finity.mascot.type.NeoRect;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Dispatches events to triggers and fires the first one whose conditions are
 * met.
 * This class is central to the event-driven architecture of Shimeji Neo.
 * It holds a list of registered triggers and evaluates them when an event
 * occurs.
 */
public class EventDispatcher {

    private final EvaluationContext context;
    private final Mascot mascot;
    private final List<Trigger> triggers = new CopyOnWriteArrayList<>();
    private Supplier<List<Mascot>> mascotListProvider;

    public EventDispatcher(EvaluationContext context, Mascot mascot) {
        this.context = context;
        this.mascot = mascot;
    }

    public void registerTrigger(Trigger trigger) {
        this.triggers.add(trigger);
    }

    public void setMascotListProvider(Supplier<List<Mascot>> mascotListProvider) {
        this.mascotListProvider = mascotListProvider;
    }

    /**
     * Evaluates registered triggers in response to an event.
     * <p>
     * This method iterates through the list of triggers. For each trigger, it
     * checks if its
     * conditions are met by calling {@link Trigger#evaluate(EvaluationContext)}.
     * <p>
     * The first trigger that evaluates to {@code true} is considered "fired".
     * If the fired trigger is a {@link Behavior}, its associated {@link Action} is
     * retrieved and passed to the {@link Mascot} to be executed. The evaluation
     * then stops.
     * <p>
     * Currently, this method evaluates all triggers for any event. Future
     * optimizations
     * could filter triggers based on the event type to improve performance.
     *
     * @param event The event that triggered the evaluation (currently unused, for
     *              future filtering).
     */
    public void evaluateTriggers(EventEnvelope<?> event) {
        if (mascot == null) {
            // Cannot execute actions without a mascot.
            return;
        }

        List<Behavior> candidates = new ArrayList<>();

        // イベント変数をコンテキストに注入して、条件式から参照できるようにする
        this.context.setValue("event", event);

        // ✅ 【修正】マスコット自身もコンテキストに注入しないと、条件式(mascot.y < floor等)が評価できない
        this.context.setValue("mascot", this.mascot);

        // 環境認識: 最寄りのマスコット情報を計算して注入
        updateNearestMascot();

        // 環境認識: 崖っぷち判定や壁との距離を計算して注入
        updateEnvironmentInfo();

        // Debug: マスコットの物理状態を確認
        // これにより、isGrounded() が true なのか false なのかをログで確定させる
        if (this.mascot != null) {
            String floorInfo = "None";
            if (this.mascot.getFloorWindow() != null) {
                floorInfo = "Window";
            } else if (this.mascot.isGrounded()) {
                floorInfo = "WorkArea";
            }
            System.out.println("[EventDispatcher] Mascot State: isGrounded=" + this.mascot.isGrounded() + ", y=" + this.mascot.getAnchor().y() + ", Floor=" + floorInfo);
        }

        try {
            for (final Trigger trigger : triggers) {
                if (trigger.evaluate(event, this.context)) {
                    if (trigger instanceof Behavior) {
                        Behavior b = (Behavior) trigger;
                        // Debug Log
                        System.out.println("[EventDispatcher] Condition Matched: " + b.getName() + " (Event: "
                                + event.getType() + ")");
                        candidates.add(b);
                    }
                }
            }
        } finally {
            // 評価終了後にイベント変数を削除
            this.context.removeVariable("event");
            // mascotは常駐させても良いが、念のためクリーンアップする場合
            this.context.removeVariable("mascot");
            this.context.removeVariable("nearestMascot");
        }

        if (candidates.isEmpty()) {
            return;
        }

        // 候補の中からFrequencyに基づいて抽選を行う
        Behavior selectedBehavior = selectBehavior(candidates);

        if (selectedBehavior != null) {
            // 決定したアクションをログ出力
            System.out.println("[EventDispatcher] Selected Behavior: " + selectedBehavior.getName());

            Action action = selectedBehavior.instantiateAction(this.mascot);

            // 現在実行中のアクションと同じインスタンスであれば、リセット・再設定を行わない
            // これにより、毎フレーム条件を満たすアクション（Fallなど）がリセットされずに継続実行され、加速などが有効になる
            if (this.mascot.getCurrentAction() == action) {
                return;
            }

            if (action != null) {
                action.reset(); // アクションを再利用する前に必ず初期化する
                this.mascot.setNextAction(action);
            }
        }
    }

    private void updateNearestMascot() {
        // デフォルト値（誰もいない場合）
        double minDistance = Double.MAX_VALUE;
        int targetX = 0;
        int targetY = 0;

        if (mascotListProvider != null) {
            List<Mascot> allMascots = mascotListProvider.get();
            NeoPoint myAnchor = mascot.getAnchor();

            for (Mascot other : allMascots) {
                if (other == mascot) continue; // 自分自身は除外

                NeoPoint otherAnchor = other.getAnchor();
                // 距離の二乗を計算（平方根計算を避けるため比較時は二乗のままが望ましいが、
                // XML条件式が "distance < 150" と実距離を期待しているため sqrt する）
                double dist = Math.hypot(myAnchor.x() - otherAnchor.x(), myAnchor.y() - otherAnchor.y());

                if (dist < minDistance) {
                    minDistance = dist;
                    targetX = otherAnchor.x();
                    targetY = otherAnchor.y();
                }
            }
        }

        // コンテキストに注入するオブジェクトを作成
        // Mapを使うことで、スクリプトからは nearestMascot.distance のようにアクセスできる
        Map<String, Object> nearestInfo = new HashMap<>();
        nearestInfo.put("distance", minDistance);
        nearestInfo.put("x", targetX);
        nearestInfo.put("y", targetY);
        this.context.setValue("nearestMascot", nearestInfo);
    }

    private void updateEnvironmentInfo() {
        // 1. isOnEdge (崖っぷち判定)
        boolean isOnEdge = false;
        if (mascot.isGrounded()) {
            MemorySegment floor = mascot.getFloorWindow();
            if (floor != null && NativeWindowUtil.isWindow(floor)) {
                NeoRect rect = NativeWindowUtil.getWindowRect(floor);
                if (rect != null) {
                    int distLeft = Math.abs(mascot.getX() - rect.left());
                    int distRight = Math.abs(mascot.getX() - rect.right());
                    // 端から50px以内なら「崖っぷち」とみなす
                    if (distLeft < 50 || distRight < 50) {
                        isOnEdge = true;
                    }
                }
            }
        }
        this.context.setValue("isOnEdge", isOnEdge);

        // 2. signedDistToWallTop (壁の頂上までの距離)
        // PullUpアクションの発動条件に使用
        int signedDistToWallTop = 0;
        MemorySegment wall = null;
        if (mascot.isHittingLeftWall()) {
            wall = mascot.getLeftWallWindow();
        } else if (mascot.isHittingRightWall()) {
            wall = mascot.getRightWallWindow();
        }

        if (wall != null && NativeWindowUtil.isWindow(wall)) {
            NeoRect rect = NativeWindowUtil.getWindowRect(wall);
            if (rect != null) {
                // マスコットの頭 (Y-128) と壁の上端 (rect.top) の距離
                // 頭が壁より上にある場合は負の値になる
                signedDistToWallTop = (mascot.getY() - 128) - rect.top();
            }
        }
        this.context.setValue("signedDistToWallTop", signedDistToWallTop);
    }

    private Behavior selectBehavior(List<Behavior> candidates) {
        int totalFrequency = candidates.stream().mapToInt(Behavior::getFrequency).sum();
        if (totalFrequency == 0)
            return candidates.get(0);

        int random = new Random().nextInt(totalFrequency);
        int current = 0;
        for (Behavior behavior : candidates) {
            current += behavior.getFrequency();
            if (random < current)
                return behavior;
        }
        return candidates.get(candidates.size() - 1);
    }

    public void clear() {
        triggers.clear();
    }

    public int getRegisteredCount() {
        return triggers.size();
    }
}
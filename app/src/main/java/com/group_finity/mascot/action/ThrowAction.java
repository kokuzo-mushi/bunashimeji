package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.manager.WindowRestorationManager;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * ターゲットウィンドウに向かって移動し、投げるアクション。
 * Plan B: Reliability First (厳格な状態遷移による再設計)
 */
public class ThrowAction implements Action {

    private enum State {
        SCANNING,
        CHASING,
        GRABBING,
        CARRYING,
        THROWING,
        CELEBRATING,
        FINISHED
    }

    // マスコットごとの状態を保持するクラス
    private static class ThrowState {
        State state = State.SCANNING;
        MemorySegment targetWindow;
        boolean initialized = false;
        Animation animation; // マスコットごとのアニメーションインスタンス
        Animation celebrateAnimation; // 勝利ポーズ用アニメーション

        // 持ち上げ/運搬用
        int initialWindowX, initialWindowY, windowWidth, windowHeight;
        int grabOffsetX; // 掴んだ時のマスコットとのX座標差分
        int grabOffsetY; // 掴んだ時のマスコットとのY座標差分

        // 投擲用
        double velocityX; // 物理演算用 (double精度)
        double velocityY;

        // 汎用タイマー/カウンタ
        int tickCounter;

        ThrowState(Animation animation, Animation celebrateAnimation) {
            this.animation = animation;
            this.celebrateAnimation = celebrateAnimation;
        }

        void reset() {
            state = State.SCANNING;
            targetWindow = null;
            initialized = false;
            if (animation != null)
                animation.reset();
            tickCounter = 0;
            initialWindowX = 0;
            initialWindowY = 0;
            windowWidth = 0;
            windowHeight = 0;
            grabOffsetX = 0;
            grabOffsetY = 0;
            velocityX = 0;
            velocityY = 0;
        }
    }

    private final Animation animationTemplate;
    private Animation celebrationAnimationTemplate;
    private final Map<Mascot, ThrowState> states = new WeakHashMap<>();
    private Mascot lastMascot;

    private static final int REACH_DISTANCE = 5; // 到達判定距離
    private static final int SEARCH_RADIUS = 400; // 探索半径
    private static final int MAX_SEARCH_VERTICAL = 400; // 垂直探索範囲
    private static final int CHASE_SPEED = 8;
    private static final int CARRY_SPEED = 6;
    private static final int GRAB_DURATION = 10; // 掴みにかける時間

    public ThrowAction(Animation animation) {
        this.animationTemplate = animation;
    }

    public void setCelebrationAnimation(Animation animation) {
        this.celebrationAnimationTemplate = animation;
    }

    private ThrowState getState(Mascot mascot) {
        return states.computeIfAbsent(mascot, m -> new ThrowState(animationTemplate, celebrationAnimationTemplate));
    }

    @Override
    public void execute(Mascot mascot) {
        lastMascot = mascot;
        ThrowState s = getState(mascot);

        if (!s.initialized && s.state == State.SCANNING) {
            // Log.debug("[ThrowAction] Started.");
            s.initialized = true;
        }

        // 以前の実行で終了していた場合はリセットして再開
        if (s.state == State.FINISHED) {
            s.reset();
            s.initialized = true;
            mascot.setTargetWindow(null);
        }

        switch (s.state) {
            case SCANNING:
                executeScanning(mascot, s);
                break;
            case CHASING:
                executeChasing(mascot, s);
                break;
            case GRABBING:
                executeGrabbing(mascot, s);
                break;
            case CARRYING:
                executeCarrying(mascot, s);
                break;
            case THROWING:
                executeThrowing(mascot, s);
                break;
            case CELEBRATING:
                executeCelebrating(mascot, s);
                break;
            default:
                break;
        }
    }

    private void executeScanning(Mascot mascot, ThrowState s) {
        // 環境クラスの新しいメソッドを使用してターゲットを探す
        s.targetWindow = Environment.getInstance().findReachableTargetWindow(
                mascot.getX(), mascot.getY(), SEARCH_RADIUS);

        if (s.targetWindow != null) {
            System.out.println("[ThrowAction] Re-Target found: " + s.targetWindow);
            s.state = State.CHASING;
            mascot.setTargetWindow(s.targetWindow); // 壁判定から除外
        } else {
            s.state = State.FINISHED; // 見つからなければ即終了
        }
    }

    private void executeChasing(Mascot mascot, ThrowState s) {
        // ターゲットの状態監視
        if (!isValidTarget(s.targetWindow)) {
            System.out.println("[ThrowAction] Target lost during chase.");
            abort(mascot, s);
            return;
        }

        NeoRect rect = NativeWindowUtil.getWindowRect(s.targetWindow);
        if (rect == null) {
            abort(mascot, s);
            return;
        }

        // ターゲットまでの距離と目標X座標を計算
        // ウィンドウの左端か右端、近い方を目指す
        int distToLeft = Math.abs(mascot.getX() - rect.left());
        int distToRight = Math.abs(mascot.getX() - rect.right());

        // 少し内側を目標にする (ウィンドウ枠をしっかり掴むため)
        int targetX = (distToLeft < distToRight) ? rect.left() + 10 : rect.right() - 10;
        int distance = targetX - mascot.getX();

        // アニメーション
        if (s.animation != null) {
            mascot.setAnimation(s.animation);
            s.animation.tick(40);
        }

        // 壁チェック (進行方向に壁がある場合、到達不能とみなして中止)
        boolean movingRight = distance > 0;
        boolean blocked = (movingRight && mascot.isHittingRightWall()) ||
                (!movingRight && mascot.isHittingLeftWall());

        if (blocked && Math.abs(distance) > REACH_DISTANCE * 2) {
            System.out.println("[ThrowAction] Blocked by wall. Aborting chase.");
            abort(mascot, s);
            return;
        }

        // 到達チェック
        if (Math.abs(distance) <= REACH_DISTANCE) {
            s.state = State.GRABBING;
            s.tickCounter = 0;

            // 掴み情報の初期化
            s.initialWindowX = rect.left();
            s.initialWindowY = rect.top();
            s.windowWidth = rect.width();
            s.windowHeight = rect.height();

            // オフセット計算: マスコットの位置からみたウィンドウ原点
            s.grabOffsetX = rect.left() - mascot.getX();
            s.grabOffsetY = rect.top() - mascot.getY();

            mascot.setHoldingWindow(s.targetWindow);
            mascot.setTargetWindow(null); // targetWindow設定は解除(holdingWindowとして扱わせる)
        } else {
            // 移動
            mascot.setLookRight(movingRight);
            int move = movingRight ? CHASE_SPEED : -CHASE_SPEED;
            mascot.setX(mascot.getX() + move);
        }
    }

    private void executeGrabbing(Mascot mascot, ThrowState s) {
        // 掴みモーション (今は専用アニメーションがないので、とりあえず数フレーム待つ)
        // 必要に応じて "Grab" や "Lifting" のようなアニメーションに切り替えることも可能

        s.tickCounter++;
        if (s.tickCounter >= GRAB_DURATION) {
            s.state = State.CARRYING;

            // 運ぶ方向を決定 (壁際なら反対側へ)
            if (mascot.isHittingLeftWall()) {
                mascot.setLookRight(true);
            } else if (mascot.isHittingRightWall()) {
                mascot.setLookRight(false);
            } else {
                // デフォルトは今の向きで
            }
        }
    }

    private void executeCarrying(Mascot mascot, ThrowState s) {
        // 進行方向の壁に接触したら投げに移行
        boolean reachedWall = (mascot.isLookRight() && mascot.isHittingRightWall()) ||
                (!mascot.isLookRight() && mascot.isHittingLeftWall());

        if (reachedWall) {
            s.state = State.THROWING;

            // 初速の設定
            // 進行方向に向かって投げる
            int direction = mascot.isLookRight() ? 1 : -1;
            s.velocityX = direction * 20.0;
            s.velocityY = -30.0; // 上に放り投げる

            mascot.setHoldingWindow(null); // 離す
            return;
        }

        // 移動
        int move = mascot.isLookRight() ? CARRY_SPEED : -CARRY_SPEED;
        mascot.setX(mascot.getX() + move);

        // アニメーション (歩行と同じでよい)
        if (s.animation != null) {
            mascot.setAnimation(s.animation);
            s.animation.tick(40);
        }

        // ウィンドウを追従させる
        if (NativeWindowUtil.isWindow(s.targetWindow)) {
            int winX = mascot.getX() + s.grabOffsetX;
            // Y座標はマスコットの動きに合わせて少し持ち上げる演出を入れても良いが
            // ここではシンプルに固定オフセットで追従
            int winY = mascot.getY() + s.grabOffsetY;

            NativeWindowUtil.moveWindow(s.targetWindow, winX, winY, s.windowWidth, s.windowHeight, true);
        } else {
            // 運搬中にウィンドウが消えたら中止
            abort(mascot, s);
        }
    }

    private void executeThrowing(Mascot mascot, ThrowState s) {
        if (!NativeWindowUtil.isWindow(s.targetWindow)) {
            s.state = State.FINISHED;
            return;
        }

        NeoRect rect = NativeWindowUtil.getWindowRect(s.targetWindow);
        if (rect == null) {
            s.state = State.FINISHED;
            return;
        }

        // 物理演算
        int currentX = rect.left();
        int currentY = rect.top();

        int nextX = (int) (currentX + s.velocityX);
        int nextY = (int) (currentY + s.velocityY);

        NativeWindowUtil.moveWindow(s.targetWindow, nextX, nextY, s.windowWidth, s.windowHeight, true);

        // 空気抵抗
        s.velocityX *= 0.95;
        // 重力
        s.velocityY += 2.0;

        // 画面外判定
        boolean outOfBounds = (nextY > 3000) || (nextX < -3000) || (nextX > 5000); // 雑な判定だが既存コード準拠
        if (outOfBounds) {
            // RestoreMangerに登録して復帰予約
            WindowRestorationManager.getInstance().addThrownWindow(s.targetWindow, s.initialWindowX, s.initialWindowY,
                    s.windowWidth, s.windowHeight);

            s.state = State.CELEBRATING;
            s.tickCounter = 0;

            // 勝利ポーズタイマー設定
            int duration = (s.celebrateAnimation != null) ? s.celebrateAnimation.getTotalDuration() : 2000;
            s.tickCounter = duration;
        }
    }

    private void executeCelebrating(Mascot mascot, ThrowState s) {
        if (s.celebrateAnimation != null) {
            mascot.setAnimation(s.celebrateAnimation);
            s.celebrateAnimation.tick(40);
        }

        s.tickCounter -= 40;
        if (s.tickCounter <= 0) {
            s.state = State.FINISHED;
        }
    }

    private boolean isValidTarget(MemorySegment window) {
        return NativeWindowUtil.isWindow(window) &&
                NativeWindowUtil.isWindowVisible(window) &&
                !NativeWindowUtil.isIconic(window);
    }

    private void abort(Mascot mascot, ThrowState s) {
        mascot.setHoldingWindow(null);
        mascot.setTargetWindow(null);
        s.state = State.FINISHED;
    }

    @Override
    public boolean hasNext() {
        if (lastMascot == null)
            return false;
        ThrowState s = states.get(lastMascot);
        return s != null && s.state != State.FINISHED;
    }

    @Override
    public void reset() {
        // 個別の状態リセットはexecute内のFINISHEDチェックで行う
    }
}
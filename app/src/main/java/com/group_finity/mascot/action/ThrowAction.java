package com.group_finity.mascot.action;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * ターゲットウィンドウに向かって移動し、投げるアクション。
 */
public class ThrowAction implements Action {

    private enum State {
        APPROACHING,
        LIFTING,
        CARRYING,
        THROWING,
        CELEBRATING,
        FINISHED
    }

    // マスコットごとの状態を保持するクラス
    private static class ThrowState {
        State state = State.APPROACHING;
        MemorySegment targetWindow;
        boolean initialized = false;
        Animation animation; // マスコットごとのアニメーションインスタンス
        Animation celebrateAnimation; // 勝利ポーズ用アニメーション

        // 持ち上げアニメーション用
        int liftPhase = 0;
        int initialWindowX, initialWindowY, windowWidth, windowHeight;
        int grabOffsetX; // 掴んだ時のマスコットとのX座標差分
        int liftStartY; // 持ち上げ開始時のマスコットY座標

        // 投擲用
        int throwVelocityX;
        int throwVelocityY;

        // 勝利ポーズ用
        int celebrateTime;

        ThrowState(Animation animation, Animation celebrateAnimation) {
            this.animation = animation;
            this.celebrateAnimation = celebrateAnimation;
        }

        void reset() {
            state = State.APPROACHING;
            targetWindow = null;
            initialized = false;
            if (animation != null)
                animation.reset();
            liftPhase = 0;
            initialWindowX = 0;
            initialWindowY = 0;
            windowWidth = 0;
            windowHeight = 0;
            grabOffsetX = 0;
            liftStartY = 0;
            throwVelocityX = 0;
            throwVelocityY = 0;
            celebrateTime = 0;
        }
    }

    private final Animation animationTemplate; // アニメーション生成用テンプレート
    private Animation celebrationAnimationTemplate; // 勝利ポーズ用テンプレート
    private final Map<Mascot, ThrowState> states = new WeakHashMap<>();
    private Mascot lastMascot; // hasNext()判定用

    private static final int REACH_DISTANCE = 4; // 到達判定距離 (ほぼ重なるまで近づく)
    private static final int SEARCH_RADIUS = 600; // 探索半径
    private static final int SPEED = 8; // 移動速度 (倍増)
    private static final int LIFT_DURATION = 20; // 持ち上げにかかるフレーム数

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

        // 以前の実行で終了していた場合はリセットして再開
        if (s.state == State.FINISHED) {
            s.reset();
            mascot.setTargetWindow(null);
        }

        if (!s.initialized && s.state == State.APPROACHING) {
            // ターゲット探索
            s.targetWindow = Environment.getInstance().findTargetWindow(
                    mascot.getX(), mascot.getY(),
                    100,
                    SEARCH_RADIUS,
                    mascot.getFloorWindow());

            if (s.targetWindow == null) {
                s.state = State.FINISHED;
                mascot.setTargetWindow(null);
                return;
            }
            System.out.println("[ThrowAction] Target found: " + s.targetWindow);
            s.initialized = true;
            mascot.setTargetWindow(s.targetWindow); // 壁判定から除外
        }

        // ターゲットの存在確認
        if (!NativeWindowUtil.isWindow(s.targetWindow) || !NativeWindowUtil.isWindowVisible(s.targetWindow)
                || NativeWindowUtil.isIconic(s.targetWindow)) {
            System.out.println("[ThrowAction] Target lost.");
            s.state = State.FINISHED;
            mascot.setTargetWindow(null);
            return;
        }

        if (s.state == State.APPROACHING) {
            // ターゲットの位置取得
            NeoRect rect = NativeWindowUtil.getWindowRect(s.targetWindow);

            // マスコットに近い方の端をターゲットにする
            int mascotX = mascot.getX();
            int distLeft = Math.abs(mascotX - rect.left());
            int distRight = Math.abs(mascotX - rect.right());

            // ウィンドウの内側へ少し入り込んだ位置をターゲットとする
            int innerOffset = 10;
            int targetX = (distLeft < distRight) ? rect.left() + innerOffset : rect.right() - innerOffset;

            // 距離計算
            int distance = targetX - mascotX;

            // アニメーション更新
            if (s.animation != null) {
                mascot.setAnimation(s.animation);
                s.animation.tick(40);
            }

            // 壁に阻まれているか判定
            boolean movingRight = distance > 0;
            boolean blockedByWall = (movingRight && mascot.isHittingRightWall())
                    || (!movingRight && mascot.isHittingLeftWall());

            // 移動または到達判定
            if (Math.abs(distance) <= REACH_DISTANCE || blockedByWall) {
                s.state = State.LIFTING;
                s.liftPhase = 0;

                // 持ち上げ開始時の情報を記録
                s.initialWindowX = rect.left();
                s.initialWindowY = rect.top();
                s.windowWidth = rect.width();
                s.windowHeight = rect.height();

                // 掴んだ位置のオフセットを保存 (X軸)
                s.grabOffsetX = s.initialWindowX - mascot.getX();

                // 持ち上げ開始時のマスコットY座標を保存
                s.liftStartY = mascot.getY();

                mascot.setHoldingWindow(s.targetWindow);
                mascot.setTargetWindow(null); // 掴んだ後はholdingWindowとして除外されるのでtargetWindowは解除
            } else {
                mascot.setLookRight(movingRight);
                int move = mascot.isLookRight() ? SPEED : -SPEED;
                mascot.setX(mascot.getX() + move);
            }
        } else if (s.state == State.LIFTING) {
            // 持ち上げアニメーション
            s.liftPhase++;

            // 目標座標
            int destX = mascot.getX() + s.grabOffsetX;
            int destY = s.liftStartY - 90 - s.windowHeight;

            // 補間計算 (EaseOut)
            double progress = (double) s.liftPhase / LIFT_DURATION;
            if (progress > 1.0)
                progress = 1.0;
            progress = Math.sin(progress * Math.PI / 2);

            int currentX = (int) (s.initialWindowX + (destX - s.initialWindowX) * progress);
            int currentY = (int) (s.initialWindowY + (destY - s.initialWindowY) * progress);

            NativeWindowUtil.moveWindow(s.targetWindow, currentX, currentY, s.windowWidth, s.windowHeight, true);

            if (s.liftPhase >= LIFT_DURATION) {
                boolean hitLeft = mascot.isHittingLeftWall();
                boolean hitRight = mascot.isHittingRightWall();
                s.state = State.CARRYING;

                // 壁際にいる場合は、反対側の壁に向かって運ぶように向きを変える
                if (hitLeft) {
                    mascot.setLookRight(true);
                } else if (hitRight) {
                    mascot.setLookRight(false);
                }
            }
        } else if (s.state == State.CARRYING) {
            // 進行方向の壁に到達したら終了（投げるフェーズへ）
            boolean reachedWall = (mascot.isLookRight() && mascot.isHittingRightWall()) ||
                    (!mascot.isLookRight() && mascot.isHittingLeftWall());

            if (reachedWall) {
                s.state = State.THROWING;

                // 投げる方向
                int direction = mascot.isHittingRightWall() ? 1 : -1;
                s.throwVelocityX = direction * 25; // 初速X
                s.throwVelocityY = -35; // 初速Y

                mascot.setHoldingWindow(null); // マスコットの手から離す
                return;
            }

            // 移動
            int move = mascot.isLookRight() ? SPEED : -SPEED;
            mascot.setX(mascot.getX() + move);

            // アニメーション更新
            if (s.animation != null) {
                mascot.setAnimation(s.animation);
                s.animation.tick(40);
            }

            // ウィンドウ同期
            int currentX = mascot.getX() + s.grabOffsetX;
            int currentY = mascot.getY() - 90 - s.windowHeight;
            NativeWindowUtil.moveWindow(s.targetWindow, currentX, currentY, s.windowWidth, s.windowHeight, true);
        } else if (s.state == State.THROWING) {
            // ウィンドウの現在位置を取得
            NeoRect rect = NativeWindowUtil.getWindowRect(s.targetWindow);
            if (rect.width() == 0 && rect.height() == 0) { // Failed to get rect, maybe window closed
                if (!NativeWindowUtil.isWindow(s.targetWindow)) {
                    s.state = State.FINISHED;
                    return;
                }
            }

            int nextX = rect.left() + s.throwVelocityX;
            int nextY = rect.top() + s.throwVelocityY;

            NativeWindowUtil.moveWindow(s.targetWindow, nextX, nextY, s.windowWidth, s.windowHeight, true);

            // 重力適用
            s.throwVelocityY += 1;

            // 画面外判定
            boolean outOfBounds = (nextY > 3000) || (nextX < -3000) || (nextX > 5000);
            if (outOfBounds) {
                Main.getInstance().addThrownWindow(s.targetWindow, s.initialWindowX, s.initialWindowY, s.windowWidth,
                        s.windowHeight);
                mascot.setTargetWindow(null);

                // 勝利ポーズへ移行
                s.state = State.CELEBRATING;
                if (s.celebrateAnimation != null) {
                    s.celebrateTime = s.celebrateAnimation.getTotalDuration();
                } else {
                    s.celebrateTime = 2000;
                }
            }
        } else if (s.state == State.CELEBRATING) {
            if (s.celebrateAnimation != null) {
                mascot.setAnimation(s.celebrateAnimation);
                s.celebrateAnimation.tick(40);
            }
            s.celebrateTime -= 40;
            if (s.celebrateTime <= 0) {
                s.state = State.FINISHED;
            }
        }
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
    }
}
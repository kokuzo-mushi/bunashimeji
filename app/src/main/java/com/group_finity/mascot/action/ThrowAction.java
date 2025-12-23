package com.group_finity.mascot.action;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.awt.Point;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * ターゲットウィンドウに向かって移動し、投げる（予定）アクション。
 * 現在はプロトタイプとして「ターゲットへの移動」のみを実装。
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
        HWND targetWindow;
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
        
        ThrowState(XmlAnimation xmlAnimation) {
            if (xmlAnimation != null) {
                this.animation = new Animation(
                        xmlAnimation.getPoses().stream()
                                .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                                .collect(Collectors.toList())
                );
            }
            // 勝利ポーズ（座り）のアニメーションを作成
            this.celebrateAnimation = new Animation(Collections.singletonList(
                    new Pose("shime11.png", 2000, new Point(64, 128))
            ));
        }

        void reset() {
            state = State.APPROACHING;
            targetWindow = null;
            initialized = false;
            if (animation != null) animation.reset();
            liftPhase = 0;
            initialWindowX = 0; initialWindowY = 0; windowWidth = 0; windowHeight = 0;
            grabOffsetX = 0; liftStartY = 0;
            throwVelocityX = 0; throwVelocityY = 0;
            celebrateTime = 0;
        }
    }

    private final XmlAnimation xmlAnimation; // アニメーション生成用テンプレート
    private final Map<Mascot, ThrowState> states = new WeakHashMap<>();
    private Mascot lastMascot; // hasNext()判定用
    
    private static final int REACH_DISTANCE = 4; // 到達判定距離 (ほぼ重なるまで近づく)
    private static final int SEARCH_RADIUS = 600; // 探索半径
    private static final int SPEED = 8; // 移動速度 (倍増)
    private static final int LIFT_DURATION = 20; // 持ち上げにかかるフレーム数

    public ThrowAction(XmlAnimation xmlAnimation) {
        this.xmlAnimation = xmlAnimation;
    }

    private ThrowState getState(Mascot mascot) {
        return states.computeIfAbsent(mascot, m -> new ThrowState(xmlAnimation));
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
                // System.out.println("[ThrowAction] No target found.");
                s.state = State.FINISHED;
                mascot.setTargetWindow(null);
                return;
            }
            System.out.println("[ThrowAction] Target found: " + s.targetWindow + " (Mascot: " + mascot.getX() + ", " + mascot.getY() + ")");
            s.initialized = true;
            mascot.setTargetWindow(s.targetWindow); // 壁判定から除外するためにセット
        }

        // ターゲットの存在確認
        // 最小化(IsIconic)された場合もターゲットから外す
        if (!Win32.INSTANCE.IsWindow(s.targetWindow) || !Win32.INSTANCE.IsWindowVisible(s.targetWindow) || Win32.INSTANCE.IsIconic(s.targetWindow)) {
             System.out.println("[ThrowAction] Target lost: Window invalid, invisible, or minimized.");
             s.state = State.FINISHED;
             mascot.setTargetWindow(null);
             return;
        }

        if (s.state == State.APPROACHING) {
            // ターゲットの位置取得
            RECT rect = new RECT();
            Win32.INSTANCE.GetWindowRect(s.targetWindow, rect);
            
            // マスコットに近い方の端をターゲットにする
            int mascotX = mascot.getX();
            int distLeft = Math.abs(mascotX - rect.left);
            int distRight = Math.abs(mascotX - rect.right);
            
            // ウィンドウの内側へ少し入り込んだ位置をターゲットとする
            int innerOffset = 10;
            int targetX = (distLeft < distRight) ? rect.left + innerOffset : rect.right - innerOffset;
            
            // 距離計算
            int distance = targetX - mascotX;
            
            // アニメーション更新
            if (s.animation != null) {
                mascot.setAnimation(s.animation);
                s.animation.tick(40);
            }

            // 壁に阻まれているか判定
            boolean movingRight = distance > 0;
            boolean blockedByWall = (movingRight && mascot.isHittingRightWall()) || (!movingRight && mascot.isHittingLeftWall());

            // 移動または到達判定
            if (Math.abs(distance) <= REACH_DISTANCE || blockedByWall) {
                System.out.printf("[ThrowAction] Reached target (Dist: %d, Blocked: %b). Starting lift.%n", distance, blockedByWall);
                System.out.printf("  [PosDebug] MascotX: %d, Window: [%d, %d], TargetX: %d, Offset: %d, DistL: %d, DistR: %d%n",
                        mascotX, rect.left, rect.right, targetX, innerOffset, distLeft, distRight);
                s.state = State.LIFTING;
                s.liftPhase = 0;
                
                // 持ち上げ開始時の情報を記録
                s.initialWindowX = rect.left;
                s.initialWindowY = rect.top;
                s.windowWidth = rect.right - rect.left;
                s.windowHeight = rect.bottom - rect.top;
                
                // 掴んだ位置のオフセットを保存 (X軸)
                s.grabOffsetX = s.initialWindowX - mascot.getX();
                
                // 持ち上げ開始時のマスコットY座標を保存 (Y軸の基準固定)
                s.liftStartY = mascot.getY();
                System.out.printf("[ThrowAction] Lift params: LiftStartY=%d, WindowTop=%d, GrabOffset=%d%n", s.liftStartY, rect.top, s.grabOffsetX);
                
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
            
            // 目標座標: 
            // X: 掴んだ時の相対位置を維持
            // Y: マスコットの頭上(Y-90)にウィンドウの下端が来るように
            int destX = mascot.getX() + s.grabOffsetX;
            int destY = s.liftStartY - 90 - s.windowHeight;
            
            // 補間計算 (EaseOut)
            double progress = (double) s.liftPhase / LIFT_DURATION;
            if (progress > 1.0) progress = 1.0;
            progress = Math.sin(progress * Math.PI / 2);

            int currentX = (int) (s.initialWindowX + (destX - s.initialWindowX) * progress);
            int currentY = (int) (s.initialWindowY + (destY - s.initialWindowY) * progress);
            
            System.out.printf("[ThrowAction] Lifting: Phase=%d/%d, Progress=%.2f, DestY=%d, CurY=%d (InitY=%d, StartY=%d, H=%d)%n",
                    s.liftPhase, LIFT_DURATION, progress, destY, currentY, s.initialWindowY, s.liftStartY, s.windowHeight);

            User32.INSTANCE.MoveWindow(s.targetWindow, currentX, currentY, s.windowWidth, s.windowHeight, true);
            
            if (s.liftPhase >= LIFT_DURATION) {
                boolean hitLeft = mascot.isHittingLeftWall();
                boolean hitRight = mascot.isHittingRightWall();
                System.out.printf("[ThrowAction] Lift complete. Wall: L=%b, R=%b. LookRight: %b%n", hitLeft, hitRight, mascot.isLookRight());
                s.state = State.CARRYING;

                // 壁際にいる場合は、反対側の壁に向かって運ぶように向きを変える
                if (hitLeft) {
                    mascot.setLookRight(true);
                    System.out.println("[ThrowAction] Turn Right (was hitting left)");
                } else if (hitRight) {
                    mascot.setLookRight(false);
                    System.out.println("[ThrowAction] Turn Left (was hitting right)");
                }
            }
        } else if (s.state == State.CARRYING) {
            // 進行方向の壁に到達したら終了（投げるフェーズへ）
            boolean reachedWall = (mascot.isLookRight() && mascot.isHittingRightWall()) ||
                                  (!mascot.isLookRight() && mascot.isHittingLeftWall());

            if (reachedWall) {
                System.out.printf("[ThrowAction] Reached wall. HitR=%b, LookR=%b. Throwing!%n", mascot.isHittingRightWall(), mascot.isLookRight());
                s.state = State.THROWING;

                // 投げる方向（壁の外側）: 左壁なら左(-1)、右壁なら右(1)
                int direction = mascot.isHittingRightWall() ? 1 : -1;
                s.throwVelocityX = direction * 25; // 初速X (より遠くへ)
                s.throwVelocityY = -35; // 初速Y (より高く)

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

            // ウィンドウ同期（頭上に維持）
            int currentX = mascot.getX() + s.grabOffsetX;
            int currentY = mascot.getY() - 90 - s.windowHeight;
            User32.INSTANCE.MoveWindow(s.targetWindow, currentX, currentY, s.windowWidth, s.windowHeight, true);
        } else if (s.state == State.THROWING) {
            // ウィンドウの現在位置を取得
            RECT rect = new RECT();
            if (Win32.INSTANCE.GetWindowRect(s.targetWindow, rect) == 0) {
                s.state = State.FINISHED;
                return;
            }

            int nextX = rect.left + s.throwVelocityX;
            int nextY = rect.top + s.throwVelocityY;

            User32.INSTANCE.MoveWindow(s.targetWindow, nextX, nextY, s.windowWidth, s.windowHeight, true);

            // 重力適用
            s.throwVelocityY += 1; // 重力を弱めて滞空時間を延ばす

            // 画面外判定 (簡易的に十分遠くへ行ったら終了とする)
            boolean outOfBounds = (nextY > 3000) || (nextX < -3000) || (nextX > 5000);
            if (outOfBounds) {
                System.out.printf("[ThrowAction] Window thrown away. (Pos: %d, %d)%n", nextX, nextY);
                Main.getInstance().addThrownWindow(s.targetWindow, s.initialWindowX, s.initialWindowY, s.windowWidth, s.windowHeight);
                mascot.setTargetWindow(null);
                
                // 勝利ポーズへ移行
                s.state = State.CELEBRATING;
                s.celebrateTime = 2000; // 2秒間
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
        if (lastMascot == null) return false;
        ThrowState s = states.get(lastMascot);
        return s != null && s.state != State.FINISHED;
    }

    @Override
    public void reset() {
        // 共有インスタンスのため、特定の状態リセットは行わない
        // execute() 内で初期化判定を行う
    }
}
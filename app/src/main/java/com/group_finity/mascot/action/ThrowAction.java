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
        FINISHED
    }

    // マスコットごとの状態を保持するクラス
    private static class ThrowState {
        State state = State.APPROACHING;
        HWND targetWindow;
        boolean initialized = false;
        Animation animation; // マスコットごとのアニメーションインスタンス

        // 持ち上げアニメーション用
        int liftPhase = 0;
        int initialWindowX, initialWindowY, windowWidth, windowHeight;
        int grabOffsetX; // 掴んだ時のマスコットとのX座標差分

        // 投擲用
        int throwVelocityX;
        int throwVelocityY;
        
        ThrowState(XmlAnimation xmlAnimation) {
            if (xmlAnimation != null) {
                this.animation = new Animation(
                        xmlAnimation.getPoses().stream()
                                .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                                .collect(Collectors.toList())
                );
            }
        }

        void reset() {
            state = State.APPROACHING;
            targetWindow = null;
            initialized = false;
            if (animation != null) animation.reset();
            liftPhase = 0;
            initialWindowX = 0; initialWindowY = 0; windowWidth = 0; windowHeight = 0;
            grabOffsetX = 0;
            throwVelocityX = 0; throwVelocityY = 0;
        }
    }

    private final XmlAnimation xmlAnimation; // アニメーション生成用テンプレート
    private final Map<Mascot, ThrowState> states = new WeakHashMap<>();
    private Mascot lastMascot; // hasNext()判定用
    
    private static final int REACH_DISTANCE = 32; // 到達判定距離 (より近づいて掴むように調整)
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
        }

        if (!s.initialized && s.state == State.APPROACHING) {
            // ターゲット探索
            s.targetWindow = Environment.getInstance().findTargetWindow(
                    mascot.getX(), mascot.getY(), 
                    100, 
                    SEARCH_RADIUS);
            
            if (s.targetWindow == null) {
                // System.out.println("[ThrowAction] No target found.");
                s.state = State.FINISHED;
                return;
            }
            System.out.println("[ThrowAction] Target found: " + s.targetWindow);
            s.initialized = true;
        }

        // ターゲットの存在確認
        if (!Win32.INSTANCE.IsWindow(s.targetWindow) || !Win32.INSTANCE.IsWindowVisible(s.targetWindow)) {
             System.out.println("[ThrowAction] Target lost.");
             s.state = State.FINISHED;
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
            int targetX = (distLeft < distRight) ? rect.left : rect.right;
            
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
                System.out.println("[ThrowAction] Reached target! Starting lift.");
                s.state = State.LIFTING;
                s.liftPhase = 0;
                
                // 持ち上げ開始時の情報を記録
                s.initialWindowX = rect.left;
                s.initialWindowY = rect.top;
                s.windowWidth = rect.right - rect.left;
                s.windowHeight = rect.bottom - rect.top;
                
                // 掴んだ位置のオフセットを保存 (X軸)
                s.grabOffsetX = s.initialWindowX - mascot.getX();
                
                mascot.setHoldingWindow(s.targetWindow);
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
            // Y: マスコットの頭上(Y-128)にウィンドウの下端が来るように
            int destX = mascot.getX() + s.grabOffsetX;
            int destY = mascot.getY() - 128 - s.windowHeight;
            
            // 補間計算 (EaseOut)
            double progress = (double) s.liftPhase / LIFT_DURATION;
            if (progress > 1.0) progress = 1.0;
            progress = Math.sin(progress * Math.PI / 2);

            int currentX = (int) (s.initialWindowX + (destX - s.initialWindowX) * progress);
            int currentY = (int) (s.initialWindowY + (destY - s.initialWindowY) * progress);
            
            User32.INSTANCE.MoveWindow(s.targetWindow, currentX, currentY, s.windowWidth, s.windowHeight, true);
            
            if (s.liftPhase >= LIFT_DURATION) {
                System.out.println("[ThrowAction] Lift complete. Carrying to wall.");
                s.state = State.CARRYING;
            }
        } else if (s.state == State.CARRYING) {
            // 壁に到達したら終了（投げるフェーズへ）
            if (mascot.isHittingLeftWall() || mascot.isHittingRightWall()) {
                System.out.println("[ThrowAction] Reached wall. Throwing!");
                s.state = State.THROWING;

                // 投げる方向（壁の外側）: 左壁なら左(-1)、右壁なら右(1)
                int direction = mascot.isHittingRightWall() ? 1 : -1;
                s.throwVelocityX = direction * 20; // 初速X
                s.throwVelocityY = -15; // 初速Y (上へ)

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
            int currentY = mascot.getY() - 128 - s.windowHeight;
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
            s.throwVelocityY += 2;

            // 画面外判定 (簡易的に十分遠くへ行ったら終了とする)
            boolean outOfBounds = (nextY > 3000) || (nextX < -3000) || (nextX > 5000);
            if (outOfBounds) {
                System.out.println("[ThrowAction] Window thrown away.");
                Main.getInstance().addThrownWindow(s.targetWindow);
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
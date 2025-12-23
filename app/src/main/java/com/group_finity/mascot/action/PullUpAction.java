package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * 壁の上端からよじ登ってウィンドウの上に着地するアクション。
 */
public class PullUpAction implements Action {

    private static class PullUpState {
        boolean initialized = false;
        int startX, startY;
        int targetX, targetY;
        int duration;
        int time;
        Animation animation;

        PullUpState(Animation animation, int duration) {
            this.animation = animation;
            this.duration = duration;
        }
    }

    private final XmlAnimation xmlAnimation;
    private final int duration;
    private final Map<Mascot, PullUpState> states = new WeakHashMap<>();
    private Mascot lastMascot;

    public PullUpAction(XmlAnimation xmlAnimation, int duration) {
        this.xmlAnimation = xmlAnimation;
        this.duration = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        lastMascot = mascot;
        PullUpState s = states.computeIfAbsent(mascot, m -> {
            Animation anim = null;
            if (xmlAnimation != null) {
                anim = new Animation(xmlAnimation.getPoses().stream()
                        .map(p -> new Pose(p.getImage(), p.getDuration(), p.getImageAnchorPoint()))
                        .collect(Collectors.toList()));
            }
            return new PullUpState(anim, duration);
        });

        if (!s.initialized) {
            s.startX = mascot.getX();
            s.startY = mascot.getY();
            s.time = 0;
            
            // 壁判定を無視して移動できるようにする
            mascot.setIgnoreWalls(true);

            // 壁の情報を取得
            HWND wallWindow = null;
            boolean isLeft = false;
            if (mascot.isHittingLeftWall()) {
                wallWindow = mascot.getLeftWallWindow();
                isLeft = true;
            } else if (mascot.isHittingRightWall()) {
                wallWindow = mascot.getRightWallWindow();
                isLeft = false;
            }

            if (wallWindow != null && Win32.INSTANCE.IsWindow(wallWindow)) {
                RECT rect = new RECT();
                Win32.INSTANCE.GetWindowRect(wallWindow, rect);
                
                // 目標地点: 壁の上端(Y) と 壁の内側(X)
                s.targetY = rect.top;
                // 少し内側に入り込む
                s.targetX = isLeft ? rect.left + 40 : rect.right - 40;
                
                // マスコットの向きを壁に向ける
                mascot.setLookRight(!isLeft);
            } else {
                // 壁が見つからない場合はその場で終了
                s.targetX = s.startX;
                s.targetY = s.startY;
            }
            s.initialized = true;
        }

        // アニメーション進行
        if (s.animation != null) {
            mascot.setAnimation(s.animation);
            s.animation.tick(40);
        }

        s.time += 40;
        double progress = (double) s.time / s.duration;
        if (progress >= 1.0) {
            progress = 1.0;
        }

        // 軌道の調整: Y軸（上昇）を先行させ、後半でX軸（乗り込み）を行う
        // Y: EaseOutSine
        double easeY = Math.sin(progress * Math.PI / 2);
        
        // X: 後半から動き出す (progress^3 などを利用)
        double easeX = Math.pow(progress, 3);

        int currentX = (int) (s.startX + (s.targetX - s.startX) * easeX);
        int currentY = (int) (s.startY + (s.targetY - s.startY) * easeY);

        mascot.setX(currentX);
        mascot.setY(currentY);

        if (progress >= 1.0) {
            // 完了時に接地状態にする
            mascot.setGrounded(true);
            // 壁判定を解除して、次のフレームで床判定されるようにする
            mascot.setHittingLeftWall(false);
            mascot.setHittingRightWall(false);
            mascot.setIgnoreWalls(false);
            states.remove(mascot);
        }
    }

    @Override
    public boolean hasNext() {
        return lastMascot != null && states.containsKey(lastMascot);
    }

    @Override
    public void reset() {
        // 個別の状態リセットはexecute内で行う
    }
}

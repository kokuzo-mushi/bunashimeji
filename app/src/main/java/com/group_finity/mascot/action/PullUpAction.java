package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.util.Map;
import java.util.WeakHashMap;

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
        boolean isLeft; // 壁の方向を記憶するフィールドを追加

        PullUpState(Animation animation, int duration) {
            this.animation = animation;
            this.duration = duration;
        }
    }

    private final Animation animationTemplate;
    private final int duration;
    private final Map<Mascot, PullUpState> states = new WeakHashMap<>();
    private Mascot lastMascot;

    public PullUpAction(Animation animation, int duration) {
        this.animationTemplate = animation;
        this.duration = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        lastMascot = mascot;
        PullUpState s = states.computeIfAbsent(mascot, m -> new PullUpState(animationTemplate, duration));

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
            s.isLeft = isLeft; // 判定結果を保存

            if (wallWindow != null && Win32.INSTANCE.IsWindow(wallWindow)) {
                RECT rect = new RECT();
                Win32.INSTANCE.GetWindowRect(wallWindow, rect);
                
                // 目標地点: 壁の上端(Y) と 壁の内側(X)
                s.targetY = rect.top;
                // 少し内側に入り込む
                int width = rect.right - rect.left;
                int offset = Math.min(40, width / 2);
                
                // isLeft(左壁ヒット)=ウィンドウ右側面にいる -> rect.right側へ着地
                s.targetX = isLeft ? rect.right - offset : rect.left + offset;
                
                // マスコットの向きを壁に向ける
                mascot.setLookRight(!isLeft);
            } else {
                // 壁が見つからない場合はその場で終了
                s.targetX = s.startX;
                s.targetY = s.startY;
            }
            s.initialized = true;
        }

        // 【重要】アクション実行中、向きを強制的に維持する
        // 壁から離れていく動作を含むため、途中で向きが変わらないように固定する
        mascot.setLookRight(!s.isLeft);

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
        // Y: EaseInOutCubic (最初はゆっくり力を込め、中盤で加速し、最後はゆっくり着地することで重量感を出す)
        double easeY = progress < 0.5 ? 4 * Math.pow(progress, 3) : 1 - Math.pow(-2 * progress + 2, 3) / 2;
        
        // X: EaseInQuad (徐々に加速して乗り込む)
        double easeX = Math.pow(progress, 2);

        int currentX = (int) (s.startX + (s.targetX - s.startX) * easeX);
        int currentY = (int) (s.startY + (s.targetY - s.startY) * easeY);

        mascot.setX(currentX);
        mascot.setY(currentY);

        if (progress >= 1.0) {
            // 完了時に接地状態にする
            // Main.javaの接地判定(getY() >= floorY)を確実にパスさせるため、
            // ターゲット位置より1px深く設定する（直後のフレームでMainにより補正される）
            mascot.setX(s.targetX);
            mascot.setY(s.targetY + 1);
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

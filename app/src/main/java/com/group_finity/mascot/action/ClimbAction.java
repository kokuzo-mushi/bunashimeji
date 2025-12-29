package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

/**
 * 壁を登るアクション。
 * 指定された速度で上方向に移動します。
 */
public class ClimbAction implements Action {

    private final Animation animation;
    private final int speed;
    private int timeRemaining;

    public ClimbAction(Animation animation, int speed) {
        this.animation = animation;
        this.speed = speed;
        this.timeRemaining = this.animation.getTotalDuration();
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        if (mascot.isHittingCeiling()) { // 天井に到達
            timeRemaining = 0;
            return;
        }

        // 壁の上端チェック: PullUpActionへ遷移するために、壁の頂上付近でアクションを終了する
        HWND wallWindow = null;
        if (mascot.isHittingLeftWall()) {
            wallWindow = mascot.getLeftWallWindow();
        } else if (mascot.isHittingRightWall()) {
            wallWindow = mascot.getRightWallWindow();
        }

        if (wallWindow != null && Win32.INSTANCE.IsWindow(wallWindow)) {
            RECT rect = new RECT();
            Win32.INSTANCE.GetWindowRect(wallWindow, rect);
            
            // マスコットの頭上（Y - 128）と壁の上端（rect.top）の距離をチェック
            // Main.javaのPullUp発動条件 (distToWallTop < 64) に合わせて終了させる
            int mascotHeadY = mascot.getY() - 128;
            int distToTop = mascotHeadY - rect.top;
            
            if (distToTop < -32) { // 頭が半分くらい出るまで登る
                timeRemaining = 0;
                return;
            }
        }

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        mascot.setY(mascot.getY() - speed);
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.animation.getTotalDuration();
        this.animation.reset();
    }
}
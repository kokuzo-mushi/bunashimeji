package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;

/**
 * 壁を登るアクション。
 * 指定された速度で上方向に移動します。
 */
public class ClimbAction implements Action {

    private final Animation animation;
    private final int speed;
    private int timeRemaining;

    public ClimbAction(Animation animation, int speed) {
        this(animation, speed, 0);
    }

    public ClimbAction(Animation animation, int speed, int duration) {
        this.animation = animation;
        this.speed = speed;
        this.timeRemaining = duration > 0 ? duration : this.animation.getTotalDuration();
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext())
            return;

        // 壁の方向に向き直る
        // 右壁の判定を優先する（両方Trueになるケース対策）
        if (mascot.isHittingRightWall()) {
            mascot.setLookRight(true);
        } else if (mascot.isHittingLeftWall()) {
            mascot.setLookRight(false);
        }

        if (mascot.isHittingCeiling()) { // 天井に到達
            // 座標はいじらず、そのまま終了して次のアクション（WallToCeilingSequence）に任せる
            timeRemaining = 0;
            return;
        }

        // 壁の上端チェック: PullUpActionへ遷移するために、壁の頂上付近でアクションを終了する
        MemorySegment wallWindow = null;
        if (mascot.isHittingLeftWall()) {
            wallWindow = mascot.getLeftWallWindow();
        } else if (mascot.isHittingRightWall()) {
            wallWindow = mascot.getRightWallWindow();
        }

        if (wallWindow != null && NativeWindowUtil.isWindow(wallWindow)) {
            NeoRect rect = NativeWindowUtil.getWindowRect(wallWindow);

            // マスコットの頭上（Y - 128）と壁の上端（rect.top）の距離をチェック
            // Main.javaのPullUp発動条件 (distToWallTop < 64) に合わせて終了させる
            int mascotHeadY = mascot.getY() - 128;
            int distToTop = mascotHeadY - rect.top();

            if (distToTop < -32) { // 頭が半分くらい出るまで登る
                timeRemaining = 0;
                return;
            }
        }

        final int FRAME_DURATION_MS = 16;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        mascot.setY(mascot.getY() - speed);
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.animation.getTotalDuration();
        this.animation.reset();
    }
}
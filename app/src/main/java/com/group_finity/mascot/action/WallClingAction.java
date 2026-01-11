package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;

/**
 * 壁にしがみつくアクション。
 * 速度をゼロにしてその場に留まります。
 */
public class WallClingAction implements Action {
    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private boolean initialized = false;

    public WallClingAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!initialized) {
            mascot.setVelocityX(0);
            mascot.setVelocityY(0);

            boolean hitLeft = mascot.isHittingLeftWall();
            boolean hitRight = mascot.isHittingRightWall();

            // 壁の方向に向き直る (フラグ優先)
            // 両方の壁に接触している場合(挟まっている場合)は、現在の向きを維持する
            if (hitLeft && !hitRight) {
                mascot.setLookRight(false);
            } else if (hitRight && !hitLeft) {
                mascot.setLookRight(true);
            } else if (!hitLeft && !hitRight) {
                // フラグが立っていない場合のフォールバック: 近くの壁を探して向きを決める
                // (アクション遷移の隙間でフラグが落ちているケースへの対策)
                MemorySegment leftWall = mascot.getLeftWallWindow();
                MemorySegment rightWall = mascot.getRightWallWindow();
                int distLeft = Integer.MAX_VALUE;
                int distRight = Integer.MAX_VALUE;

                if (leftWall != null && NativeWindowUtil.isWindow(leftWall)) {
                    NeoRect rect = NativeWindowUtil.getWindowRect(leftWall);
                    distLeft = Math.abs(mascot.getX() - rect.right()); // 左壁の右端とマスコット
                }
                if (rightWall != null && NativeWindowUtil.isWindow(rightWall)) {
                    NeoRect rect = NativeWindowUtil.getWindowRect(rightWall);
                    distRight = Math.abs(mascot.getX() - rect.left()); // 右壁の左端とマスコット
                }

                if (distLeft < distRight && distLeft < 50) {
                    mascot.setLookRight(false);
                } else if (distRight < distLeft && distRight < 50) {
                    mascot.setLookRight(true);
                }
            }
            initialized = true;
        }

        if (!hasNext())
            return;

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        this.animation.reset();
        this.initialized = false;
    }
}
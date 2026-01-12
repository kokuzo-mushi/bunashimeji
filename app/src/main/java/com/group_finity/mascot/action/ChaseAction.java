package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import com.group_finity.mascot.type.NeoPoint;
import java.lang.foreign.MemorySegment;

/**
 * マウスカーソルを追いかけるアクション。
 */
public class ChaseAction implements Action {

    private final Animation animation;
    private final int speed;
    private final int duration;
    private int timeRemaining;
    private final int targetDistance = 5; // 追いついたとみなす距離

    public ChaseAction(Animation animation, int speed, int duration) {
        this.animation = animation;
        this.speed = speed;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext())
            return;

        // 接地していない場合はアクションを終了する（落下させるため）
        if (!mascot.isGrounded()) {
            this.timeRemaining = 0;
            return;
        }

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);

        // マウス位置の取得
        NeoPoint mousePos = NativeWindowUtil.getCursorPos();
        if (mousePos == null) return; // 取得失敗時は何もしない

        int distance = mousePos.x() - mascot.getX();

        // 向きの更新と移動
        if (Math.abs(distance) >= targetDistance) {
            mascot.setLookRight(distance > 0);

            // 壁にぶつかっていて、かつその方向に進もうとしている場合は終了
            if ((mascot.isHittingLeftWall() && !mascot.isLookRight()) ||
                    (mascot.isHittingRightWall() && mascot.isLookRight())) {
                timeRemaining = 0;
                return;
            }

            int move = (distance > 0) ? speed : -speed;
            int nextX = mascot.getX() + move;

            // 床の端チェック: 次の一歩で床から落ちるなら止まる
            if (mascot.isGrounded()) {
                MemorySegment floor = mascot.getFloorWindow();
                if (floor != null && NativeWindowUtil.isWindow(floor)) {
                    NeoRect rect = NativeWindowUtil.getWindowRect(floor);
                    if (nextX <= rect.left() || nextX >= rect.right()) {
                        // 端ギリギリまで移動して止まる
                        int direction = (nextX - mascot.getX() > 0) ? 1 : -1;
                        int edgeX = (direction > 0) ? rect.right() - 5 : rect.left() + 5;
                        mascot.setX(edgeX);

                        timeRemaining = 0;
                        return;
                    }
                }
            }

            mascot.setX(nextX);
        } else {
            // 追いついたら終了
            timeRemaining = 0;
        }

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
    }
}
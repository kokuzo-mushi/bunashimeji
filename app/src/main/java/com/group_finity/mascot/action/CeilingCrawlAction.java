package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.util.Random;

/**
 * 天井を伝って歩くアクション。
 */
public class CeilingCrawlAction implements Action {

    private final Animation animation;
    private final int speed;
    private final Random random = new Random();
    private final int duration;
    private int timeRemaining;
    private int elapsedFrames = 0;
    private boolean finished = false;

    public CeilingCrawlAction(Animation animation, int speed, int duration) {
        this.animation = animation;
        this.speed = speed;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (finished)
            return;

        elapsedFrames++;

        // 1. 壁にぶつかったらアクションを終了し、CornerTurnDownへ移行させる
        if ((mascot.isHittingRightWall() && mascot.isLookRight())
                || (mascot.isHittingLeftWall() && !mascot.isLookRight())) {
            // 向きは変えずに終了
            // 壁に埋まるのを防ぎ、CornerTurnDownActionの回転半径を確保するため、壁から少し内側に戻す
            int backStep = mascot.isLookRight() ? -5 : 5;
            mascot.setX(mascot.getX() + backStep);

            finished = true;
            return;
        }

        // 2. 一定時間経過後にランダムで落下
        // 最初の100フレーム（約4秒）は落下しない
        if (elapsedFrames > 100) {
            // 1/200の確率で落下
            if (random.nextInt(200) == 0) {
                mascot.setY(mascot.getY() + 1); // 天井判定を外す
                finished = true;
                return;
            }
        }

        final int FRAME_DURATION_MS = 16;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);
        int move = mascot.isLookRight() ? speed : -speed;
        mascot.setX(mascot.getX() + move);

        // 3. 天井の端（コーナー）チェック
        // 次の一歩で天井から外れる場合は停止して終了する
        int nextX = mascot.getX() + (mascot.isLookRight() ? speed : -speed);
        boolean reachEdge = false;

        MemorySegment ceilingWindow = mascot.getCeilingWindow();
        if (ceilingWindow != null && NativeWindowUtil.isWindow(ceilingWindow)) {
            // ウィンドウ天井の場合
            NeoRect rect = mascot.getCeilingRect();
            if (rect != null) {
                // マスコットのX座標がウィンドウ範囲外に出そうなら停止
                if (nextX < rect.left() || nextX > rect.left() + rect.width())
                    reachEdge = true;
            }
        } else {
            // 画面天井の場合 (WorkArea)
            NeoRect workArea = mascot.getWorkArea();
            if (workArea != null) {
                if (nextX < workArea.left() || nextX > workArea.left() + workArea.width()) {
                    reachEdge = true;
                }
            }
        }

        if (reachEdge) {
            finished = true;
            return;
        }

        // 時間経過で終了
        this.timeRemaining -= FRAME_DURATION_MS;
        if (this.timeRemaining <= 0) {
            finished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public void reset() {
        this.elapsedFrames = 0;
        this.finished = false;
        this.timeRemaining = this.duration;
        this.animation.reset();
    }
}
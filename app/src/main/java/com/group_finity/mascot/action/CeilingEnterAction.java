package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

/**
 * 天井に到達した後、画面内側へ移動するアクション。
 * Y=45を維持したまま、X軸方向に64px移動します。
 */
public class CeilingEnterAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    private int startX;
    private int targetX;
    private boolean initialized = false;

    public CeilingEnterAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!initialized) {
            startX = mascot.getX();
            // 壁登り中は壁の方を向いている
            // 左壁(LookRight=false)なら右(+64)へ、右壁(LookRight=true)なら左(-64)へ
            int offset = mascot.isLookRight() ? -64 : 64;
            targetX = startX + offset;
            initialized = true;
        }

        if (!hasNext()) return;

        // Main.javaによる天井吸着（Y=10への強制移動）を防ぐ
        mascot.setIgnoreWalls(true);
        
        // Y座標を45に固定（ClimbCeilingActionからの引継ぎを確実に維持）
        mascot.setY(45);

        final int FRAME_DURATION_MS = 16;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);

        double progress = 1.0 - (double) timeRemaining / duration;
        int currentX = (int) (startX + (targetX - startX) * progress);
        mascot.setX(currentX);

        this.timeRemaining -= FRAME_DURATION_MS;
        
        if (this.timeRemaining <= 0) {
            mascot.setX(targetX);
            mascot.setIgnoreWalls(false); // 終了時に戻す
        }
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        this.animation.reset();
        this.initialized = false;
    }
}

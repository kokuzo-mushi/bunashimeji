package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.platform.Platform;

/**
 * 他のマスコットの近くに集まるアクション。
 */
public class GatherAction implements Action {

    private final Animation animation;
    private final int speed;
    private final int duration;
    private int timeRemaining;
    private final int targetDistance = 50; // この距離まで近づいたら停止

    public GatherAction(Animation animation, int speed, int duration) {
        this.animation = animation;
        this.speed = speed;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!hasNext()) return;

        // 最も近いマスコットを探す
        Platform platform = Platform.getInstance();
        Mascot target = null;
        if (platform != null) {
            target = platform.getNearestMascot(mascot);
        }

        // ターゲットがいない、または既に十分近い場合は終了
        if (target == null || Math.abs(target.getX() - mascot.getX()) < targetDistance) {
            timeRemaining = 0;
            return;
        }

        final int FRAME_DURATION_MS = 40;
        mascot.setAnimation(animation);
        animation.tick(FRAME_DURATION_MS);

        // ターゲットに向かって移動
        mascot.setLookRight(target.getX() > mascot.getX());
        int move = mascot.isLookRight() ? speed : -speed;
        mascot.setX(mascot.getX() + move);

        this.timeRemaining -= FRAME_DURATION_MS;
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        this.animation.reset();
    }
}
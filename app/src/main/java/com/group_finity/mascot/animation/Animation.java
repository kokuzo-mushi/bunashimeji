package com.group_finity.mascot.animation;

import java.util.List;

/**
 * ポーズのリストから構成されるアニメーションを表現するクラス。
 * このクラスは自身の再生時間を内部で管理し、外部のタイマーに依存しません。
 */
public class Animation {

    private final List<Pose> poses;
    private final int totalDuration;

    /**
     * アニメーションの内部的な経過時間 (ミリ秒)
     */
    private int time;

    public Animation(List<Pose> poses) {
        this.poses = poses;
        this.totalDuration = poses.stream().mapToInt(Pose::getDuration).sum();
        this.time = 0;
    }

    /**
     * アニメーションの総再生時間（ミリ秒）を返します。
     */
    public int getTotalDuration() {
        return totalDuration;
    }

    /**
     * アニメーションの時間を1フレーム分進めます。
     * @param deltaMillis 前のフレームからの経過時間(ms)
     */
    public void tick(int deltaMillis) {
        this.time += deltaMillis;
    }

    /**
     * 現在の経過時間に対応するポーズを返します。
     * アニメーションはループ再生されます。
     * @return 現在表示すべきポーズ
     */
    public Pose getPose() {
        if (poses.isEmpty()) {
            return null;
        }
        if (getTotalDuration() == 0) {
            return poses.get(0);
        }

        int currentTimeInLoop = this.time % getTotalDuration();
        int accumulatedDuration = 0;
        for (Pose pose : poses) {
            accumulatedDuration += pose.getDuration();
            if (currentTimeInLoop < accumulatedDuration) {
                return pose;
            }
        }
        // 通常は到達しませんが、フォールバックとして最初のポーズを返します
        return poses.get(0);
    }

    /**
     * アニメーションの再生位置を先頭にリセットします。
     */
    public void reset() {
        this.time = 0;
    }
}
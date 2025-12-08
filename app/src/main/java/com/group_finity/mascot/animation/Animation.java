package com.group_finity.mascot.animation;

import java.util.List;

/**
 * ポーズのシーケンスからなるアニメーションを管理します。
 */
public class Animation {
    private final List<Pose> poses;
    private int currentPoseIndex = 0;

    public Animation(List<Pose> poses) {
        this.poses = poses;
    }

    /**
     * 指定された経過時間に基づいて、現在表示すべきポーズを更新します。
     * @param elapsedTime アニメーション開始からの経過時間（ミリ秒）
     */
    public void tick(long elapsedTime) {
        if (poses.isEmpty()) {
            return;
        }

        long time = 0;
        int index = 0;
        for (Pose pose : poses) {
            time += pose.getDuration();
            if (elapsedTime < time) {
                break;
            }
            index++;
        }
        // アニメーションの最後のポーズを超えないようにする
        currentPoseIndex = Math.min(index, poses.size() - 1);
    }

    /**
     * 現在のポーズを返します。
     * @return 現在のポーズ。ポーズがない場合はnull。
     */
    public Pose getCurrentPose() {
        return poses.isEmpty() ? null : poses.get(currentPoseIndex);
    }

    /**
     * アニメーションの総時間を返します。
     * @return 総時間（ミリ秒）
     */
    public int getTotalDuration() {
        return poses.stream().mapToInt(Pose::getDuration).sum();
    }
}
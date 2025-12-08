package com.group_finity.mascot.animation;

/**
 * アニメーションの1フレーム（ポーズ）を表します。
 * 画像と表示時間（ミリ秒）を保持します。
 */
public class Pose {
    private final String imageName;
    private final int duration;

    public Pose(String imageName, int duration) {
        this.imageName = imageName;
        this.duration = duration;
    }

    public String getImageName() {
        return imageName;
    }

    public int getDuration() {
        return duration;
    }
}
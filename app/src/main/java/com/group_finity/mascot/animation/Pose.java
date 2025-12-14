package com.group_finity.mascot.animation;

import java.awt.Point;

public class Pose {
    private final String imageName;
    private final int duration;
    private final Point imageAnchor;

    public Pose(String imageName, int duration, Point imageAnchor) {
        this.imageName = imageName;
        this.duration = duration;
        this.imageAnchor = imageAnchor;
    }

    public Pose(String imageName, int duration) {
        this(imageName, duration, null);
    }

    public String getImageName() {
        return imageName;
    }

    public int getDuration() {
        return duration;
    }

    public Point getImageAnchor() {
        return imageAnchor;
    }
}
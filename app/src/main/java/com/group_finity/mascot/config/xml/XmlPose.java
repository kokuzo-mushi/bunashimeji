package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import java.awt.Point;

/**
 * <Pose> タグに対応するJAXBモデル。
 */
public class XmlPose {

    @XmlAttribute(name = "Image")
    private String image;

    @XmlAttribute(name = "Duration")
    private int duration;

    @XmlAttribute(name = "ImageAnchor")
    private String imageAnchor;

    public String getImage() {
        return image;
    }

    public int getDuration() {
        return duration;
    }

    public Point getImageAnchorPoint() {
        if (imageAnchor == null || imageAnchor.isEmpty()) {
            return null;
        }
        String[] parts = imageAnchor.split(",");
        if (parts.length != 2) return null;
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }
}
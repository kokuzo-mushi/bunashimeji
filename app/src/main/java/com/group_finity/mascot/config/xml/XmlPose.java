package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * <Pose> タグに対応するJAXBモデル。
 */
public class XmlPose {

    @XmlAttribute(name = "Image", required = true)
    private String image;

    @XmlAttribute(name = "Duration", required = true)
    private int duration;

    public String getImage() { return image; }
    public int getDuration() { return duration; }
}
package com.group_finity.mascot.config.xml;

import java.awt.Point;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * アニメーションの1フレーム（ポーズ）を表すXML要素のマッピングクラス。
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlPose {

    @XmlAttribute(name = "Image")
    public String image;

    @XmlAttribute(name = "Duration")
    public int duration;

    @XmlAttribute(name = "X")
    public int x;

    @XmlAttribute(name = "Y")
    public int y;

    public String getImage() { return image; }
    
    public int getDuration() { return duration; }

    public Point getImageAnchorPoint() {
        return new Point(x, y);
    }
}
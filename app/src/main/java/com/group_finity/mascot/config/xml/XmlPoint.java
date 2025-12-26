package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * 座標を表すXML要素のマッピングクラス。
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlPoint {

    @XmlAttribute(name = "X")
    public int x;

    @XmlAttribute(name = "Y")
    public int y;

    public int getX() { return x; }
    public int getY() { return y; }
}
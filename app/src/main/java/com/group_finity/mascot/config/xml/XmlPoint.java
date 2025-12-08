package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * <Point> タグに対応するJAXBモデル。
 */
public class XmlPoint {

    @XmlAttribute(name = "X", required = true)
    private int x;

    @XmlAttribute(name = "Y", required = true)
    private int y;

    public int getX() { return x; }
    public int getY() { return y; }
}
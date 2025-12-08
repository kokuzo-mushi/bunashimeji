package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * <ActionReference> タグに対応するJAXBモデル。
 */
public class XmlActionReference {

    @XmlAttribute(name = "Name", required = true)
    private String name;

    public String getName() { return name; }
}
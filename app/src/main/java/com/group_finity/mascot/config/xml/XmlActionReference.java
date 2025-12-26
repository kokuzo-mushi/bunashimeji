package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * <ActionReference> タグに対応するJAXBモデル。
 * BehaviorやSequenceActionなどで、他のアクションを名前で参照する際に使用します。
 */
@XmlRootElement(name = "ActionReference")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlActionReference {

    @XmlAttribute(name = "Name", required = true)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
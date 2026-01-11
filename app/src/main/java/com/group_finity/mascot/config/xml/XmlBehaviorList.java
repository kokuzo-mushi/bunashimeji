package com.group_finity.mascot.config.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * behaviors.xml のルート要素を表すクラス。
 */
@XmlRootElement(name = "Behaviors")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlBehaviorList {

    @XmlElement(name = "Behavior")
    private List<XmlBehavior> behaviors = new ArrayList<>();

    public List<XmlBehavior> getBehaviors() {
        return behaviors;
    }
}
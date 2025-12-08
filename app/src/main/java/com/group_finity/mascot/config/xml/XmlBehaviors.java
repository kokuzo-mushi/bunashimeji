package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * behaviors.xml のルート要素 <Behaviors> に対応するJAXBモデル。
 */
@XmlRootElement(name = "Behaviors")
public class XmlBehaviors {

    @XmlElement(name = "Behavior")
    private List<XmlBehavior> behaviors;

    public List<XmlBehavior> getBehaviors() { return behaviors; }
}
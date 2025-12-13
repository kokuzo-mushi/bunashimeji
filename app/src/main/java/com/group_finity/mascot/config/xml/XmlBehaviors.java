package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "Behaviors")
public class XmlBehaviors {
    private List<XmlBehavior> behaviors = new ArrayList<>();

    @XmlElement(name = "Behavior")
    public List<XmlBehavior> getBehaviors() {
        return behaviors;
    }

    public void setBehaviors(List<XmlBehavior> behaviors) {
        this.behaviors = behaviors;
    }
}
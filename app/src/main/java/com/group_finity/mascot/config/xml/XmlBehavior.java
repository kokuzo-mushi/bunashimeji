package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "Behavior")
public class XmlBehavior {

    private String name;
    private String condition;
    private boolean hidden;
    private List<XmlActionReference> actionReferences = new ArrayList<>();

    @XmlAttribute(name = "Name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "Condition")
    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    @XmlAttribute(name = "Hidden")
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @XmlElement(name = "ActionReference")
    public List<XmlActionReference> getActionReferences() {
        return actionReferences;
    }

    public void setActionReferences(List<XmlActionReference> actionReferences) {
        this.actionReferences = actionReferences;
    }
}
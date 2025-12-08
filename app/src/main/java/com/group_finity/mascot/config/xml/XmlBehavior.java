package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * <Behavior> タグに対応するJAXBモデル。
 */
public class XmlBehavior {

    @XmlAttribute(name = "Condition", required = true)
    private String condition;

    @XmlElement(name = "ActionReference", required = true)
    private List<XmlActionReference> actionReferences;

    public String getCondition() { return condition; }
    public List<XmlActionReference> getActionReferences() { return actionReferences; }
}
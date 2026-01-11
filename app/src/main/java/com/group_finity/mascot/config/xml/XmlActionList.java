package com.group_finity.mascot.config.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * actions.xml のルート要素を表すクラス。
 */
@XmlRootElement(name = "Actions")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlActionList {

    @XmlElement(name = "Action")
    private List<XmlAction> actions = new ArrayList<>();

    public List<XmlAction> getActions() {
        return actions;
    }
}
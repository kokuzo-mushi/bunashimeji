package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * actions.xml のルート要素 <Actions> に対応するJAXBモデル。
 */
@XmlRootElement(name = "Actions")
public class XmlActions {

    @XmlElement(name = "Action")
    private List<XmlAction> actions;

    public List<XmlAction> getActions() { return actions; }
}
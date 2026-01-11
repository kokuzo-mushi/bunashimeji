package com.group_finity.mascot.config.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * 振る舞い定義を表すXML要素のマッピングクラス。
 */
@XmlRootElement(name = "Behavior")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlBehavior {

    @XmlAttribute(name = "Name")
    private String name;

    @XmlAttribute(name = "Frequency")
    private Integer frequency;

    @XmlAttribute(name = "Hidden")
    private boolean hidden;

    @XmlElement(name = "Condition")
    private String condition;

    @XmlElement(name = "ActionReference")
    private List<XmlActionReference> actionReferences = new ArrayList<>();

    @XmlElement(name = "NextBehavior")
    private List<XmlNextBehavior> nextBehaviors = new ArrayList<>();

    public String getName() { return name; }
    public Integer getFrequency() { return frequency; }
    public boolean isHidden() { return hidden; }
    public String getCondition() { return condition; }
    public List<XmlActionReference> getActionReferences() { return actionReferences; }
    public List<XmlNextBehavior> getNextBehaviors() { return nextBehaviors; }

    public void setFrequency(Integer frequency) { this.frequency = frequency; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    /**
     * 次の振る舞いへの遷移定義を表す内部クラス。
     */
    public static class XmlNextBehavior {
        @XmlAttribute(name = "Behavior")
        private String behaviorName;

        @XmlAttribute(name = "Add")
        private boolean add;

        public String getBehaviorName() { return behaviorName; }
        public boolean isAdd() { return add; }
    }
}
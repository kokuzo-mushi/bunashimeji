package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

/**
 * <Action> タグに対応するJAXBモデル。
 */
public class XmlAction {

    @XmlAttribute(name = "Name", required = true)
    private String name;

    @XmlAttribute(name = "Type", required = true)
    private String type;

    @XmlAttribute(name = "Speed")
    private Integer speed;

    @XmlAttribute(name = "Duration")
    private Integer duration;

    @XmlAttribute(name = "Loop")
    private Integer loop;

    @XmlAttribute(name = "VelocityX")
    private Integer velocityX;

    @XmlAttribute(name = "VelocityY")
    private Integer velocityY;

    @XmlElement(name = "Animation")
    private XmlAnimation animation;

    @XmlElement(name = "Point")
    private XmlPoint point;

    @XmlElement(name = "ActionReference")
    private List<XmlActionReference> actionReferences = new ArrayList<>();

    public String getName() { return name; }
    public String getType() { return type; }
    public Integer getSpeed() { return speed; }
    public Integer getDuration() { return duration; }
    public Integer getLoop() { return loop; }
    public Integer getVelocityX() { return velocityX; }
    public Integer getVelocityY() { return velocityY; }
    public XmlAnimation getAnimation() { return animation; }
    public XmlPoint getPoint() { return point; }
    public List<XmlActionReference> getActionReferences() { return actionReferences; }
}
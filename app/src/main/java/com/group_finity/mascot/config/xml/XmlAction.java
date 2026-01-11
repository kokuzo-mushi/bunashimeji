package com.group_finity.mascot.config.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * アクション定義を表すXML要素のマッピングクラス。
 * 各種Action実装クラスが必要とするパラメータを属性として保持します。
 */
@XmlRootElement(name = "Action")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlAction {

    @XmlAttribute(name = "Name")
    private String name;

    @XmlAttribute(name = "Type")
    private String type;

    @XmlAttribute(name = "Class")
    private String className;

    // --- 汎用パラメータ ---
    // プリミティブ型ではなくラッパークラスを使用し、未設定(null)を判別可能にする

    @XmlAttribute(name = "Duration")
    private Integer duration;

    @XmlAttribute(name = "Speed")
    private Integer speed;

    @XmlAttribute(name = "X")
    private Integer x;

    @XmlAttribute(name = "Y")
    private Integer y;

    @XmlAttribute(name = "VelocityX")
    private Integer velocityX;

    @XmlAttribute(name = "VelocityY")
    private Integer velocityY;

    // BreedAction用
    @XmlAttribute(name = "BornX")
    private Integer bornX;
    @XmlAttribute(name = "BornY")
    private Integer bornY;
    @XmlAttribute(name = "BornVelocityX")
    private Integer bornVelocityX;
    @XmlAttribute(name = "BornVelocityY")
    private Integer bornVelocityY;

    // TeeterAction用
    @XmlAttribute(name = "FallProbability")
    private Double fallProbability;

    @XmlAttribute(name = "Loop")
    private Integer loop;

    @XmlElement(name = "Animation")
    private List<XmlAnimation> animations = new ArrayList<>();

    @XmlElement(name = "Point")
    private XmlPoint point;

    @XmlElement(name = "ActionReference")
    private List<XmlActionReference> actionReferences = new ArrayList<>();

    // --- Getters (nullの場合はデフォルト値として0などを返す) ---

    public String getName() { return name; }
    public String getType() { return type; }
    public String getClassName() { return className; }

    public Integer getDuration() { return duration; }
    public Integer getSpeed() { return speed; }
    
    public Integer getX() { return x; }
    public Integer getY() { return y; }
    
    public Integer getVelocityX() { return velocityX; }
    public Integer getVelocityY() { return velocityY; }

    public Integer getBornX() { return bornX; }
    public Integer getBornY() { return bornY; }
    public Integer getBornVelocityX() { return bornVelocityX; }
    public Integer getBornVelocityY() { return bornVelocityY; }

    public Double getFallProbability() { return fallProbability; }

    public Integer getLoop() { return loop; }

    public List<XmlAnimation> getAnimations() { return animations; }

    public XmlAnimation getAnimation() {
        return (animations != null && !animations.isEmpty()) ? animations.get(0) : null;
    }

    public XmlPoint getPoint() { return point; }

    public List<XmlActionReference> getActionReferences() { return actionReferences; }

    /**
     * MoveActionなどで使用するためのターゲット座標オブジェクトを生成して返します。
     * @return XmlPointインスタンス
     */
    public XmlPoint getTarget() {
        XmlPoint p = new XmlPoint();
        p.x = getX();
        p.y = getY();
        return p;
    }
}
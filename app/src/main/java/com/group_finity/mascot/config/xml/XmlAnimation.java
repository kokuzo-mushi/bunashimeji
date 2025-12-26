package com.group_finity.mascot.config.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * アニメーション定義を表すXML要素のマッピングクラス。
 * 複数のPose要素を持ちます。
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlAnimation {

    @XmlElement(name = "Pose")
    private List<XmlPose> poses = new ArrayList<>();

    public List<XmlPose> getPoses() {
        return poses;
    }
}
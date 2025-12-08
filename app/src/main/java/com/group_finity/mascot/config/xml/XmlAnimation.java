package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * <Animation> タグに対応するJAXBモデル。
 */
public class XmlAnimation {

    @XmlElement(name = "Pose", required = true)
    private List<XmlPose> poses;

    public List<XmlPose> getPoses() { return poses; }
}
package com.group_finity.mascot.config.xml;

import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 * JAXB-annotated class for the <Animation> tag.
 * This class holds a list of poses that make up an animation.
 */
public class XmlAnimation {

    @XmlElement(name = "Pose")
    private List<XmlPose> poses;

    public List<XmlPose> getPoses() {
        return poses;
    }
}
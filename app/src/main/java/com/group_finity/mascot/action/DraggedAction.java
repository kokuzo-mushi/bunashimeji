package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.stream.Collectors;

/**
 * Action representing the state of being dragged by the user.
 * This action continues as long as the mascot is being dragged.
 * Actual position updates are typically handled by the mouse event listeners in the View layer.
 */
public class DraggedAction implements Action {

    private final Animation animation;
    private boolean finished = false;

    public DraggedAction(XmlAnimation xmlAnimation) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void execute(Mascot mascot) {
        mascot.setAnimation(animation);
        final int FRAME_DURATION_MS = 40;
        animation.tick(FRAME_DURATION_MS);

        // If the mascot is no longer being dragged, finish this action.
        if (!mascot.isBeingDragged()) {
            finished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public void reset() {
        this.finished = false;
        this.animation.reset();
    }
}
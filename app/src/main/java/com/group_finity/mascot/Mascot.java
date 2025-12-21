package com.group_finity.mascot;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.animation.Animation;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.awt.Point;
import java.util.Collections;
import java.util.Map;

/**
 * Represents the mascot character.
 * Holds state such as position, direction, and current animation.
 */
public class Mascot {
    private int x;
    private int y;
    private int velocityX;
    private int velocityY;
    private boolean lookRight;
    private Animation animation;
    private Action nextAction;
    private Action currentAction;

    // State flags
    private boolean grounded;
    private boolean hittingLeftWall;
    private boolean hittingRightWall;
    private boolean hittingCeiling;
    private boolean beingDragged;
    
    // ウィンドウ操作用
    private HWND floorWindow;
    private HWND holdingWindow;

    private Map<String, Action> actions = Collections.emptyMap();

    /**
     * Checks if the mascot is currently on the ground (e.g. taskbar or window edge).
     * Alias for isGrounded() to support different naming conventions.
     * @return true if on ground, false otherwise.
     */
    public boolean isOnGround() {
        return isGrounded();
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public boolean isHittingLeftWall() {
        return hittingLeftWall;
    }

    public void setHittingLeftWall(boolean hittingLeftWall) {
        this.hittingLeftWall = hittingLeftWall;
    }

    public boolean isHittingRightWall() {
        return hittingRightWall;
    }

    public void setHittingRightWall(boolean hittingRightWall) {
        this.hittingRightWall = hittingRightWall;
    }

    public boolean isHittingCeiling() {
        return hittingCeiling;
    }

    public void setHittingCeiling(boolean hittingCeiling) {
        this.hittingCeiling = hittingCeiling;
    }

    public boolean isBeingDragged() {
        return beingDragged;
    }

    public void setBeingDragged(boolean beingDragged) {
        this.beingDragged = beingDragged;
    }

    public void startDrag() {
        setBeingDragged(true);
        setHoldingWindow(null);
    }

    public void endDrag() {
        setBeingDragged(false);
    }

    public HWND getFloorWindow() {
        return floorWindow;
    }

    public void setFloorWindow(HWND floorWindow) {
        this.floorWindow = floorWindow;
    }

    public HWND getHoldingWindow() {
        return holdingWindow;
    }

    public void setHoldingWindow(HWND holdingWindow) {
        this.holdingWindow = holdingWindow;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(int velocityX) {
        this.velocityX = velocityX;
    }

    public int getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(int velocityY) {
        this.velocityY = velocityY;
    }

    public boolean isLookRight() {
        return lookRight;
    }

    public void setLookRight(boolean lookRight) {
        this.lookRight = lookRight;
    }

    public Point getAnchor() {
        return new Point(x, y);
    }

    public void setAnchor(Point p) {
        this.x = p.x;
        this.y = p.y;
    }

    public void setAnimation(Animation animation) { this.animation = animation; }
    
    public Animation getAnimation() {
        return animation;
    }

    public void setNextAction(Action action) { this.nextAction = action; }

    // Added to resolve "setAction undefined" error
    public void setAction(Action action) { this.nextAction = action; }

    public Action getCurrentAction() {
        return currentAction;
    }

    public String getState() {
        return currentAction != null ? currentAction.toString() : null;
    }

    public void setActions(Map<String, Action> actions) {
        this.actions = actions;
    }

    public void tick() {
        if (nextAction != null) {
            currentAction = nextAction;
            nextAction = null;
        }
        
        if (currentAction != null) {
            currentAction.execute(this);
            if (!currentAction.hasNext()) {
                currentAction = null;
            }
        }
    }
}
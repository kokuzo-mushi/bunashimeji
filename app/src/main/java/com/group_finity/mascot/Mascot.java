package com.group_finity.mascot;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.animation.Animation;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.awt.Point;
import java.awt.Rectangle;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Context;

/**
 * Represents the mascot character.
 * Holds state such as position, direction, and current animation.
 */
public class Mascot {
    private int x;
    private int y;
    private int velocityX;
    private int velocityY;
    private int previousVelocityY;
    private boolean lookRight;
    private Animation animation;
    private Action nextAction;
    private Action currentAction;
    private Action previousAction;

    // State flags
    private boolean grounded;
    private boolean hittingLeftWall;
    private boolean hittingRightWall;
    private boolean hittingCeiling;
    private boolean beingDragged;
    private boolean ignoreWalls;
    
    // ウィンドウ操作用
    private HWND floorWindow;
    private HWND ceilingWindow;
    private HWND holdingWindow;
    private HWND targetWindow;
    private HWND leftWallWindow;
    private HWND rightWallWindow;

    // 環境情報（論理座標）
    private Rectangle workArea;
    private Rectangle leftWallRect;
    private Rectangle rightWallRect;
    private Rectangle ceilingRect;

    // GraalJS Context (Per-instance isolation)
    private Context jsContext;

    /**
     * Checks if the mascot is currently on the ground (e.g. taskbar or window edge).
     * Alias for isGrounded() to support different naming conventions.
     * @return true if on ground, false otherwise.
     */
    @HostAccess.Export
    public boolean isOnGround() {
        return isGrounded();
    }

    @HostAccess.Export
    public boolean isGrounded() {
        return grounded;
    }

    @HostAccess.Export
    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    @HostAccess.Export
    public boolean isHittingLeftWall() {
        return hittingLeftWall;
    }

    @HostAccess.Export
    public void setHittingLeftWall(boolean hittingLeftWall) {
        this.hittingLeftWall = hittingLeftWall;
    }

    @HostAccess.Export
    public boolean isHittingRightWall() {
        return hittingRightWall;
    }

    @HostAccess.Export
    public void setHittingRightWall(boolean hittingRightWall) {
        this.hittingRightWall = hittingRightWall;
    }

    @HostAccess.Export
    public boolean isHittingCeiling() {
        return hittingCeiling;
    }

    @HostAccess.Export
    public void setHittingCeiling(boolean hittingCeiling) {
        this.hittingCeiling = hittingCeiling;
    }

    @HostAccess.Export
    public boolean isBeingDragged() {
        return beingDragged;
    }

    @HostAccess.Export
    public void setBeingDragged(boolean beingDragged) {
        this.beingDragged = beingDragged;
    }

    @HostAccess.Export
    public void startDrag() {
        setBeingDragged(true);
        setHoldingWindow(null);
    }

    @HostAccess.Export
    public void endDrag() {
        setBeingDragged(false);
    }

    @HostAccess.Export
    public boolean isIgnoringWalls() {
        return ignoreWalls;
    }

    @HostAccess.Export
    public void setIgnoreWalls(boolean ignoreWalls) {
        this.ignoreWalls = ignoreWalls;
    }

    @HostAccess.Export
    public HWND getFloorWindow() {
        return floorWindow;
    }

    @HostAccess.Export
    public void setFloorWindow(HWND floorWindow) {
        this.floorWindow = floorWindow;
    }

    @HostAccess.Export
    public HWND getCeilingWindow() {
        return ceilingWindow;
    }

    @HostAccess.Export
    public void setCeilingWindow(HWND ceilingWindow) {
        this.ceilingWindow = ceilingWindow;
    }

    @HostAccess.Export
    public HWND getHoldingWindow() {
        return holdingWindow;
    }

    @HostAccess.Export
    public void setHoldingWindow(HWND holdingWindow) {
        this.holdingWindow = holdingWindow;
    }

    @HostAccess.Export
    public HWND getTargetWindow() {
        return targetWindow;
    }

    @HostAccess.Export
    public void setTargetWindow(HWND targetWindow) {
        this.targetWindow = targetWindow;
    }

    @HostAccess.Export
    public HWND getLeftWallWindow() {
        return leftWallWindow;
    }

    @HostAccess.Export
    public void setLeftWallWindow(HWND leftWallWindow) {
        this.leftWallWindow = leftWallWindow;
    }

    @HostAccess.Export
    public HWND getRightWallWindow() {
        return rightWallWindow;
    }

    @HostAccess.Export
    public void setRightWallWindow(HWND rightWallWindow) {
        this.rightWallWindow = rightWallWindow;
    }

    @HostAccess.Export
    public Rectangle getWorkArea() {
        return workArea;
    }

    public void setWorkArea(Rectangle workArea) {
        this.workArea = workArea;
    }

    @HostAccess.Export
    public Rectangle getLeftWallRect() {
        return leftWallRect;
    }

    public void setLeftWallRect(Rectangle leftWallRect) {
        this.leftWallRect = leftWallRect;
    }

    @HostAccess.Export
    public Rectangle getRightWallRect() {
        return rightWallRect;
    }

    public void setRightWallRect(Rectangle rightWallRect) {
        this.rightWallRect = rightWallRect;
    }

    @HostAccess.Export
    public Rectangle getCeilingRect() {
        return ceilingRect;
    }

    public void setCeilingRect(Rectangle ceilingRect) {
        this.ceilingRect = ceilingRect;
    }

    @HostAccess.Export
    public int getX() {
        return x;
    }

    @HostAccess.Export
    public void setX(int x) {
        this.x = x;
    }

    @HostAccess.Export
    public int getY() {
        return y;
    }

    @HostAccess.Export
    public void setY(int y) {
        this.y = y;
    }

    @HostAccess.Export
    public int getVelocityX() {
        return velocityX;
    }

    @HostAccess.Export
    public void setVelocityX(int velocityX) {
        this.velocityX = velocityX;
    }

    @HostAccess.Export
    public int getVelocityY() {
        return velocityY;
    }

    @HostAccess.Export
    public void setVelocityY(int velocityY) {
        this.velocityY = velocityY;
    }

    @HostAccess.Export
    public int getPreviousVelocityY() {
        return previousVelocityY;
    }

    @HostAccess.Export
    public boolean isLookRight() {
        return lookRight;
    }

    @HostAccess.Export
    public void setLookRight(boolean lookRight) {
        this.lookRight = lookRight;
    }

    @HostAccess.Export
    public Point getAnchor() {
        return new Point(x, y);
    }

    @HostAccess.Export
    public void setAnchor(Point p) {
        this.x = p.x;
        this.y = p.y;
    }

    public void setAnimation(Animation animation) { this.animation = animation; }
    
    public Animation getAnimation() {
        return animation;
    }

    @HostAccess.Export
    public void setNextAction(Action action) { this.nextAction = action; }

    // Added to resolve "setAction undefined" error
    @HostAccess.Export
    public void setAction(Action action) { this.nextAction = action; }

    @HostAccess.Export
    public Action getCurrentAction() {
        return currentAction;
    }

    @HostAccess.Export
    public Action getPreviousAction() {
        return previousAction;
    }

    @HostAccess.Export
    public boolean isPreviousAction(String name) {
        if (previousAction == null) return false;
        // クラス名またはtoString()の結果で判定する（安全策）
        return previousAction.getClass().getSimpleName().equals(name) || previousAction.toString().equals(name);
    }

    @HostAccess.Export
    public String getState() {
        return currentAction != null ? currentAction.toString() : null;
    }

    public void tick() {
        // 物理演算で速度が書き換わる前に、現在の速度を履歴として保存
        this.previousVelocityY = this.velocityY;

        if (nextAction != null) {
            if (currentAction != null) {
                previousAction = currentAction;
            }
            currentAction = nextAction;
            nextAction = null;
        }
        
        if (currentAction != null) {
            currentAction.execute(this);
            if (!currentAction.hasNext()) {
                previousAction = currentAction;
                currentAction = null;
            }
        }
    }

    public Context getJsContext() {
        return jsContext;
    }

    public void setJsContext(Context jsContext) {
        this.jsContext = jsContext;
    }
}
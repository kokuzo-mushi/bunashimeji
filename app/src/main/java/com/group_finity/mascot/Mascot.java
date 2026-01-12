package com.group_finity.mascot;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.type.NeoPoint;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.util.Map;

import com.group_finity.mascot.script.ScriptEngine;
import com.group_finity.mascot.script.ScriptEngineManager;

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

    // Window handles (Panama)
    private MemorySegment floorWindow;
    private MemorySegment ceilingWindow;
    private MemorySegment holdingWindow;
    private MemorySegment targetWindow;
    private MemorySegment leftWallWindow;
    private MemorySegment rightWallWindow;

    // Environment info (Logical coordinates)
    private NeoRect workArea;
    private NeoRect leftWallRect;
    private NeoRect rightWallRect;
    private NeoRect ceilingRect;

    // GraalJS Context (Per-instance isolation)
    private Context jsContext;

     /**
     * このマスコット専用のJavaScript実行エンジン。
     */
    private ScriptEngine scriptEngine;

    /**
     * コンストラクタ
     */
    public Mascot() {
        // ... 既存の初期化処理 ...

        // ▼▼▼ この初期化処理を追加 ▼▼▼
        // JavaScriptコンテキストを初期化し、グローバル変数として 'mascot' 自身を登録する。
        // これにより、スクリプトや条件式から 'mascot.isGrounded()' のようにアクセスできるようになる。
        this.scriptEngine = ScriptEngineManager.INSTANCE.createMascotContext(Map.of("mascot", this));
    }

    /**
     * このマスコットのJavaScript実行コンテキストを返す。
     * Behaviorの条件式評価などで使用される。
     * @return GraalJSのContextオブジェクト
     */
    public Context getJsContext() {
        return (this.scriptEngine != null) ? this.scriptEngine.getContext() : null;
    }

    /**
     * Checks if the mascot is currently on the ground (e.g. taskbar or window
     * edge).
     * Alias for isGrounded() to support different naming conventions.
     * 
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
    public MemorySegment getFloorWindow() {
        return floorWindow;
    }

    @HostAccess.Export
    public void setFloorWindow(MemorySegment floorWindow) {
        this.floorWindow = floorWindow;
    }

    @HostAccess.Export
    public MemorySegment getCeilingWindow() {
        return ceilingWindow;
    }

    @HostAccess.Export
    public void setCeilingWindow(MemorySegment ceilingWindow) {
        this.ceilingWindow = ceilingWindow;
    }

    @HostAccess.Export
    public MemorySegment getHoldingWindow() {
        return holdingWindow;
    }

    @HostAccess.Export
    public void setHoldingWindow(MemorySegment holdingWindow) {
        this.holdingWindow = holdingWindow;
    }

    @HostAccess.Export
    public MemorySegment getTargetWindow() {
        return targetWindow;
    }

    @HostAccess.Export
    public void setTargetWindow(MemorySegment targetWindow) {
        this.targetWindow = targetWindow;
    }

    @HostAccess.Export
    public MemorySegment getLeftWallWindow() {
        return leftWallWindow;
    }

    @HostAccess.Export
    public void setLeftWallWindow(MemorySegment leftWallWindow) {
        this.leftWallWindow = leftWallWindow;
    }

    @HostAccess.Export
    public MemorySegment getRightWallWindow() {
        return rightWallWindow;
    }

    @HostAccess.Export
    public void setRightWallWindow(MemorySegment rightWallWindow) {
        this.rightWallWindow = rightWallWindow;
    }

    @HostAccess.Export
    public NeoRect getWorkArea() {
        return workArea;
    }

    public void setWorkArea(NeoRect workArea) {
        this.workArea = workArea;
    }

    @HostAccess.Export
    public NeoRect getLeftWallRect() {
        return leftWallRect;
    }

    public void setLeftWallRect(NeoRect leftWallRect) {
        this.leftWallRect = leftWallRect;
    }

    @HostAccess.Export
    public NeoRect getRightWallRect() {
        return rightWallRect;
    }

    public void setRightWallRect(NeoRect rightWallRect) {
        this.rightWallRect = rightWallRect;
    }

    @HostAccess.Export
    public NeoRect getCeilingRect() {
        return ceilingRect;
    }

    public void setCeilingRect(NeoRect ceilingRect) {
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
    public NeoPoint getAnchor() {
        return new NeoPoint(x, y);
    }

    @HostAccess.Export
    public void setAnchor(NeoPoint p) {
        this.x = p.x();
        this.y = p.y();
    }

    public void setAnimation(Animation animation) {
        this.animation = animation;
    }

    public Animation getAnimation() {
        return animation;
    }

    @HostAccess.Export
    public void setNextAction(Action action) {
        this.nextAction = action;
    }

    @HostAccess.Export
    public void setAction(Action action) {
        this.nextAction = action;
    }

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
        if (previousAction == null)
            return false;
        return previousAction.getClass().getSimpleName().equals(name) || previousAction.toString().equals(name);
    }

    @HostAccess.Export
    public String getState() {
        return currentAction != null ? currentAction.toString() : null;
    }

    public void tick() {
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

    public void setJsContext(Context jsContext) {
        this.jsContext = jsContext;
    }
}
package com.group_finity.mascot;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.animation.Animation;

import java.awt.Point;
import java.util.Map;
import java.util.Collections;

/**
 * The core class representing a single mascot character.
 * <p>
 * It holds the mascot's state (e.g., position, orientation) and manages the
 * execution of its current {@link Action}.
 */
public class Mascot {

    // All available actions, loaded from configuration.
    private Map<String, Action> actions = Collections.emptyMap();

    // The action currently being executed.
    private Action currentAction;

    // The next action to be executed, typically set by the EventDispatcher.
    private Action nextAction;

    // Mascot's state variables. These will be manipulated by Actions.
    private int x = 0;
    private int y = 0;
    private boolean lookRight = true;
    private boolean isGrounded = false;
    private boolean isHittingLeftWall = false;
    private boolean isHittingRightWall = false;
    private boolean isBeingDragged = false;

    // The animation currently being displayed.
    private Animation currentAnimation;

    /**
     * The main "heartbeat" method for the mascot. This should be called periodically
     * by the main application loop.
     * <p>
     * It manages the lifecycle of actions:
     * 1. If the current action is finished, it transitions to the next scheduled action.
     * 2. It then executes a single step of the current action.
     */
    public void tick() {
        // ドラッグされている間は、自律的なアクションを実行しない
        if (isBeingDragged) {
            // NOTE: ここでドラッグ中のアニメーションを処理することも可能
            return;
        }

        // If the current action is null or has finished, switch to the next one.
        if (this.currentAction == null || !this.currentAction.hasNext()) {
            this.currentAction = this.nextAction;
            this.nextAction = null;
        }

        // If there's an action to perform, execute it.
        if (this.currentAction != null) {
            this.currentAction.execute(this);
        }
    }

    /**
     * ドラッグが開始されたときに呼び出されます。
     */
    public void startDrag() {
        this.isBeingDragged = true;
        this.currentAction = null; // 現在のアクションを中断
        this.nextAction = null;
    }

    public void endDrag() {
        this.isBeingDragged = false;
    }

    /**
     * Schedules the next action to be executed once the current one is complete.
     * This method is designed to be called by the {@code EventDispatcher} when a trigger fires.
     *
     * @param nextAction The action to schedule.
     */
    public void setNextAction(Action nextAction) {
        this.nextAction = nextAction;
    }

    public Action getCurrentAction() {
        return currentAction;
    }

    /**
     * Sets the map of all available actions.
     * This is called during initialization to provide the mascot with its action repertoire.
     * @param actions A map of action names to Action instances.
     */
    public void setActions(Map<String, Action> actions) {
        this.actions = actions;
    }

    /**
     * Finds an action by its name and schedules it for execution.
     * This method is designed to be called by the {@code EventDispatcher}.
     * @param actionName The name of the action to perform.
     */
    public void performAction(String actionName) {
        Action action = actions.get(actionName);
        if (action != null) {
            setNextAction(action);
        } else {
            System.err.println("Action not found: " + actionName);
        }
    }

    //--- Getters and Setters for State ---

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

    public Point getAnchor() {
        return new Point(x, y);
    }

    public void setAnchor(Point anchor) {
        this.x = anchor.x;
        this.y = anchor.y;
    }

    public Animation getAnimation() {
        return currentAnimation;
    }

    public void setAnimation(Animation animation) {
        // アクションが新しいアニメーションを設定した場合、それを現在のものとして保持します。
        this.currentAnimation = animation;
    }

    public boolean isLookRight() {
        return lookRight;
    }

    public void setLookRight(boolean lookRight) {
        this.lookRight = lookRight;
    }

    public boolean isGrounded() {
        return isGrounded;
    }

    public void setGrounded(boolean isGrounded) {
        this.isGrounded = isGrounded;
    }

    public boolean isHittingLeftWall() {
        return isHittingLeftWall;
    }

    public void setHittingLeftWall(boolean isHittingLeftWall) {
        this.isHittingLeftWall = isHittingLeftWall;
    }

    public boolean isHittingRightWall() {
        return isHittingRightWall;
    }

    public void setHittingRightWall(boolean isHittingRightWall) {
        this.isHittingRightWall = isHittingRightWall;
    }

    public boolean isBeingDragged() {
        return isBeingDragged;
    }
}
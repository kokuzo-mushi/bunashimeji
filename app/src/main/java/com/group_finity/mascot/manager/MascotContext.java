package com.group_finity.mascot.manager;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.view.MascotView;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;

/**
 * マスコット1体分のコンテキスト情報を保持するクラス。
 * (旧 Main.MascotInstance のリファクタリング版)
 */
public class MascotContext {
    private final Mascot mascot;
    private final MascotView view;
    private final EventDispatcher dispatcher;
    private final EvaluationContext context;
    private final long bornTime;

    private MemorySegment currentFloorWindow;
    private NeoRect currentFloorRect;
    private MemorySegment currentCeilingWindow;
    private NeoRect currentCeilingRect;
    private MemorySegment currentLeftWallWindow;
    private NeoRect currentLeftWallRect;
    private MemorySegment currentRightWallWindow;
    private NeoRect currentRightWallRect;
    private boolean wasDragged = false;

    public MascotContext(Mascot mascot, MascotView view, EventDispatcher dispatcher, EvaluationContext context,
            long bornTime) {
        this.mascot = mascot;
        this.view = view;
        this.dispatcher = dispatcher;
        this.context = context;
        this.bornTime = bornTime;
    }

    public Mascot getMascot() {
        return mascot;
    }

    public MascotView getView() {
        return view;
    }

    public EventDispatcher getDispatcher() {
        return dispatcher;
    }

    public EvaluationContext getContext() {
        return context;
    }

    public long getBornTime() {
        return bornTime;
    }

    public MemorySegment getCurrentFloorWindow() {
        return currentFloorWindow;
    }

    public void setCurrentFloorWindow(MemorySegment currentFloorWindow) {
        this.currentFloorWindow = currentFloorWindow;
    }

    public NeoRect getCurrentFloorRect() {
        return currentFloorRect;
    }

    public void setCurrentFloorRect(NeoRect currentFloorRect) {
        this.currentFloorRect = currentFloorRect;
    }

    public MemorySegment getCurrentCeilingWindow() {
        return currentCeilingWindow;
    }

    public void setCurrentCeilingWindow(MemorySegment currentCeilingWindow) {
        this.currentCeilingWindow = currentCeilingWindow;
    }

    public NeoRect getCurrentCeilingRect() {
        return currentCeilingRect;
    }

    public void setCurrentCeilingRect(NeoRect currentCeilingRect) {
        this.currentCeilingRect = currentCeilingRect;
    }

    public MemorySegment getCurrentLeftWallWindow() {
        return currentLeftWallWindow;
    }

    public void setCurrentLeftWallWindow(MemorySegment currentLeftWallWindow) {
        this.currentLeftWallWindow = currentLeftWallWindow;
    }

    public NeoRect getCurrentLeftWallRect() {
        return currentLeftWallRect;
    }

    public void setCurrentLeftWallRect(NeoRect currentLeftWallRect) {
        this.currentLeftWallRect = currentLeftWallRect;
    }

    public MemorySegment getCurrentRightWallWindow() {
        return currentRightWallWindow;
    }

    public void setCurrentRightWallWindow(MemorySegment currentRightWallWindow) {
        this.currentRightWallWindow = currentRightWallWindow;
    }

    public NeoRect getCurrentRightWallRect() {
        return currentRightWallRect;
    }

    public void setCurrentRightWallRect(NeoRect currentRightWallRect) {
        this.currentRightWallRect = currentRightWallRect;
    }

    public boolean isWasDragged() {
        return wasDragged;
    }

    public void setWasDragged(boolean wasDragged) {
        this.wasDragged = wasDragged;
    }
}

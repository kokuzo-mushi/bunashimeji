package com.group_finity.mascot.manager;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.event.StateChangeEvent;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.type.NeoPoint;
import com.group_finity.mascot.type.NeoRect;
import com.group_finity.mascot.view.MascotView;

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * マスコットのライフサイクルと物理演算ループを管理するクラス。
 * (旧 Main.updateMascot および関連ロジックの抽出)
 */
public class MascotManager {

    /**
     * マスコット1体の1フレーム分の更新処理を行います。
     *
     * @param instance    更新対象のマスコットコンテキスト
     * @param allMascots  全マスコットのリスト (近傍マスコット判定用)
     * @param workArea    デスクトップの作業領域
     * @param gravity     現在の重力値
     * @param tickCount   現在のティックカウント
     * @param mouseMap    マウス座標情報
     * @param limitWindow アクティブウィンドウ制限 (制限対象のウィンドウハンドル, null可)
     */
    public void tick(MascotContext instance, List<MascotContext> allMascots, NeoRect workArea, int gravity,
            long tickCount, Map<String, Integer> mouseMap, MemorySegment limitWindow) {

        Mascot mascot = instance.getMascot();
        EventDispatcher dispatcher = instance.getDispatcher();
        EvaluationContext context = instance.getContext();
        MascotView mascotView = instance.getView();

        // 1. イベントをディスパッチ
        // Main.getInstance() への依存を排除するため、EventEnvelopeのソースは一旦 null または instance にする
        // 既存実装では Main インスタンスを渡していたが、Trigger 側で厳密に Main 型を要求しない限りは問題ないはず。
        // 安全のため、ここでは null を渡すか、あるいは MascotManager をソースにする検討が必要。
        // 一旦 null で進め、問題があれば修正する。
        dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, tickCount, null));

        // 2. マスコットのtick()
        mascot.tick();

        // アクションが終了してnullになった場合即座に次を決定
        if (mascot.getCurrentAction() == null) {
            if (mascot.getPreviousAction() != null) {
                System.out.printf("[Debug] Transition: Prev=%s -> Next (Evaluating)%n",
                        mascot.getPreviousAction());
            }

            dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, tickCount, null));
            mascot.tick();
        }

        // tick処理中に削除された可能性も考慮 (呼び出し元でチェックしているが念のため)
        if (!allMascots.contains(instance))
            return;

        NeoPoint floorMove = new NeoPoint(0, 0);
        NeoPoint ceilingMove = new NeoPoint(0, 0);
        NeoPoint leftWallMove = new NeoPoint(0, 0);
        NeoPoint rightWallMove = new NeoPoint(0, 0);

        // 追従処理済みのウィンドウを記録
        MemorySegment[] movedWindow = { null };

        // --- 2.5 ウィンドウ追従処理 ---
        if (mascot.isGrounded()) {
            floorMove = applyWindowMove(mascot, instance.getCurrentFloorWindow(), instance.getCurrentFloorRect(),
                    movedWindow);
        }

        if (mascot.isHittingCeiling()) {
            ceilingMove = applyWindowMove(mascot, instance.getCurrentCeilingWindow(), instance.getCurrentCeilingRect(),
                    movedWindow);
        }

        if (mascot.isHittingLeftWall()) {
            leftWallMove = applyWindowMove(mascot, instance.getCurrentLeftWallWindow(),
                    instance.getCurrentLeftWallRect(),
                    movedWindow);
        }
        if (mascot.isHittingRightWall()) {
            rightWallMove = applyWindowMove(mascot, instance.getCurrentRightWallWindow(),
                    instance.getCurrentRightWallRect(), movedWindow);
        }

        // --- 3. 物理演算と座標補正 ---
        int mascotWidth = mascotView.getMascotWidth();
        int mascotHeight = mascotView.getMascotHeight();
        java.awt.Point anchor = mascotView.getAnchor(); // AWT View returns AWT Point

        boolean targetWindowMinimized = false;

        // --- ワークエリア更新 (DPI/マルチモニタ) ---
        // AWT Window の位置更新
        if (mascotView instanceof java.awt.Window) {
            java.awt.Window window = (java.awt.Window) mascotView;
            try {
                // Limit Window Logic
                NeoRect logicalWorkArea = null;
                if (limitWindow != null) {
                    if (NativeWindowUtil.isWindow(limitWindow)) {
                        if (NativeWindowUtil.isIconic(limitWindow)) {
                            targetWindowMinimized = true;
                        } else {
                            NeoRect rect = NativeWindowUtil.getWindowRect(limitWindow);
                            logicalWorkArea = new NeoRect(rect.left(), rect.top(), rect.width(), rect.height());
                        }
                    } else {
                        // 参照渡しではないので呼び出し元のlimitWindowは変えられないが、
                        // ここでの計算には影響する。
                        limitWindow = null;
                    }
                }

                if (logicalWorkArea != null) {
                    // workAreaの上書き。ただしここはローカル変数に対する操作に留める必要あり。
                    // 引数の workArea は変更不可(プリミティブ/レコード的)だが、
                    // 後続のロジックで使う変数は差し替える。
                    workArea = logicalWorkArea;
                }

                // Update window position using AWT
                int logicalX = mascot.getX() - anchor.x;
                int logicalY = mascot.getY() - anchor.y;
                window.setBounds(logicalX, logicalY, mascotWidth, mascotHeight);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 環境情報の取得
        MemorySegment floorWindowForEnv = (floorMove.x() != 0 || floorMove.y() != 0)
                ? instance.getCurrentFloorWindow()
                : null;
        MemorySegment ceilingWindowForEnv = (ceilingMove.x() != 0 || ceilingMove.y() != 0)
                ? instance.getCurrentCeilingWindow()
                : null;
        MemorySegment leftWallWindowForEnv = (leftWallMove.x() != 0 || leftWallMove.y() != 0)
                ? instance.getCurrentLeftWallWindow()
                : null;
        MemorySegment rightWallWindowForEnv = (rightWallMove.x() != 0 || rightWallMove.y() != 0)
                ? instance.getCurrentRightWallWindow()
                : null;

        Environment.EnvironmentInfo envInfo = Environment.getInstance().getEnvironmentInfo(
                mascot.getX(), mascot.getY(), mascotWidth, mascotHeight, workArea,
                floorWindowForEnv, ceilingWindowForEnv, leftWallWindowForEnv, rightWallWindowForEnv,
                mascot.getHoldingWindow(), mascot.getTargetWindow());

        int effectiveFloorY = Math.min(envInfo.floorY, workArea.y() + workArea.height());

        // Update instance info
        instance.setCurrentFloorWindow(envInfo.floorWindow);
        instance.setCurrentFloorRect(envInfo.floorRect);
        mascot.setFloorWindow(envInfo.floorWindow);
        instance.setCurrentCeilingWindow(envInfo.ceilingWindow);
        instance.setCurrentCeilingRect(envInfo.ceilingRect);
        instance.setCurrentLeftWallWindow(envInfo.leftWallWindow);
        instance.setCurrentLeftWallRect(envInfo.leftWallRect);
        instance.setCurrentRightWallWindow(envInfo.rightWallWindow);
        instance.setCurrentRightWallRect(envInfo.rightWallRect);
        mascot.setLeftWallWindow(envInfo.leftWallWindow);
        mascot.setRightWallWindow(envInfo.rightWallWindow);
        mascot.setCeilingWindow(envInfo.ceilingWindow);

        mascot.setWorkArea(workArea);
        mascot.setLeftWallRect(envInfo.leftWallRect);
        mascot.setRightWallRect(envInfo.rightWallRect);
        mascot.setCeilingRect(envInfo.ceilingRect);

        // 接地判定
        boolean wasGrounded = mascot.isGrounded();
        boolean isNowGrounded = false;
        double bounceFactor = 0.6;
        int bounceThreshold = 10;

        if (mascot.getY() >= effectiveFloorY) {
            if (!mascot.isBeingDragged() && mascot.getVelocityY() > bounceThreshold) {
                mascot.setY(effectiveFloorY);
                mascot.setVelocityY((int) (-mascot.getVelocityY() * bounceFactor));
                mascot.setVelocityX((int) (mascot.getVelocityX() * 0.8));
                isNowGrounded = false;
            } else {
                isNowGrounded = true;
            }
        }

        if (!isNowGrounded && mascot.getVelocityY() >= 0 && mascot.getY() >= effectiveFloorY - 5) {
            if (mascot.getVelocityY() <= bounceThreshold) {
                isNowGrounded = true;
            }
        }

        // Window move stickiness
        if (!isNowGrounded && wasGrounded && envInfo.floorWindow != null
                && isSameWindow(envInfo.floorWindow, instance.getCurrentFloorWindow())) {
            int tolerance = (floorMove.y() > 0) ? floorMove.y() + 10 : 5;
            if (mascot.getY() >= effectiveFloorY - tolerance && mascot.getVelocityY() >= 0) {
                isNowGrounded = true;
            }
        }

        mascot.setGrounded(isNowGrounded);

        if (isNowGrounded) {
            mascot.setY(effectiveFloorY);
            mascot.setVelocityY(0);
            if (mascot.getCurrentAction() == null) {
                mascot.setVelocityX((int) (mascot.getVelocityX() * 0.8));
                if (Math.abs(mascot.getVelocityX()) < 1)
                    mascot.setVelocityX(0);
            }
        }

        // Fix: Apply gravity even during actions (like Fall/Jump), unless hitting walls/ceiling or dragged.
        // This ensures the "Gravity" setting in the UI actually affects the mascot's fall speed.
        if (!mascot.isGrounded() && !mascot.isHittingCeiling() && !mascot.isBeingDragged()
                && !mascot.isHittingLeftWall() && !mascot.isHittingRightWall()) {
            mascot.setY((int) (mascot.getY() + gravity));
        }

        if (isNowGrounded != wasGrounded) {
            dispatcher.evaluateTriggers(new EventEnvelope<>(
                    EventType.MASCOT_STATE_CHANGED,
                    new StateChangeEvent("isGrounded", wasGrounded, isNowGrounded),
                    mascot));
        }

        // 壁衝突判定
        boolean isDragged = mascot.isBeingDragged();
        int catchRange = (isDragged || instance.isWasDragged()) ? 64 : 0;

        int leftWallTolerance = 0;
        if (mascot.isHittingLeftWall() && envInfo.leftWallWindow != null
                && isSameWindow(envInfo.leftWallWindow, instance.getCurrentLeftWallWindow())) {
            leftWallTolerance = (leftWallMove.x() < 0) ? -leftWallMove.x() + 10 : 10;
        }
        boolean isHittingLeftWall = (mascot.getX() - anchor.x) <= envInfo.leftWallX + leftWallTolerance + 4
                + catchRange;

        int rightWallTolerance = 0;
        if (mascot.isHittingRightWall() && envInfo.rightWallWindow != null
                && isSameWindow(envInfo.rightWallWindow, instance.getCurrentRightWallWindow())) {
            rightWallTolerance = (rightWallMove.x() > 0) ? rightWallMove.x() + 10 : 10;
        }
        boolean isHittingRightWall = (mascot.getX() + (mascotWidth - anchor.x)) >= envInfo.rightWallX
                - rightWallTolerance - 4 - catchRange;

        int ceilingTolerance = 0;
        if (mascot.isHittingCeiling() && envInfo.ceilingWindow != null
                && isSameWindow(envInfo.ceilingWindow, instance.getCurrentCeilingWindow())) {
            ceilingTolerance = (ceilingMove.y() < 0) ? -ceilingMove.y() + 10 : 10;
        }
        boolean isHittingCeiling = (mascot.getY() - anchor.y) <= envInfo.ceilingY + ceilingTolerance
                + catchRange;

        if (isHittingCeiling && mascot.getVelocityY() > 0
                && (System.currentTimeMillis() - instance.getBornTime() <= 10000) && !isDragged) {
            isHittingCeiling = false;
        }

        if (isHittingCeiling) {
            mascot.setGrounded(false);
        }

        mascot.setHittingLeftWall(isHittingLeftWall);
        mascot.setHittingRightWall(isHittingRightWall);
        mascot.setHittingCeiling(isHittingCeiling);

        if (!mascot.isBeingDragged() && !mascot.isIgnoringWalls()) {
            if (isHittingLeftWall) {
                if (envInfo.leftWallWindow == null) {
                    if (mascot.isHittingCeiling()) {
                        mascot.setX(envInfo.leftWallX + anchor.x);
                    } else {
                        mascot.setX(envInfo.leftWallX);
                    }
                } else {
                    mascot.setX(envInfo.leftWallX + anchor.x - 8);
                }
                if (mascot.getVelocityX() < -bounceThreshold) {
                    mascot.setVelocityX((int) (-mascot.getVelocityX() * bounceFactor));
                } else {
                    mascot.setVelocityX(0);
                }
            }
            if (isHittingRightWall) {
                int distToRight = mascotWidth - anchor.x;
                if (envInfo.rightWallWindow == null) {
                    if (mascot.isHittingCeiling()) {
                        mascot.setX(envInfo.rightWallX - distToRight);
                    } else {
                        mascot.setX(envInfo.rightWallX);
                    }
                } else {
                    mascot.setX(envInfo.rightWallX - distToRight + 8);
                }
                if (mascot.getVelocityX() > bounceThreshold) {
                    mascot.setVelocityX((int) (-mascot.getVelocityX() * bounceFactor));
                } else {
                    mascot.setVelocityX(0);
                }
            }
            if (isHittingCeiling) {
                mascot.setY(envInfo.ceilingY + 10);
                if (mascot.getVelocityY() < -bounceThreshold) {
                    mascot.setVelocityY((int) (-mascot.getVelocityY() * bounceFactor));
                } else {
                    mascot.setVelocityY(0);
                }
            }
        }

        // Check interactions
        int distToWallTop = Integer.MAX_VALUE;
        int signedDistToWallTop = Integer.MAX_VALUE;

        if (mascot.isHittingLeftWall()) {
            int wallTop = (instance.getCurrentLeftWallRect() != null) ? instance.getCurrentLeftWallRect().top()
                    : workArea.y();
            signedDistToWallTop = (mascot.getY() - anchor.y) - wallTop;
            distToWallTop = Math.abs(signedDistToWallTop);
        } else if (mascot.isHittingRightWall()) {
            int wallTop = (instance.getCurrentRightWallRect() != null) ? instance.getCurrentRightWallRect().top()
                    : workArea.y();
            signedDistToWallTop = (mascot.getY() - anchor.y) - wallTop;
            distToWallTop = Math.abs(signedDistToWallTop);
        }

        Mascot nearest = getNearestMascot(mascot, allMascots);
        Map<String, Object> nearestMascotMap = new HashMap<>();
        nearestMascotMap.put("distance", 999999.0);
        nearestMascotMap.put("x", 0);
        if (nearest != null) {
            double dx = mascot.getX() - nearest.getX();
            double dy = mascot.getY() - nearest.getY();
            nearestMascotMap.put("distance", Math.sqrt(dx * dx + dy * dy));
            nearestMascotMap.put("x", nearest.getX());
        }

        int distToFloorLeft = Integer.MAX_VALUE;
        int distToFloorRight = Integer.MAX_VALUE;
        boolean isOnEdge = false;
        if (mascot.isGrounded() && instance.getCurrentFloorRect() != null) {
            distToFloorLeft = Math.abs(mascot.getX() - instance.getCurrentFloorRect().left());
            distToFloorRight = Math.abs(mascot.getX() - instance.getCurrentFloorRect().right());
            if (distToFloorLeft < 20 || distToFloorRight < 20) {
                isOnEdge = true;
            }
        }

        boolean isOnCeilingEdge = false;
        if (mascot.isHittingCeiling()) {
            int ceilingLeft = workArea.x();
            int ceilingRight = workArea.x() + workArea.width();
            if (instance.getCurrentCeilingRect() != null) {
                ceilingLeft = instance.getCurrentCeilingRect().left();
                ceilingRight = instance.getCurrentCeilingRect().right();
            }
            int distToCeilingLeft = Math.abs(mascot.getX() - ceilingLeft);
            int distToCeilingRight = Math.abs(mascot.getX() - ceilingRight);
            int edgeThreshold = anchor.x + 20;
            if (mascot.isHittingLeftWall() || mascot.isHittingRightWall() || distToCeilingLeft < edgeThreshold
                    || distToCeilingRight < edgeThreshold) {
                isOnCeilingEdge = true;
            }
        }

        context.getVariables().put("time", tickCount);
        context.getVariables().put("mouse", mouseMap);
        context.getVariables().put("distToWallTop", distToWallTop);
        context.getVariables().put("signedDistToWallTop", signedDistToWallTop);
        context.getVariables().put("mascot.distToFloorLeft", distToFloorLeft);
        context.getVariables().put("mascot.distToFloorRight", distToFloorRight);
        context.getVariables().put("isOnEdge", isOnEdge);
        context.getVariables().put("isOnCeilingEdge", isOnCeilingEdge);
        context.getVariables().put("nearestMascot", nearestMascotMap);

        // 4. Draw
        if (targetWindowMinimized) {
            if (mascotView.isVisible())
                mascotView.setVisible(false);
        } else {
            if (!mascotView.isVisible())
                mascotView.setVisible(true);
            mascotView.draw();
        }

        instance.setWasDragged(isDragged);
    }

    private NeoPoint applyWindowMove(Mascot mascot, MemorySegment window, NeoRect previousRect,
            MemorySegment[] processedWindow) {
        if (window == null || NativeWindowUtil.isIconic(window))
            return new NeoPoint(0, 0);
        if (processedWindow[0] != null && isSameWindow(window, processedWindow[0]))
            return new NeoPoint(0, 0);

        NeoRect rect = NativeWindowUtil.getWindowRect(window);
        if (rect.width() > 0 && rect.height() > 0) {
            int dx = rect.left() - previousRect.left();
            int dy = rect.top() - previousRect.top();
            if (dx != 0 || dy != 0) {
                mascot.setX(mascot.getX() + dx);
                mascot.setY(mascot.getY() + dy);
                processedWindow[0] = window;
                return new NeoPoint(dx, dy);
            }
        }
        return new NeoPoint(0, 0);
    }

    private boolean isSameWindow(MemorySegment w1, MemorySegment w2) {
        if (w1 == null || w2 == null)
            return false;
        return w1.address() == w2.address();
    }

    public Mascot getNearestMascot(Mascot self, List<MascotContext> allMascots) {
        Mascot nearest = null;
        double minDistanceSq = Double.MAX_VALUE;
        for (MascotContext instance : allMascots) {
            Mascot other = instance.getMascot();
            if (other == self)
                continue;
            double dx = self.getX() - other.getX();
            double dy = self.getY() - other.getY();
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                nearest = other;
            }
        }
        return nearest;
    }
}

package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.nativeaccess.Win32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.util.Random;
import java.util.stream.Collectors;

/**
 * 足元のウィンドウを掴んで、マスコットの移動に合わせてウィンドウを動かすアクション。
 */
public class GrabAction implements Action {

    private final Animation animation;
    private boolean initialized = false;
    private boolean isHolding = false;
    private int offsetX;
    private int offsetY;
    private int width;
    private int height;

    // 方向転換・移動用
    private final Random random = new Random();
    private int timeToChangeDirection = 0;
    private static final int MOVE_SPEED = 4;

    public GrabAction(XmlAnimation xmlAnimation) {
        this.animation = new Animation(
                xmlAnimation.getPoses().stream()
                        .map(xmlPose -> new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void execute(Mascot mascot) {
        // ドラッグ中などの理由で中断すべき場合は、ウィンドウを離して終了
        if (mascot.isBeingDragged()) {
            mascot.setHoldingWindow(null);
            this.isHolding = false;
            return;
        }

        // アニメーション再生
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(40);
        }

        // 初回実行時にターゲットを確定し、オフセットを計算
        if (!initialized) {
            HWND targetWindow = mascot.getFloorWindow();
            System.out.println("[GrabAction] Attempting to grab. FloorWindow: " + targetWindow);
            
            // 足元に有効なウィンドウがあれば掴む
            if (targetWindow != null && !Win32.INSTANCE.IsIconic(targetWindow)) {
                mascot.setHoldingWindow(targetWindow);
                
                RECT rect = new RECT();
                Win32.INSTANCE.GetWindowRect(targetWindow, rect);
                
                this.width = rect.right - rect.left;
                this.height = rect.bottom - rect.top;
                
                // マスコットの中心座標とウィンドウ左上の差分（オフセット）を記録
                this.offsetX = rect.left - mascot.getX();
                this.offsetY = rect.top - mascot.getY();
            }
            initialized = true;
        }

        // 掴んでいるウィンドウがある場合、マスコットの位置に合わせて移動させる
        HWND holding = mascot.getHoldingWindow();
        if (holding != null && User32.INSTANCE.IsWindow(holding)) {
            
            // --- 方向転換と移動ロジック ---
            if (timeToChangeDirection <= 0) {
                // 標準: 2秒〜5秒の間隔で方向転換を検討
                timeToChangeDirection = 2000 + random.nextInt(3000);
                // 50%の確率で向きを反転
                if (random.nextBoolean()) {
                    mascot.setLookRight(!mascot.isLookRight());
                }
            }
            timeToChangeDirection -= 40;

            // 壁にぶつかっていたら強制的に向きを変える
            if ((mascot.isHittingLeftWall() && !mascot.isLookRight()) || 
                (mascot.isHittingRightWall() && mascot.isLookRight())) {
                mascot.setLookRight(!mascot.isLookRight());
            }

            // 向いている方向に移動
            int moveX = mascot.isLookRight() ? MOVE_SPEED : -MOVE_SPEED;
            mascot.setX(mascot.getX() + moveX);
            // ---------------------------

            int targetX = mascot.getX() + offsetX;
            int targetY = mascot.getY() + offsetY;
            
            // ウィンドウを移動 (bRepaint: true)
            User32.INSTANCE.MoveWindow(holding, targetX, targetY, width, height, true);

            // 実際に移動したウィンドウの位置を取得して、マスコットがはみ出していたら引き戻す
            // (ウィンドウが画面端で止まった場合などに、マスコットだけが進んで落下するのを防ぐ)
            RECT actualRect = new RECT();
            Win32.INSTANCE.GetWindowRect(holding, actualRect);

            // マスコットのX座標をウィンドウの範囲内（マージン考慮）にクランプする
            int margin = 10; // 端から10px内側まで
            int windowWidth = actualRect.right - actualRect.left;
            int minX = actualRect.left + Math.min(margin, windowWidth / 2);
            int maxX = actualRect.right - Math.min(margin, windowWidth / 2);
            
            int clampedX = Math.max(minX, Math.min(mascot.getX(), maxX));
            
            // Y座標も同期ズレを防ぐために補正（ウィンドウ上端との相対位置を維持）
            int correctedY = actualRect.top - offsetY;

            if (mascot.getX() != clampedX || mascot.getY() != correctedY) {
                // X座標が補正された＝ウィンドウの端に到達したとみなして向きを反転
                if (mascot.getX() != clampedX) {
                    mascot.setLookRight(!mascot.isLookRight());
                }
                mascot.setX(clampedX);
                mascot.setY(correctedY);
            }
        } else {
            // ウィンドウが無効になった（閉じられた等）場合は掴むのをやめる
            mascot.setHoldingWindow(null);
        }

        // 継続判定フラグを更新
        this.isHolding = (mascot.getHoldingWindow() != null);
    }

    @Override
    public boolean hasNext() {
        return this.isHolding;
    }

    @Override
    public void reset() {
        initialized = false;
        isHolding = false;
        if (animation != null) {
            animation.reset();
        }
    }
}

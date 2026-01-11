package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.util.Random;

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

    public GrabAction(Animation animation) {
        this.animation = animation;
    }

    @Override
    public void execute(Mascot mascot) {
        // ドラッグ中などの理由で中断すべき場合は、ウィンドウを離して終了
        if (mascot.isBeingDragged()) {
            mascot.setHoldingWindow(null);
            this.isHolding = false;
            return;
        }

        // 接地していない場合はアクションを終了する
        if (!mascot.isGrounded()) {
            mascot.setHoldingWindow(null);
            this.isHolding = false;
            return;
        }

        // 床の端にいる場合はアクションを終了する（Teeter等のため）
        if (mascot.getFloorWindow() != null) {
            NeoRect rect = NativeWindowUtil.getWindowRect(mascot.getFloorWindow());
            // Main.javaのisOnEdge判定(40px)と合わせる
            if (Math.abs(mascot.getX() - rect.left()) < 40 || Math.abs(mascot.getX() - rect.right()) < 40) {
                mascot.setHoldingWindow(null);
                this.isHolding = false;
                return;
            }
        }

        // アニメーション再生
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(40);
        }

        // 初回実行時にターゲットを確定し、オフセットを計算
        if (!initialized) {
            MemorySegment targetWindow = mascot.getFloorWindow();
            System.out.println("[GrabAction] Attempting to grab. FloorWindow: " + targetWindow);

            // 足元に有効なウィンドウがあれば掴む
            if (targetWindow != null && !NativeWindowUtil.isIconic(targetWindow)) {
                mascot.setHoldingWindow(targetWindow);

                NeoRect rect = NativeWindowUtil.getWindowRect(targetWindow);

                this.width = rect.width();
                this.height = rect.height();

                // マスコットの中心座標とウィンドウ左上の差分（オフセット）を記録
                this.offsetX = rect.left() - mascot.getX();
                this.offsetY = rect.top() - mascot.getY();
            }
            initialized = true;
        }

        // 掴んでいるウィンドウがある場合、マスコットの位置に合わせて移動させる
        MemorySegment holding = mascot.getHoldingWindow();
        if (holding != null && NativeWindowUtil.isWindow(holding)) {

            // --- 方向転換と移動ロジック ---
            if (timeToChangeDirection <= 0) {
                // 標準: 2秒〜5秒の間隔で方向転換を検討
                timeToChangeDirection = 2000 + random.nextInt(3000);
                // 50%の確率で向きを反転 (今回は無効化コメントアウトそのまま)
                if (random.nextBoolean()) {
                    // mascot.setLookRight(!mascot.isLookRight());
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
            NativeWindowUtil.moveWindow(holding, targetX, targetY, width, height, true);

            // 実際に移動したウィンドウの位置を取得して、マスコットがはみ出していたら引き戻す
            NeoRect actualRect = NativeWindowUtil.getWindowRect(holding);

            // マスコットのX座標をウィンドウの範囲内（マージン考慮）にクランプする
            int margin = 2; // 端から2px内側まで
            int windowWidth = actualRect.width();
            int minX = actualRect.left() + Math.min(margin, windowWidth / 2);
            int maxX = actualRect.right() - Math.min(margin, windowWidth / 2);

            int clampedX = Math.max(minX, Math.min(mascot.getX(), maxX));

            // Y座標も同期ズレを防ぐために補正
            int correctedY = actualRect.top() - offsetY;

            if (mascot.getX() != clampedX || mascot.getY() != correctedY) {
                // X座標が補正された＝ウィンドウの端に到達したとみなして向きを反転
                if (mascot.getX() != clampedX) {
                    mascot.setHoldingWindow(null);
                    this.isHolding = false;
                    mascot.setX(clampedX);
                    mascot.setY(correctedY);
                    return;
                }
                mascot.setX(clampedX);
                mascot.setY(correctedY);
            }
        } else {
            // ウィンドウが無効になった場合は掴むのをやめる
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

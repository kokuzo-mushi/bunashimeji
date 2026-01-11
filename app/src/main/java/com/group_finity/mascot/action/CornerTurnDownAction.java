package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.math.CornerMath;
import com.group_finity.mascot.type.NeoPoint;
import com.group_finity.mascot.type.NeoRect;

/**
 * 天井の端から壁へ降りるアクション。
 * 物理演算を一時的に無効化し、幾何学的な軌道計算(CornerMath)に基づいて移動します。
 */
public class CornerTurnDownAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;

    private NeoPoint startCorner;
    private NeoPoint wallAnchor;
    private NeoPoint ceilingAnchor;
    private boolean isLeftWall; // 左壁へ降りるかどうか
    private double xRadius; // 回転半径（開始時の壁からの距離）
    private boolean initialized = false;

    public CornerTurnDownAction(Animation animation, int duration) {
        this.animation = animation;
        this.duration = duration;
        this.timeRemaining = duration;
    }

    @Override
    public void execute(Mascot mascot) {
        if (!initialized) {
            initialize(mascot);
        }

        if (!hasNext())
            return;

        // 物理演算（重力・壁吸着）を無効化
        mascot.setIgnoreWalls(true);

        // 【重要】アクション実行中、向きを強制的に維持する
        mascot.setLookRight(!isLeftWall);

        // アニメーション進行
        final int FRAME_DURATION_MS = 16;
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 進行度 (0.0 -> 1.0)
        double t = 1.0 - (double) timeRemaining / duration;

        // 天井(1.0) -> 壁(0.0)
        double progress = 1.0 - t;

        // 軌道計算
        NeoPoint nextPos = CornerMath.calculateAnchorPosition(
                startCorner, wallAnchor, ceilingAnchor, isLeftWall, progress, xRadius);

        mascot.setX(nextPos.x());
        mascot.setY(nextPos.y());

        this.timeRemaining -= FRAME_DURATION_MS;

        // 終了時の処理
        if (this.timeRemaining <= 0) {
            // 最終位置（壁の状態）に強制補正
            NeoPoint finalPos = CornerMath.calculateAnchorPosition(
                    startCorner, wallAnchor, ceilingAnchor, isLeftWall, 0.0, xRadius);
            mascot.setX(finalPos.x());
            mascot.setY(finalPos.y());

            // 物理演算を有効化
            mascot.setIgnoreWalls(false);

            // 終了時も向きを確定させておく
            mascot.setLookRight(!isLeftWall);
        }
    }

    private void initialize(Mascot mascot) {
        wallAnchor = ActionConstants.WALL_ANCHOR;
        ceilingAnchor = ActionConstants.CEILING_ANCHOR;

        // コーナー座標の特定（天井の端）
        int cornerX;
        int cornerY;
        int leftEdge;
        int rightEdge;

        NeoRect ceilingRect = mascot.getCeilingRect();
        if (ceilingRect != null) {
            // ウィンドウ天井の場合
            cornerY = ceilingRect.y(); // 天井のY座標
            leftEdge = ceilingRect.x();
            rightEdge = ceilingRect.x() + ceilingRect.width();
        } else {
            // 画面天井の場合
            NeoRect workArea = mascot.getWorkArea();
            if (workArea != null) {
                cornerY = workArea.y();
                leftEdge = workArea.x();
                rightEdge = workArea.x() + workArea.width();
            } else {
                // フォールバック
                cornerY = 0;
                leftEdge = mascot.getX() - 100;
                rightEdge = mascot.getX() + 100;
            }
        }

        // 現在地から近い方の端を選択する
        int distToLeft = Math.abs(mascot.getX() - leftEdge);
        int distToRight = Math.abs(mascot.getX() - rightEdge);

        // 近い方の壁を選択し、そちらを向くように補正する
        isLeftWall = (distToLeft < distToRight);
        cornerX = isLeftWall ? leftEdge : rightEdge;

        // 【重要】壁の位置に合わせて、マスコットの向きを強制的に補正する
        mascot.setLookRight(!isLeftWall);

        startCorner = new NeoPoint(cornerX, cornerY);

        // 現在地と壁（コーナー）との距離を回転半径とする
        this.xRadius = Math.abs(mascot.getX() - cornerX);
        initialized = true;
    }

    @Override
    public boolean hasNext() {
        return timeRemaining > 0;
    }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        if (animation != null)
            animation.reset();
        this.initialized = false;
    }
}
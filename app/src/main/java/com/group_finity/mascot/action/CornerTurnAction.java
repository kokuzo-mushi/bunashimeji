package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.math.CornerMath;
import com.group_finity.mascot.type.NeoPoint;
import com.group_finity.mascot.type.NeoRect;

/**
 * 壁のコーナーを回って天井に移動するアクション。
 * 物理演算を一時的に無効化し、幾何学的な軌道計算(CornerMath)に基づいて移動します。
 */
public class CornerTurnAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;

    private NeoPoint startCorner;
    private NeoPoint wallAnchor;
    private NeoPoint ceilingAnchor;
    private boolean isLeftWall;
    private boolean initialized = false;

    public CornerTurnAction(Animation animation, int duration) {
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

        // 【推奨】アクション実行中、壁の方向に向きを固定する
        // 左壁(isLeftWall=true)なら左向き(LookRight=false)、右壁なら右向き
        mascot.setLookRight(!isLeftWall);

        // アニメーション進行
        final int FRAME_DURATION_MS = 16;
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 進行度 (0.0 -> 1.0)
        double progress = 1.0 - (double) timeRemaining / duration;

        // 軌道計算
        NeoPoint nextPos = CornerMath.calculateAnchorPosition(
                startCorner, wallAnchor, ceilingAnchor, isLeftWall, progress, wallAnchor.y());

        mascot.setX(nextPos.x());
        mascot.setY(nextPos.y());

        this.timeRemaining -= FRAME_DURATION_MS;

        // 終了時の処理
        if (this.timeRemaining <= 0) {
            // 最終位置に強制補正
            NeoPoint finalPos = CornerMath.calculateAnchorPosition(
                    startCorner, wallAnchor, ceilingAnchor, isLeftWall, 1.0, wallAnchor.y());
            mascot.setX(finalPos.x());
            mascot.setY(finalPos.y());

            // 物理演算を有効化（天井吸着が機能するように）
            mascot.setIgnoreWalls(false);

            // 終了時も向きを確定させておく
            mascot.setLookRight(!isLeftWall);
        }
    }

    private void initialize(Mascot mascot) {
        // 現在の壁の状態からコーナー位置を特定
        isLeftWall = mascot.isHittingLeftWall();

        // 壁の方向に向きを合わせる
        mascot.setLookRight(!isLeftWall);

        // 壁のアンカー
        wallAnchor = ActionConstants.WALL_ANCHOR;

        // 天井のアンカー
        ceilingAnchor = ActionConstants.CEILING_ANCHOR;

        // コーナー座標の特定
        int cornerX = isLeftWall ? mascot.getX() : mascot.getX();

        int cornerY = 0; // デフォルト

        // 壁の矩形情報を取得
        NeoRect wallRect = isLeftWall ? mascot.getLeftWallRect() : mascot.getRightWallRect();

        if (wallRect != null) {
            cornerY = wallRect.y(); // 壁の上端
        } else {
            // 壁矩形がない場合（画面端など）、ワークエリアの上端を使用
            NeoRect workArea = mascot.getWorkArea();
            if (workArea != null) {
                cornerY = workArea.y();
            } else {
                // フォールバック: 現在位置から推定
                cornerY = mascot.getY() - wallAnchor.y();
            }
        }

        startCorner = new NeoPoint(cornerX, cornerY);
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
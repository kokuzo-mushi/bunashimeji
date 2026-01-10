package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.math.CornerMath;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * 天井の端から壁へ降りるアクション。
 * 物理演算を一時的に無効化し、幾何学的な軌道計算(CornerMath)に基づいて移動します。
 */
public class CornerTurnDownAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    
    private Point startCorner;
    private Point wallAnchor;
    private Point ceilingAnchor;
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

        if (!hasNext()) return;

        // 物理演算（重力・壁吸着）を無効化
        mascot.setIgnoreWalls(true);
        
        // 【重要】アクション実行中、向きを強制的に維持する
        // これにより、右壁に降りる際に左を向いて壁に埋まる現象を防ぐ
        mascot.setLookRight(!isLeftWall);

        // アニメーション進行
        final int FRAME_DURATION_MS = 16;
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 進行度 (0.0 -> 1.0)
        double t = 1.0 - (double) timeRemaining / duration;
        
        // CornerMathは 0.0(壁) -> 1.0(天井) なので、逆方向に計算させる
        // 天井(1.0) -> 壁(0.0)
        double progress = 1.0 - t;

        // 軌道計算
        Point nextPos = CornerMath.calculateAnchorPosition(
            startCorner, wallAnchor, ceilingAnchor, isLeftWall, progress, xRadius
        );

        mascot.setX(nextPos.x);
        mascot.setY(nextPos.y);

        this.timeRemaining -= FRAME_DURATION_MS;

        // 終了時の処理
        if (this.timeRemaining <= 0) {
            // 最終位置（壁の状態）に強制補正
            Point finalPos = CornerMath.calculateAnchorPosition(
                startCorner, wallAnchor, ceilingAnchor, isLeftWall, 0.0, xRadius
            );
            mascot.setX(finalPos.x);
            mascot.setY(finalPos.y);
            
            // 物理演算を有効化（壁吸着が機能するように）
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

        Rectangle ceilingRect = mascot.getCeilingRect();
        if (ceilingRect != null) {
            // ウィンドウ天井の場合
            cornerY = ceilingRect.y; // 天井のY座標
            leftEdge = ceilingRect.x;
            rightEdge = ceilingRect.x + ceilingRect.width;
        } else {
            // 画面天井の場合
            Rectangle workArea = mascot.getWorkArea();
            if (workArea != null) {
                cornerY = workArea.y;
                leftEdge = workArea.x;
                rightEdge = workArea.x + workArea.width;
            } else {
                // フォールバック
                cornerY = 0;
                leftEdge = mascot.getX() - 100;
                rightEdge = mascot.getX() + 100;
            }
        }
        
        // 現在地から近い方の端を選択する（向きに依存しない安全な判定）
        int distToLeft = Math.abs(mascot.getX() - leftEdge);
        int distToRight = Math.abs(mascot.getX() - rightEdge);

        // 近い方の壁を選択し、そちらを向くように補正する
        isLeftWall = (distToLeft < distToRight);
        cornerX = isLeftWall ? leftEdge : rightEdge;
        
        // 【重要】壁の位置に合わせて、マスコットの向きを強制的に補正する
        // 左壁なら左(false), 右壁なら右(true)を向く
        mascot.setLookRight(!isLeftWall);

        startCorner = new Point(cornerX, cornerY);
        
        // 現在地と壁（コーナー）との距離を回転半径とする
        this.xRadius = Math.abs(mascot.getX() - cornerX);
        initialized = true;
    }

    @Override
    public boolean hasNext() { return timeRemaining > 0; }

    @Override
    public void reset() {
        this.timeRemaining = this.duration;
        if (animation != null) animation.reset();
        this.initialized = false;
    }
}
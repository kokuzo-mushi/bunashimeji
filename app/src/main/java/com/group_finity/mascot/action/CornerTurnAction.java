package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.math.CornerMath;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * 壁のコーナーを回って天井に移動するアクション。
 * 物理演算を一時的に無効化し、幾何学的な軌道計算(CornerMath)に基づいて移動します。
 */
public class CornerTurnAction implements Action {

    private final Animation animation;
    private final int duration;
    private int timeRemaining;
    
    private Point startCorner;
    private Point wallAnchor;
    private Point ceilingAnchor;
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

        if (!hasNext()) return;

        // 物理演算（重力・壁吸着）を無効化
        mascot.setIgnoreWalls(true);

        // アニメーション進行
        final int FRAME_DURATION_MS = 16;
        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(FRAME_DURATION_MS);
        }

        // 進行度 (0.0 -> 1.0)
        double progress = 1.0 - (double) timeRemaining / duration;
        
        // 軌道計算
        Point nextPos = CornerMath.calculateAnchorPosition(
            startCorner, wallAnchor, ceilingAnchor, isLeftWall, progress, wallAnchor.y
        );

        mascot.setX(nextPos.x);
        mascot.setY(nextPos.y);

        this.timeRemaining -= FRAME_DURATION_MS;

        // 終了時の処理
        if (this.timeRemaining <= 0) {
            // 最終位置に強制補正
            Point finalPos = CornerMath.calculateAnchorPosition(
                startCorner, wallAnchor, ceilingAnchor, isLeftWall, 1.0, wallAnchor.y
            );
            mascot.setX(finalPos.x);
            mascot.setY(finalPos.y);
            
            // 物理演算を有効化（天井吸着が機能するように）
            mascot.setIgnoreWalls(false);
        }
    }

    private void initialize(Mascot mascot) {
        // 現在の壁の状態からコーナー位置を特定
        isLeftWall = mascot.isHittingLeftWall();
        
        // 壁のアンカー（現在のマスコットのアンカー設定を使用）
        // 通常は (64, 128)
        wallAnchor = ActionConstants.WALL_ANCHOR;
        
        // 天井のアンカー（遷移後のアクションで使われるアンカー）
        // CeilingCrawlなどは (64, 45)
        ceilingAnchor = ActionConstants.CEILING_ANCHOR;

        // コーナー座標の特定
        // マスコットの現在地ではなく、壁の定義位置を使用する
        int cornerX = isLeftWall ? mascot.getX() : mascot.getX(); // 壁Xは現在地を使用（吸着中なので）
        
        // コーナーYは、壁の上端。
        // ウィンドウ壁ならウィンドウ上端、画面壁なら画面上端(workArea.y)
        int cornerY = 0; // デフォルト
        
        // 壁の矩形情報を取得（Main.javaで計算済みの論理座標）
        Rectangle wallRect = isLeftWall ? mascot.getLeftWallRect() : mascot.getRightWallRect();

        if (wallRect != null) {
            cornerY = wallRect.y; // 壁の上端
        } else {
            // 壁矩形がない場合（画面端など）、ワークエリアの上端を使用
            Rectangle workArea = mascot.getWorkArea();
            if (workArea != null) {
                cornerY = workArea.y;
            } else {
                // フォールバック: 現在位置から推定
                cornerY = mascot.getY() - wallAnchor.y;
            }
        }

        startCorner = new Point(cornerX, cornerY);
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
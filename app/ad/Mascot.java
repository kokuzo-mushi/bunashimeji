package com.group_finity.mascot;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.animation.Animation;
import java.awt.Point;
import java.util.logging.Logger;

/**
 * マスコットの実体を表すクラス。
 * 座標、向き、アニメーション状態などを保持し、アクションを実行します。
 */
public class Mascot {

    private static final Logger log = Logger.getLogger(Mascot.class.getName());

    // --- 状態フィールド ---

    /** マスコットの足元の座標 (デスクトップ座標系) */
    private Point anchor = new Point(0, 0);

    /** マスコットが右を向いているかどうか */
    private boolean lookRight = true;

    /** 現在再生中のアニメーション */
    private Animation animation;

    /** 現在実行中のアクション */
    private Action activeAction;

    // --- コンストラクタ ---

    public Mascot() {
    }

    // --- メインロジック ---

    /**
     * マスコットの状態を1フレーム分更新します。
     * メインループから定期的に呼び出されることを想定しています。
     */
    public void tick() {
        if (activeAction != null) {
            try {
                activeAction.execute(this);
            } catch (Exception e) {
                log.severe("Error executing action: " + e.getMessage());
                // エラー時はアクションを停止するなどの安全策が必要ですが、
                // ここではログ出力にとどめます。
            }
        }
    }

    // --- アクセサ (Actionから操作されるメソッド) ---

    public Point getAnchor() {
        return anchor;
    }

    public void setAnchor(Point anchor) {
        this.anchor = anchor;
    }

    public boolean isLookRight() {
        return lookRight;
    }

    public void setLookRight(boolean lookRight) {
        this.lookRight = lookRight;
    }

    public Animation getAnimation() {
        return animation;
    }

    public void setAnimation(Animation animation) {
        this.animation = animation;
    }

    public void setAction(Action action) {
        this.activeAction = action;
    }
    
    public Action getAction() {
        return this.activeAction;
    }
}
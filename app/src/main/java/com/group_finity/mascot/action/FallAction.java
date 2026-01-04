package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;

public class FallAction implements Action {
    private final Animation animation;
    private int velocityY = 0;
    private double velocityX = 0;
    private boolean finished = false;
    private boolean initialized = false;

    public FallAction(Animation animation) {
        this.animation = animation;
    }

    @Override
    public void execute(Mascot mascot) {
        // 初回実行時にマスコットの現在の速度（ドラッグによる慣性など）を引き継ぐ
        if (!initialized) {
            this.velocityX = mascot.getVelocityX();
            this.velocityY = mascot.getVelocityY();
            System.out.printf("[FallAction] Initialized with velocity: (%.2f, %d)%n", velocityX, velocityY);
            initialized = true;
        } else {
            // Main.javaでの物理演算（バウンド処理など）の結果を反映させるため同期する
            this.velocityY = mascot.getVelocityY();
            this.velocityX = mascot.getVelocityX();
        }

        // 壁に衝突したらアクションを終了する
        // これにより、currentActionがnullになり、壁アクション（WallClimbなど）への遷移が可能になる
        if (mascot.isHittingLeftWall() || mascot.isHittingRightWall() || mascot.isHittingCeiling()) {
            finished = true;
            return;
        }

        if (animation != null) {
            mascot.setAnimation(animation);
            animation.tick(16);
        }

        boolean isBouncing = false;

        // 接地判定とバウンド処理
        // Main.javaで接地と判定された場合、またはFallAction内で接地した場合
        if (mascot.isGrounded()) {
            // バウンド処理 (Main.javaの閾値より小さい速度で接地した場合など)
            if (velocityY > 0) {
                // デバッグログ: 接地時の座標と速度を出力
                System.out.printf("[FallAction] Grounded at Y=%d, VelocityY=%d, VelocityX=%.2f%n", 
                    mascot.getY(), velocityY, velocityX);
                velocityY = (int) (-velocityY * 0.55); // Y軸反発係数 (0.55)
            } else {
                velocityY = 0;
            }
            
            velocityX *= 0.6; // 接地時のX軸摩擦 (強く減速させる)

            // バウンドが収束したか判定 (Y軸の反発が弱く、X軸の移動もほぼない場合)
            if (Math.abs(velocityY) < 6 && Math.abs(velocityX) < 1.0) {
                finished = true;
                // 次のアクションのために速度を完全にリセット
                mascot.setVelocityX(0);
                mascot.setVelocityY(0);
                return;
            }
            isBouncing = true;
        }

        // X軸の移動（慣性）
        // バウンドしたフレームでも横滑りは発生させる
        mascot.setX(mascot.getX() + (int) velocityX);
        
        // X軸の減衰
        if (!isBouncing) {
            // 接地時は既に摩擦(0.6)を適用済みなので、空中のみ空気抵抗を適用
            velocityX *= 0.99;
        }
        if (Math.abs(velocityX) < 0.1) velocityX = 0;

        // Y軸の移動
        // バウンドしたフレームはY軸更新をスキップ（地面に接地した瞬間を描画するため）
        if (!isBouncing) {
            if (velocityY < 20) {
                velocityY += 2; // 重力加速度 (2px/frame)
            }
            mascot.setY(mascot.getY() + velocityY);
            mascot.setVelocityY(velocityY); // Main.javaでの判定用に速度を更新
            mascot.setVelocityX((int) velocityX); // X軸速度も更新
        } else {
            // 接地中はY座標を更新しない（Main.javaの補正に任せる）
            // 速度は更新する（バウンド反発などを反映するため）
            mascot.setVelocityY(velocityY);
            mascot.setVelocityX((int) velocityX);
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public void reset() {
        velocityY = 0;
        velocityX = 0;
        finished = false;
        initialized = false;
        if (animation != null) animation.reset();
    }

    @Override
    public String toString() {
        return "FallAction";
    }
}
package com.group_finity.mascot.math;

import java.awt.Point;

/**
 * 壁から天井への遷移など、コーナーを回る際の座標計算を行うユーティリティクラス。
 * 物理演算（重力など）とは独立して、幾何学的な軌道を計算します。
 */
public class CornerMath {

    /**
     * 壁から天井への遷移中のアンカー座標を計算します。
     * マスコットの「頭」をピボット（回転軸）として、コーナーを中心に回転させます。
     *
     * @param corner       壁のコーナー座標 (World Coordinate)
     * @param wallAnchor   壁アクション時の画像アンカー (Local, e.g., 64, 128)
     * @param ceilingAnchor 天井アクション時の画像アンカー (Local, e.g., 64, 45)
     * @param isLeftWall   左壁からの遷移かどうか (true: 左壁->天井, false: 右壁->天井)
     * @param progress     進行度 (0.0 = 壁状態, 1.0 = 天井状態)
     * @param xRadius      X軸方向の回転半径（壁からの距離）。Wall->Ceilingなら128、Ceiling->Wallなら現在地との差分を指定。
     * @return 計算されたマスコットのワールド座標 (Anchor Position)
     */
    public static Point calculateAnchorPosition(Point corner, Point wallAnchor, Point ceilingAnchor, boolean isLeftWall, double progress, double xRadius) {
        // 角度: 0 -> 90度 (PI/2)
        double theta = progress * Math.PI / 2;

        // 開始時の半径（壁でのアンカーY = 壁に沿った深さ）
        double startR = wallAnchor.y;
        
        // 終了時の半径（天井でのアンカーY = 天井からの深さ）
        double targetY = ceilingAnchor.y;
        
        // X座標の目標値（壁からどれだけ離れるか）を指定された半径とする
        double targetX = xRadius;

        // 楕円軌道補間
        // Y座標: startR(128) から targetY(45) へ cosカーブで遷移
        // Y = (128 - 45) * cos(t) + 45
        // t=0 -> 128, t=90 -> 45
        double calcY = (startR - targetY) * Math.cos(theta) + targetY;
        
        // X座標: 0 から targetX(128) へ sinカーブで遷移
        double calcX = targetX * Math.sin(theta);

        // 左右の壁による反転（右壁なら左方向へ移動）
        if (!isLeftWall) {
            calcX = -calcX;
        }

        int finalX = (int) (corner.x + calcX);
        int finalY = (int) (corner.y + calcY);

        return new Point(finalX, finalY);
    }
}
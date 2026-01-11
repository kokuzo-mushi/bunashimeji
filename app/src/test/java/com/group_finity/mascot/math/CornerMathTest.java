package com.group_finity.mascot.math;

import com.group_finity.mascot.action.ActionConstants;
import com.group_finity.mascot.type.NeoPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CornerMathTest {

    @Test
    void testCalculateAnchorPosition_LeftWall_Start() {
        // 左壁からの開始地点 (Progress 0.0)
        // 壁のコーナー: (0, 0)
        // 壁アンカー: (64, 128) -> 足元
        // 天井アンカー: (64, 45) -> 頭

        NeoPoint corner = new NeoPoint(0, 0);
        NeoPoint wallAnchor = ActionConstants.WALL_ANCHOR;
        NeoPoint ceilingAnchor = ActionConstants.CEILING_ANCHOR;

        // Progress 0.0 では、壁アンカーの位置にあるはず
        // ただし、CornerMathのロジックでは「コーナー(0,0) = 頭(Y=0)」と仮定して計算しているため、
        // アンカー位置は (0, 128) になるはず（Xは壁吸着で0とみなす）
        // ※ wallPivotY = -128, rotatedY = -(-128) * cos(0) = 128

        NeoPoint result = CornerMath.calculateAnchorPosition(corner, wallAnchor, ceilingAnchor, true, 0.0,
                wallAnchor.y());

        assertEquals(0, result.x(), "Start X should be aligned with wall");
        assertEquals(ActionConstants.WALL_ANCHOR.y(), result.y(), "Start Y should be at wall anchor Y offset");
    }

    @Test
    void testCalculateAnchorPosition_LeftWall_End() {
        // 左壁からの終了地点 (Progress 1.0) -> 天井
        // 左壁なので反時計回りに90度回転 (-PI/2)

        NeoPoint corner = new NeoPoint(0, 0);
        NeoPoint wallAnchor = ActionConstants.WALL_ANCHOR;
        NeoPoint ceilingAnchor = ActionConstants.CEILING_ANCHOR; // 頭基準

        // Progress 1.0 では、天井アンカーの位置になる
        // Pivotは -ceilingAnchor.y = -45
        // 回転角 -90度
        // vecX = 0, vecY = -(-45) = 45
        // rotatedX = 45 * cos(-90) - 45 * sin(-90) = 0 - (-45) = 45
        // rotatedY = 45 * sin(-90) + 45 * cos(-90) = -45 + 0 = -45
        // 期待値: (45, -45) ???
        // いや、天井に張り付く場合、Y=0 (コーナーY) に頭が接する形になるはず。
        // 天井アクションのアンカーは (64, 45) なので、画像の上端(0)がY=0に来るには、アンカーはY=45にあるべき。

        // 修正後ロジック: Y座標は天井アンカー(45)に収束する

        NeoPoint result = CornerMath.calculateAnchorPosition(corner, wallAnchor, ceilingAnchor, true, 1.0,
                wallAnchor.y());

        // 左壁から天井へ行くと、Xはプラス方向（画面内側）へ移動する。壁の高さ(128)分だけスライドすると仮定。
        assertEquals(ActionConstants.WALL_ANCHOR.y(), result.x(), 1.0, "End X should be shifted inward by wall height");

        // Yは天井アンカー(45)の位置になるべき
        assertEquals(ActionConstants.CEILING_ANCHOR.y(), result.y(), 1.0, "End Y should be at ceiling anchor Y offset");
    }

    @Test
    void testCalculateAnchorPosition_RightWall_End() {
        // 右壁からの終了地点 (Progress 1.0) -> 天井
        // 右壁なので時計回りに90度回転 (+PI/2)

        NeoPoint corner = new NeoPoint(1000, 0);
        NeoPoint wallAnchor = ActionConstants.WALL_ANCHOR;
        NeoPoint ceilingAnchor = ActionConstants.CEILING_ANCHOR;

        NeoPoint result = CornerMath.calculateAnchorPosition(corner, wallAnchor, ceilingAnchor, false, 1.0,
                wallAnchor.y());

        // 右壁から天井へ行くと、Xはマイナス方向（画面内側）へ移動する
        // X = 1000 - 128
        assertEquals(1000 - ActionConstants.WALL_ANCHOR.y(), result.x(), 1.0);
        assertEquals(ActionConstants.CEILING_ANCHOR.y(), result.y(), 1.0);
    }
}
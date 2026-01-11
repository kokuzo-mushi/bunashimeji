package com.group_finity.mascot.action;

import java.awt.Point;

/**
 * アクション間で共有される定数定義。
 * 特に、物理的な位置合わせが必要なアンカーポイントなどを管理します。
 */
public class ActionConstants {

    /**
     * 壁アクション（Climb, WallClingなど）の標準的な画像アンカー。
     * 足元基準 (X=Center, Y=Bottom)
     * 例: 128x128の画像で、足元中央が(64, 128)
     */
    public static final com.group_finity.mascot.type.NeoPoint WALL_ANCHOR = new com.group_finity.mascot.type.NeoPoint(
            64, 128);

    /**
     * 天井アクション（CeilingCrawl, CeilingStayなど）の標準的な画像アンカー。
     * 頭/手基準 (X=Center, Y=Top付近)
     * 例: 128x128の画像で、頭頂部付近が(64, 45)
     */
    public static final com.group_finity.mascot.type.NeoPoint CEILING_ANCHOR = new com.group_finity.mascot.type.NeoPoint(
            64, 45);

    private ActionConstants() {
    } // インスタンス化禁止
}
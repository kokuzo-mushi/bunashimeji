package com.group_finity.mascot.manager;

import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.group_finity.mascot.type.NeoRect;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * 画面外に投げられたウィンドウを管理し、元の位置に復元するクラス。
 * 従来の Main.java からロジックを分離。
 */
public class WindowRestorationManager {

    private static final WindowRestorationManager INSTANCE = new WindowRestorationManager();

    private final List<ThrownWindowInfo> thrownWindows = new ArrayList<>();

    private WindowRestorationManager() {
    }

    public static WindowRestorationManager getInstance() {
        return INSTANCE;
    }

    // 投げられたウィンドウの情報を保持するクラス
    private static class ThrownWindowInfo {
        MemorySegment hwnd;
        int originalX, originalY, width, height;
        long thrownTime;

        // 復帰アニメーション用
        boolean isRestoring = false;
        long restoreStartTime;
        int startX, startY;

        ThrownWindowInfo(MemorySegment hwnd, int x, int y, int w, int h) {
            this.hwnd = hwnd;
            this.originalX = x;
            this.originalY = y;
            this.width = w;
            this.height = h;
            this.thrownTime = System.currentTimeMillis();
        }
    }

    /**
     * 投げられたウィンドウを管理リストに追加します。
     */
    public void addThrownWindow(MemorySegment window, int x, int y, int width, int height) {
        if (window != null && !MemorySegment.NULL.equals(window)) {
            synchronized (thrownWindows) {
                thrownWindows.add(new ThrownWindowInfo(window, x, y, width, height));
            }
            System.out.println("[WindowRestorationManager] Window added to restoration list.");
        }
    }

    /**
     * 管理中のウィンドウを即座に全て復帰させます。
     */
    public void restoreAllWindows() {
        int count = 0;
        List<ThrownWindowInfo> snapshot;
        synchronized (thrownWindows) {
            snapshot = new ArrayList<>(thrownWindows);
            thrownWindows.clear(); // Clear immediately to prevent double restore
        }

        for (ThrownWindowInfo info : snapshot) {
            if (NativeWindowUtil.isWindow(info.hwnd)) {
                if (NativeWindowUtil.isIconic(info.hwnd)) {
                    NativeWindowUtil.showWindow(info.hwnd, NativeWindowUtil.SW_RESTORE);
                }
                NativeWindowUtil.moveWindow(info.hwnd, info.originalX, info.originalY, info.width, info.height, true);
                count++;
            }
        }
        if (count > 0) {
            System.out.println("[WindowRestorationManager] Restored " + count + " windows immediately.");
        }
    }

    /**
     * 定期的に呼び出される更新処理。
     * 一定時間経過したウィンドウをアニメーション付きで元の位置に戻します。
     */
    public void tick() {
        long now = System.currentTimeMillis();
        long RESTORE_DELAY = 5000;
        long ANIMATION_DURATION = 2000;
        List<ThrownWindowInfo> toRemove = new ArrayList<>();

        synchronized (thrownWindows) {
            for (ThrownWindowInfo info : thrownWindows) {
                // ウィンドウが無効になっていたら削除対象
                if (!NativeWindowUtil.isWindow(info.hwnd)) {
                    toRemove.add(info);
                    continue;
                }

                if (!info.isRestoring) {
                    // 復帰待ち状態
                    if (now - info.thrownTime >= RESTORE_DELAY) {
                        info.isRestoring = true;
                        info.restoreStartTime = now;
                        NeoRect rect = NativeWindowUtil.getWindowRect(info.hwnd);
                        // rectがnullの場合(取得失敗)はデフォルト0
                        if (rect != null) {
                            info.startX = rect.left();
                            info.startY = rect.top();
                        } else {
                            info.startX = 0;
                            info.startY = 0;
                        }

                        if (NativeWindowUtil.isIconic(info.hwnd)) {
                            NativeWindowUtil.showWindow(info.hwnd, NativeWindowUtil.SW_RESTORE);
                        }
                        System.out.println("[WindowRestorationManager] Starting restoration animation.");
                    }
                } else {
                    // アニメーション中
                    long elapsed = now - info.restoreStartTime;
                    double progress = (double) elapsed / ANIMATION_DURATION;

                    if (progress >= 1.0) {
                        // 完了: 最終位置に移動してリストから削除
                        NativeWindowUtil.moveWindow(info.hwnd, info.originalX, info.originalY, info.width, info.height,
                                true);
                        toRemove.add(info);
                        System.out.println("[WindowRestorationManager] Restoration complete.");
                    } else {
                        // 補間移動 (EaseOut Back)
                        // Original Ease Function used in Main.java:
                        // double c1 = 1.70158;
                        // double c3 = c1 + 1;
                        // double ease = 1 + c3 * Math.pow(progress - 1, 3) + c1 * Math.pow(progress -
                        // 1, 2);

                        double c1 = 1.70158;
                        double c3 = c1 + 1;
                        double ease = 1 + c3 * Math.pow(progress - 1, 3) + c1 * Math.pow(progress - 1, 2);

                        int currentX = (int) (info.startX + (info.originalX - info.startX) * ease);
                        int currentY = (int) (info.startY + (info.originalY - info.startY) * ease);
                        NativeWindowUtil.moveWindow(info.hwnd, currentX, currentY, info.width, info.height, true);
                    }
                }
            }
            thrownWindows.removeAll(toRemove);
        }
    }
}

package com.group_finity.mascot.view;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.image.ImageCache;

import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;

/**
 * マスコットを描画するためのSwingウィンドウ。
 * 背景が透過で、常に最前面に表示されます。
 */
public class MascotView extends JWindow {

    private final Mascot mascot;
    private final ImageCache imageCache;
    private final EventDispatcher dispatcher;
    private BufferedImage currentImage;
    private Point dragStartOffset; // ドラッグ開始時の、ウィンドウ左上からのマウスカーソル相対位置
    private Point lastMouseLocation; // 速度計算用の直前のマウス位置

    public MascotView(Mascot mascot, ImageCache imageCache, EventDispatcher dispatcher) {
        this.mascot = mascot;
        this.imageCache = imageCache;
        this.dispatcher = dispatcher;

        // ウィンドウの初期設定
        initWindow();
        // マウスハンドラの初期設定
        initMouseHandlers();
    }

    private void initWindow() {
        // 背景を完全に透過させる
        setBackground(new Color(0, 0, 0, 0));
        // 常に最前面に表示
        setAlwaysOnTop(true);
    }

    private void initMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // ドラッグ開始をマスコットに通知
                mascot.startDrag();
                // クリック位置(スクリーン座標)とマスコット座標(足元)の差分を記録
                Point mouseOnScreen = e.getLocationOnScreen();
                dragStartOffset = new Point(mouseOnScreen.x - mascot.getX(), mouseOnScreen.y - mascot.getY());
                lastMouseLocation = e.getLocationOnScreen();
                mascot.setVelocityX(0);
                mascot.setVelocityY(0);

                // イベントディスパッチャに通知
                dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.MOUSE_PRESSED, e, MascotView.this));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // ドラッグ終了をマスコットに通知
                mascot.endDrag();
                dragStartOffset = null;

                // イベントディスパッチャに通知
                dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.MOUSE_RELEASED, e, MascotView.this));
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartOffset != null) {
                    Point newLoc = e.getLocationOnScreen(); // 現在のマウス位置(スクリーン座標)

                    // 直前の位置との差分を速度として設定
                    if (lastMouseLocation != null) {
                        int vx = newLoc.x - lastMouseLocation.x;
                        int vy = newLoc.y - lastMouseLocation.y;
                        mascot.setVelocityX(vx);
                        mascot.setVelocityY(vy);
                        System.out.printf("[MascotView] Drag velocity: (%d, %d)%n", vx, vy);
                    }
                    lastMouseLocation = newLoc;

                    // マスコットの位置を更新
                    Point mascotPosition = new Point(newLoc.x - dragStartOffset.x, newLoc.y - dragStartOffset.y);
                    mascot.setAnchor(mascotPosition);

                    dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.MOUSE_DRAGGED, e, MascotView.this));
                }
            }
        });
    }

    /**
     * マスコットの状態に基づいて表示を更新します。
     * このメソッドはメインループから定期的に呼び出されることを想定しています。
     */
    public void update() {
        // 1. 現在のポーズから表示すべき画像を取得
        Animation animation = mascot.getAnimation();
        Pose pose = null;

        if (animation != null) {
            pose = animation.getPose();
            if (pose != null) {
                if (mascot.isLookRight()) {
                    this.currentImage = imageCache.getImage(pose.getImageName());
                } else {
                    this.currentImage = imageCache.getLeftImage(pose.getImageName());
                }
            }
        }

        // 2. 画像がなければウィンドウを非表示にする
        if (currentImage == null || pose == null) {
            if (isVisible()) {
                setVisible(false);
            }
            return;
        }

        // 3. ウィンドウの位置とサイズを更新
        Point anchor = mascot.getAnchor();
        Dimension size = new Dimension(currentImage.getWidth(), currentImage.getHeight());

        // 変更がなければ再描画だけ行う
        if (getLocation().equals(anchor) && getSize().equals(size) && isVisible()) {
            repaint();
            return;
        }

        setSize(size);

        // アンカー位置の決定
        Point imageAnchor = getAnchor();
        // ウィンドウの左上座標 = マスコットの座標 - アンカーオフセット
        setLocation(anchor.x - imageAnchor.x, anchor.y - imageAnchor.y);

        if (!isVisible()) {
            setVisible(true);
        }
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        // 前回の描画内容をクリア（透明色で塗りつぶす）
        // これを行わないと、前のフレームの画像が残ってしまいます
        g2d.setBackground(new Color(0, 0, 0, 0));
        g2d.clearRect(0, 0, getWidth(), getHeight());

        if (currentImage != null) {
            g2d.drawImage(currentImage, 0, 0, this);
        }
    }

    /**
     * 現在表示されているマスコットの画像の高さを返します。
     * @return 画像の高さ。画像がない場合は0。
     */
    public int getMascotHeight() {
        return (currentImage != null) ? currentImage.getHeight() : 0;
    }

    /**
     * 現在表示されているマスコットの画像の幅を返します。
     * @return 画像の幅。画像がない場合は0。
     */
    public int getMascotWidth() {
        return (currentImage != null) ? currentImage.getWidth() : 0;
    }

    /**
     * 現在のマスコットの状態（アニメーション・向き）に基づき、
     * 画像の左上を原点としたアンカーポイント（基準点）を計算して返します。
     * Mainクラスでの座標計算に使用されます。
     */
    public Point getAnchor() {
        Animation animation = mascot.getAnimation();
        Pose pose = (animation != null) ? animation.getPose() : null;

        if (pose == null) {
            return new Point(0, 0);
        }

        // 画像サイズが必要なためキャッシュから取得（描画更新前でも最新の情報を取得するため）
        BufferedImage image;
        if (mascot.isLookRight()) {
            image = imageCache.getImage(pose.getImageName());
        } else {
            image = imageCache.getLeftImage(pose.getImageName());
        }

        if (image == null) {
            return new Point(0, 0);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int anchorX;
        int anchorY;

        if (pose.getImageAnchor() != null) {
            anchorX = pose.getImageAnchor().x;
            anchorY = pose.getImageAnchor().y;
            // 左向きの場合は左右反転した位置をアンカーとする
            if (!mascot.isLookRight()) {
                anchorX = width - anchorX;
            }
        } else {
            // デフォルト: 底辺中央
            anchorX = width / 2;
            anchorY = height;
        }

        // ★修正: アンカーYが0の場合（XML設定ミスやパース失敗の可能性）、
        // 強制的に画像の下端（足元）をアンカーとする。
        // これにより「頭がタスクバーに張り付く」「天井を突き抜ける」現象を防ぐ。
        if (anchorY == 0 && height > 0) {
            anchorY = height;
        }

        return new Point(anchorX, anchorY);
    }
}
package com.group_finity.mascot.view;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.image.ImageCache;

import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Dimension;
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
                dragStartOffset = e.getPoint();

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
                    Point newLoc = e.getLocationOnScreen();
                    newLoc.translate(-dragStartOffset.x, -dragStartOffset.y);
                    mascot.setAnchor(newLoc);

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
        if (animation != null) {
            Pose pose = animation.getPose();
            if (pose != null) {
                if (mascot.isLookRight()) {
                    this.currentImage = imageCache.getImage(pose.getImageName());
                } else {
                    this.currentImage = imageCache.getLeftImage(pose.getImageName());
                }
            }
        }

        // 2. 画像がなければウィンドウを非表示にする
        if (currentImage == null) {
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
        setLocation(anchor);

        if (!isVisible()) {
            setVisible(true);
        }
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        // super.paint(g) は呼び出さない（背景描画をスキップするため）
        if (currentImage != null) {
            g.drawImage(currentImage, 0, 0, this);
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
}
package com.group_finity.mascot.view;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.image.ImageCache;
import com.group_finity.mascot.nativeaccess.NativeWindowUtil;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.awt.Window;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Active Rendering と UpdateLayeredWindow を使用した
 * 高パフォーマンスなマスコット表示ウィンドウ。
 * 
 * Swing (JWindow) ではなく java.awt.Window を直接使用し、
 * OSの再描画イベントに依存せず、メインループから draw() を呼び出すことで描画します。
 */
@SuppressWarnings("preview")
public class MascotWindow extends Window implements MascotView {

    private final Mascot mascot;
    private final ImageCache imageCache;

    // Back Buffer (裏画面)
    private BufferedImage backBuffer;
    private int[] bufferData;

    // Native Handle
    private MemorySegment hwndSegment;

    // Mouse Drag State
    private Point dragStartOffset;
    private Point lastMouseLocation;

    public MascotWindow(Mascot mascot, ImageCache imageCache) {
        super(null); // 親ウィンドウなし
        this.mascot = mascot;
        this.imageCache = imageCache;

        initWindow();
        initMouseHandlers();
    }

    private void initWindow() {
        // ウィンドウの初期設定
        setLayout(null);
        setAlwaysOnTop(true);
        setFocusable(false);
        // タスクバーに表示しないための設定などは別途必要だが、まずは表示優先

        // 1x1のサイズで初期化（後で画像サイズに合わせて拡張）
        setSize(1, 1);
        setVisible(true);

        // ネイティブハンドルの取得とレイヤードウィンドウ化
        try {
            Pointer hwndPointer = Native.getComponentPointer(this);
            long hwndValue = Pointer.nativeValue(hwndPointer);
            this.hwndSegment = MemorySegment.ofAddress(hwndValue);

            // WS_EX_LAYERED スタイルを適用
            long oldStyle = NativeWindowUtil.getWindowLongPtr(hwndSegment, NativeWindowUtil.GWL_EXSTYLE);
            NativeWindowUtil.setWindowLongPtr(hwndSegment, NativeWindowUtil.GWL_EXSTYLE,
                    oldStyle | NativeWindowUtil.WS_EX_LAYERED);

        } catch (Exception e) {
            e.printStackTrace();
        }
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

                // イベント通知 (必要に応じてMain側でDispatcherを取得して投げる設計も可だが、
                // ここではMascotの状態更新を優先)
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // ドラッグ終了をマスコットに通知
                mascot.endDrag();
                dragStartOffset = null;
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
                    }
                    lastMouseLocation = newLoc;

                    // マスコットの位置を更新
                    // MascotWindowはMainループで描画されるため、ここではMascotの座標だけ更新すればよい
                    // Mainループ内の setWindowPosPhysical でウィンドウ位置が同期される
                    Point mascotPosition = new Point(newLoc.x - dragStartOffset.x, newLoc.y - dragStartOffset.y);
                    mascot.setAnchor(new com.group_finity.mascot.type.NeoPoint(mascotPosition.x, mascotPosition.y));
                }
            }
        });
    }

    /**
     * マスコットを描画します。メインループから毎フレーム呼び出してください。
     */
    public void draw() {
        if (hwndSegment == null)
            return;

        // 1. 現在のポーズと画像を取得
        BufferedImage image = getCurrentImage();

        if (image == null)
            return;

        int width = image.getWidth();
        int height = image.getHeight();

        // 2. バックバッファの準備 (サイズが変わった場合のみ再生成)
        if (backBuffer == null || backBuffer.getWidth() != width || backBuffer.getHeight() != height) {
            // Windows GDI と互換性のある INT_ARGB_PRE (Pre-multiplied Alpha) を使用
            backBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            bufferData = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();
        }

        // 3. バックバッファへの描画 (クリア処理は画像の上書きで代用可能だが、念のため)
        // Graphics2D g = backBuffer.createGraphics();
        // g.setComposite(AlphaComposite.Clear);
        // g.fillRect(0, 0, width, height);
        // g.setComposite(AlphaComposite.Src);
        // g.drawImage(image, 0, 0, null);
        // g.dispose();

        // 高速化: Graphics2Dを使わず、ピクセルデータを直接コピーする
        // (元画像も TYPE_INT_ARGB_PRE であることが望ましい)
        if (image.getType() == BufferedImage.TYPE_INT_ARGB || image.getType() == BufferedImage.TYPE_INT_ARGB_PRE) {
            int[] srcData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(srcData, 0, bufferData, 0, srcData.length);
        } else {
            // 画像形式が異なる場合はGraphics2Dで安全に描画（フォールバック）
            java.awt.Graphics2D g = backBuffer.createGraphics();
            g.setComposite(AlphaComposite.Src); // 上書きモード
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        // 4. UpdateLayeredWindow で OS に転送
        try (Arena arena = Arena.ofConfined()) {
            // int[] 配列を MemorySegment にコピー
            // MemorySegment.copy(int[], ...) のオーバーロード挙動が不安定な可能性があるため、
            // MemorySegment.ofArray() を経由してバイト単位でコピーする方式に変更
            MemorySegment srcSegment = MemorySegment.ofArray(bufferData);
            MemorySegment pBits = arena.allocate(srcSegment.byteSize());
            MemorySegment.copy(srcSegment, 0, pBits, 0, srcSegment.byteSize());

            NativeWindowUtil.updateLayeredWindow(hwndSegment, pBits, width, height);
        } catch (Exception e) {
            System.err.println("[MascotWindow] Draw failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getMascotWidth() {
        // 簡易実装: 現在のバックバッファサイズまたはキャッシュから取得
        if (backBuffer != null)
            return backBuffer.getWidth();
        BufferedImage img = getCurrentImage();
        return (img != null) ? img.getWidth() : 128; // デフォルトサイズを返す
    }

    public int getMascotHeight() {
        if (backBuffer != null)
            return backBuffer.getHeight();
        BufferedImage img = getCurrentImage();
        return (img != null) ? img.getHeight() : 128; // デフォルトサイズを返す
    }

    public Point getAnchor() {
        BufferedImage image = getCurrentImage();
        if (image == null)
            return new Point(0, 0);

        Animation animation = mascot.getAnimation();
        Pose pose = (animation != null) ? animation.getPose() : null;
        // getCurrentImageがnullでないならposeもnullではないはずだが念のため
        if (pose == null)
            return new Point(0, 0);

        int width = image.getWidth();
        int height = image.getHeight();

        int anchorX;
        int anchorY;

        if (pose.getImageAnchor() != null) {
            // XMLでアンカーが指定されていれば、それを使用
            anchorX = pose.getImageAnchor().x;
            anchorY = pose.getImageAnchor().y;
        } else {
            // XMLでアンカーが指定されていない場合のデフォルト値
            anchorX = width / 2;
            anchorY = height;
        }

        // 元画像が左向きのため、右を向くときにアンカーを反転させる
        if (mascot.isLookRight()) {
            anchorX = width - anchorX;
        }
        if (anchorY == 0 && height > 0) {
            anchorY = height;
        }

        return new Point(anchorX, anchorY);
    }

    private BufferedImage getCurrentImage() {
        Animation animation = mascot.getAnimation();
        Pose pose = (animation != null) ? animation.getPose() : null;
        if (pose == null)
            return null;

        if (mascot.isLookRight()) {
            // 元画像が左向きのため、右を向くときは反転画像を取得
            return imageCache.getRightImage(pose.getImageName());
        } else {
            // 左を向くときはオリジナル画像（左向き）を取得
            return imageCache.getImage(pose.getImageName());
        }
    }
}
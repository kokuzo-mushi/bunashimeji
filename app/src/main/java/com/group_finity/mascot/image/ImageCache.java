package com.group_finity.mascot.image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class ImageCache {
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private Path baseDir;

    public ImageCache(Path baseDir) {
        this.baseDir = baseDir;
    }

    public void updateBaseDirectory(Path newBaseDir) {
        this.baseDir = newBaseDir;
        this.cache.clear(); // スキン変更時はキャッシュをクリア
    }

    public BufferedImage getImage(String name) {
        if (!cache.containsKey(name)) {
            loadImage(name);
        }
        return cache.get(name);
    }

    public BufferedImage getRightImage(String name) {
        String key = name + ":right";
        if (!cache.containsKey(key)) {
            // 命名規則: [ActionName][Number].png -> [ActionName]R[Number].png
            String rightName = name.replaceAll("^([a-zA-Z]+)(\\d+)\\.png$", "$1R$2.png");
            Path rightFile = baseDir.resolve(rightName);

            if (Files.exists(rightFile)) {
                try {
                    System.out.println("[ImageCache] Loading explicit right image: " + rightName);
                    BufferedImage img = ImageIO.read(rightFile.toFile());
                    cache.put(key, img);
                    return img;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // 元画像（左向き）を取得
            BufferedImage left = getImage(name);
            if (left == null) return null;
            
            // 画像を左右反転してキャッシュに登録
            BufferedImage right = flipImage(left);
            cache.put(key, right);
        }
        return cache.get(key);
    }

    private void loadImage(String name) {
        Path file = baseDir.resolve(name);
        try {
            if (Files.exists(file)) {
                BufferedImage img = ImageIO.read(file.toFile());
                cache.put(name, img);
            } else {
                System.out.println("[ImageCache] Image not found: " + name + " (Fallback to Stay1.png)");
                
                // フォールバック: 画像がない場合は Stay1.png (デフォルト立ち絵) で代用する
                // これにより、赤いダミー画像による点滅を防ぐ
                Path defaultFile = baseDir.resolve("Stay1.png");
                if (Files.exists(defaultFile)) {
                    try {
                        BufferedImage img = ImageIO.read(defaultFile.toFile());
                        cache.put(name, img);
                        return;
                    } catch (IOException ignored) {}
                }
                
                // Stay1.png すらない場合はダミー画像を生成
                cache.put(name, createDummyImage());
            }
        } catch (IOException e) {
            e.printStackTrace();
            cache.put(name, createDummyImage());
        }
    }

    private BufferedImage flipImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        // drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
        // 転送先(0,0->w,h)に対し、転送元を(w,0->0,h)と指定することでX軸を反転させる
        g.drawImage(src, 0, 0, w, h, w, 0, 0, h, null);
        g.dispose();
        return dest;
    }

    private BufferedImage createDummyImage() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.drawRect(0, 0, 127, 127);
        g.drawString("Dummy", 10, 64);
        g.dispose();
        return img;
    }
}
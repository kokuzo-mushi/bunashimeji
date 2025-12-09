package com.group_finity.mascot.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 画像を読み込み、キャッシュするクラス。
 * 同じ画像を何度も読み込むのを防ぎます。
 */
public class ImageCache {

    private final Path imageBasePath;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    public ImageCache(Path imageBasePath) {
        this.imageBasePath = imageBasePath;
    }

    /**
     * 指定された名前の画像をキャッシュから取得します。
     * キャッシュにない場合はファイルから読み込みます。
     * @param imageName 画像ファイル名 (例: "shime1.png")
     * @return 読み込まれた画像。見つからない場合はnull。
     */
    public BufferedImage getImage(String imageName) {
        if (imageName == null || imageName.isEmpty()) {
            return null;
        }

        if (cache.containsKey(imageName)) {
            return cache.get(imageName);
        }

        Path imagePath = imageBasePath.resolve(imageName);
        if (!Files.exists(imagePath)) {
            System.err.println("Image file not found: " + imagePath);
            cache.put(imageName, null); // 見つからなかった情報もキャッシュ
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            cache.put(imageName, image);
            return image;
        } catch (IOException e) {
            System.err.println("Failed to read image file: " + imagePath);
            e.printStackTrace();
            cache.put(imageName, null); // エラーがあった場合もキャッシュ
            return null;
        }
    }
}
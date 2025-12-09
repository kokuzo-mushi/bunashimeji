package com.group_finity.mascot.image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
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

    /**
     * 指定された画像の水平反転版をキャッシュから取得します。
     * キャッシュにない場合は、元の画像を読み込んで反転イメージを生成します。
     * @param imageName 元の画像ファイル名
     * @return 水平反転された画像。元の画像が見つからない場合はnull。
     */
    public BufferedImage getFlippedImage(String imageName) {
        if (imageName == null || imageName.isEmpty()) {
            return null;
        }

        final String flippedKey = imageName + ":flipped";
        if (cache.containsKey(flippedKey)) {
            return cache.get(flippedKey);
        }

        // 元の画像を（必要なら読み込んで）取得
        BufferedImage originalImage = getImage(imageName);
        if (originalImage == null) {
            return null; // 元画像がないので反転も不可
        }

        // 水平反転した画像を生成
        BufferedImage flippedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = flippedImage.createGraphics();
        g.drawImage(originalImage, originalImage.getWidth(), 0, -originalImage.getWidth(), originalImage.getHeight(), null);
        g.dispose();

        cache.put(flippedKey, flippedImage);

        return flippedImage;
    }

    /**
     * 左向き用の画像を取得します。
     * <p>
     * まず、ファイル名に "L" を付けた左向き専用画像 (例: shime1.png -> shime1L.png) を探します。
     * 見つかった場合はその画像を返します。
     * 見つからなかった場合は、元の画像を水平反転したものを返します。
     *
     * @param rightImageName 右向き用の画像ファイル名
     * @return 左向き用の画像。処理に失敗した場合はnull。
     */
    public BufferedImage getLeftImage(String rightImageName) {
        if (rightImageName == null || rightImageName.isEmpty()) {
            return null;
        }

        // 1. 左向き画像のファイル名を生成する
        String leftImageName = deriveLeftImageName(rightImageName);

        // 2. 左向き専用の画像が存在するか確認する
        // getImageは内部でキャッシュとファイル存在チェックを行う
        BufferedImage specificLeftImage = getImage(leftImageName);

        if (specificLeftImage != null) {
            // 3. 専用画像が見つかった場合はそれを返す
            return specificLeftImage;
        }

        // 4. 見つからなかった場合は、元の画像を反転させて返す（下位互換性のため）
        return getFlippedImage(rightImageName);
    }

    private String deriveLeftImageName(String baseImageName) {
        int dotIndex = baseImageName.lastIndexOf('.');
        return (dotIndex == -1)
                ? baseImageName + "L" // 拡張子なし
                : baseImageName.substring(0, dotIndex) + "L" + baseImageName.substring(dotIndex);
    }
}
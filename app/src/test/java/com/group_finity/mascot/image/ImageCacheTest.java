package com.group_finity.mascot.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageCacheTest {

    @Test
    void getImage_shouldReturnDummyImage_whenFileDoesNotExist(@TempDir Path tempDir) {
        // Arrange
        // 空の一時ディレクトリを画像フォルダとして指定
        ImageCache imageCache = new ImageCache(tempDir);
        String nonExistentImageName = "missing_texture.png";

        // Act
        BufferedImage image = imageCache.getImage(nonExistentImageName);

        // Assert
        assertNotNull(image, "ファイルが存在しない場合はダミー画像が生成されるべきです");
        assertEquals(128, image.getWidth(), "ダミー画像の幅は128pxであるべきです");
        assertEquals(128, image.getHeight(), "ダミー画像の高さは128pxであるべきです");
    }
}
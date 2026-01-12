package com.group_finity.mascot.image

import androidx.compose.ui.graphics.ImageBitmap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ImageCacheTest {

    @Test
    fun getImage_shouldReturnDummyImage_whenFileDoesNotExist(@TempDir tempDir: Path) {
        // Arrange
        // 空の一時ディレクトリを画像フォルダとして指定
        val imageCache = ImageCache(tempDir)
        val nonExistentImageName = "missing_texture.png"

        // Act
        // Kotlin版は ImageBitmap? を返す
        val image: ImageBitmap? = imageCache.getImage(nonExistentImageName)

        // Assert
        assertNotNull(image, "ファイルが存在しない場合はダミー画像が生成されるべきです")
        assertEquals(128, image!!.width, "ダミー画像の幅は128pxであるべきです")
        assertEquals(128, image.height, "ダミー画像の高さは128pxであるべきです")
    }
}
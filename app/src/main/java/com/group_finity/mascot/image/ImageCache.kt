package com.group_finity.mascot.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ImageCache(private var baseDir: Path) {
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun updateBaseDirectory(newBaseDir: Path) {
        this.baseDir = newBaseDir
        this.cache.clear()
    }

    fun getImage(name: String): ImageBitmap? {
        return cache.getOrPut(name) {
            val img = loadRawImage(name) ?: return null
            img.toComposeImageBitmap()
        }
    }

    fun getRightImage(name: String): ImageBitmap? {
        val key = "$name:right"
        if (cache.containsKey(key)) {
            return cache[key]
        }

        // 命名規則: [ActionName][Number].png -> [ActionName]R[Number].png
        val rightName = name.replace(Regex("^([a-zA-Z]+)(\\d+)\\.png$"), "$1R$2.png")
        val rightFile = baseDir.resolve(rightName)

        if (Files.exists(rightFile)) {
            try {
                val img = ImageIO.read(rightFile.toFile())
                val bitmap = img.toComposeImageBitmap()
                cache[key] = bitmap
                return bitmap
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // 元画像（左向き）をロードして反転
        // キャッシュにはImageBitmapしか入っていないため、反転用に再度BufferedImageとしてロードする
        val left = loadRawImage(name) ?: return null
        val right = flipImage(left)
        val bitmap = right.toComposeImageBitmap()
        cache[key] = bitmap
        return bitmap
    }

    private fun loadRawImage(name: String): BufferedImage? {
        val file = baseDir.resolve(name)
        try {
            if (Files.exists(file)) {
                return ImageIO.read(file.toFile())
            } else {
                // フォールバック: 画像がない場合は Stay1.png (デフォルト立ち絵) で代用する
                val defaultFile = baseDir.resolve("Stay1.png")
                if (Files.exists(defaultFile)) {
                    try {
                        return ImageIO.read(defaultFile.toFile())
                    } catch (ignored: IOException) {}
                }
                
                return createDummyImage()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return createDummyImage()
        }
    }

    private fun flipImage(src: BufferedImage): BufferedImage {
        val w = src.width
        val h = src.height
        val dest = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = dest.createGraphics()
        g.drawImage(src, 0, 0, w, h, w, 0, 0, h, null)
        g.dispose()
        return dest
    }

    private fun createDummyImage(): BufferedImage {
        val img = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.RED
        g.drawRect(0, 0, 127, 127)
        g.drawString("Dummy", 10, 64)
        g.dispose()
        return img
    }
}
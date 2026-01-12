package com.group_finity.mascot.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.group_finity.mascot.Mascot
import com.group_finity.mascot.image.ImageCache
import com.group_finity.mascot.type.NeoPoint
import com.group_finity.mascot.view.MascotView
import java.awt.Point

class MascotViewImpl(
    private val mascot: Mascot,
    private val imageCache: ImageCache
) : MascotView {
    
    private var _visible = true

    override fun setVisible(b: Boolean) {
        _visible = b
    }

    override fun isVisible(): Boolean {
        return _visible
    }

    override fun draw() {
        // Compose handles drawing based on state.
    }

    override fun getMascotWidth(): Int {
        return getCurrentImage()?.width ?: 128
    }

    override fun getMascotHeight(): Int {
        return getCurrentImage()?.height ?: 128
    }

    override fun getAnchor(): Point {
        val image = getCurrentImage() ?: return Point(0, 0)
        val animation = mascot.animation
        val pose = animation?.pose ?: return Point(0, 0)
        
        val width = image.width
        val height = image.height
        
        var anchorX: Int
        var anchorY: Int
        
        if (pose.imageAnchor != null) {
            anchorX = pose.imageAnchor.x
            anchorY = pose.imageAnchor.y
        } else {
            anchorX = width / 2
            anchorY = height
        }
        
        if (mascot.isLookRight) {
            anchorX = width - anchorX
        }
        if (anchorY == 0 && height > 0) {
            anchorY = height
        }
        
        return Point(anchorX, anchorY)
    }

    /**
     * AWT依存を排除した新しいアンカー取得メソッド。
     * Phase 5: GUIのCompose化に伴い、java.awt.Point ではなく NeoPoint を返す。
     */
    fun getNeoAnchor(): NeoPoint {
        val image = getCurrentImage() ?: return NeoPoint(0, 0)
        val animation = mascot.animation
        val pose = animation?.pose ?: return NeoPoint(0, 0)

        val width = image.width
        val height = image.height

        var anchorX: Int
        var anchorY: Int

        if (pose.imageAnchor != null) {
            anchorX = pose.imageAnchor.x
            anchorY = pose.imageAnchor.y
        } else {
            anchorX = width / 2
            anchorY = height
        }

        if (mascot.isLookRight) {
            anchorX = width - anchorX
        }
        if (anchorY == 0 && height > 0) {
            anchorY = height
        }

        return NeoPoint(anchorX, anchorY)
    }

    fun getCurrentImage(): ImageBitmap? {
        val animation = mascot.animation
        val pose = animation?.pose ?: return null
        return if (mascot.isLookRight) {
            imageCache.getRightImage(pose.imageName)
        } else {
            imageCache.getImage(pose.imageName)
        }
    }
}
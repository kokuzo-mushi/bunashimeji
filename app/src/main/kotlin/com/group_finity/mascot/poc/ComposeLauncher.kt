package com.group_finity.mascot.poc

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Window
import java.io.File
import javax.imageio.ImageIO
import java.lang.foreign.MemorySegment
import com.group_finity.mascot.nativeaccess.NativeWindowUtil

fun main() = application {
    val windowState = rememberWindowState(width = 300.dp, height = 300.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        title = "Shimeji PoC"
    ) {
        val window = this.window // ComposeWindow (extends JFrame)

        // Load Image
        val imageBitmap = remember {
            try {
                // Try loading from file system "img/White/Stay1.png" (relative to project root)
                val file = File("img/White/Stay1.png")
                if (file.exists()) {
                     // Using ImageIO to read and convert to Compose Bitmap
                     ImageIO.read(file).toComposeImageBitmap()
                } else {
                     // Fallback or resource loading if needed
                     println("Image not found at ${file.absolutePath}")
                     null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Shimeji",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Native Interop Loop
        LaunchedEffect(Unit) {
             // Direct access to windowHandle (Available in Compose Multiplatform 1.5+)
            val hwnd = window.windowHandle
            println("PoC Window HWND: $hwnd")
            
            if (hwnd != 0L) {
                val hwndSegment = MemorySegment.ofAddress(hwnd)
                
                // Move window in a circle to prove Panama control
                var angle = 0.0
                val centerX = 800 // Adjusted for larger screen likely
                val centerY = 400
                val radius = 150
                
                while (true) {
                    val x = centerX + (Math.cos(angle) * radius).toInt()
                    val y = centerY + (Math.sin(angle) * radius).toInt()
                    
                    // Call Panama
                    try {
                        NativeWindowUtil.setWindowPosPhysical(hwndSegment, x, y, 128, 128)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    angle += 0.05
                    if (angle > Math.PI * 2) angle = 0.0
                    
                    kotlinx.coroutines.delay(16) // ~60 FPS
                }
            }
        }
    }
}

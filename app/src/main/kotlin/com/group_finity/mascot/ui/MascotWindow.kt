package com.group_finity.mascot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Window
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.group_finity.mascot.manager.MascotContext
import com.group_finity.mascot.manager.MascotManager
import com.group_finity.mascot.type.NeoRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.Point

@Composable
fun MascotWindow(
    mascotContext: MascotContext,
    mascotManager: MascotManager,
    workArea: NeoRect,
    allMascotsProvider: () -> List<MascotContext>,
    gravity: State<Float>,
    timeScale: State<Float>,
    icon: ImageBitmap?,
    onNewMascot: () -> Unit,
    onRemoveMascot: (MascotContext) -> Unit,
    onOpenSettings: () -> Unit
) {
    val mascot = mascotContext.mascot
    val adapter = mascotContext.view as? MascotViewImpl

    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    // Window State
    val windowState = rememberWindowState(
        position = WindowPosition(mascot.anchor.x().dp, mascot.anchor.y().dp), // Initial pos
        width = 128.dp, // Initial size, updated by image
        height = 128.dp
    )

    // Icon Painter
    val iconPainter = remember(icon) { icon?.let { BitmapPainter(it) } }

    // Animation Loop
    LaunchedEffect(mascotContext) {
        var tickCount = 0L
        while (isActive) {
            val startTime = System.nanoTime()

            // 1. Tick Logic
            val mousePos = java.awt.MouseInfo.getPointerInfo().location
            val mouseMap = mapOf("x" to mousePos.x, "y" to mousePos.y)

            mascotManager.tick(
                mascotContext,
                allMascotsProvider(),
                workArea,
                gravity.value.toInt(),
                tickCount,
                mouseMap,
                null // limitWindow
            )

            // 2. Update Image
            val bufImg = adapter?.getCurrentImage()
            if (bufImg != null) {
                currentImage = bufImg.toComposeImageBitmap()
                
                // マスコットの座標に合わせてウィンドウ位置とサイズを更新
                val anchor = adapter.getAnchor()
                windowState.position = WindowPosition((mascot.x - anchor.x).dp, (mascot.y - anchor.y).dp)
                windowState.size = DpSize(bufImg.width.dp, bufImg.height.dp)
            }

            // 3. Wait for next frame (approx 60 FPS)
            val elapsed = (System.nanoTime() - startTime) / 1_000_000
            val wait = (1000 / (60 * timeScale.value)).toLong() - elapsed
            if (wait > 0) delay(wait)
            tickCount++
        }
    }

    Window(
        onCloseRequest = { onRemoveMascot(mascotContext) },
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        title = "Shimeji",
        icon = iconPainter, // アイコンを設定
        state = windowState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentImage != null) {
                // Context Menu
                if (showMenu) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(onClick = { onNewMascot(); showMenu = false }) { Text("増やす") }
                        DropdownMenuItem(onClick = { onOpenSettings(); showMenu = false }) { Text("設定") }
                        DropdownMenuItem(onClick = { onRemoveMascot(mascotContext) }) { Text("ばいばい") }
                    }
                }

                Image(
                    bitmap = currentImage!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                        showMenu = true
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { mascot.isBeingDragged = true },
                                onDragEnd = { mascot.isBeingDragged = false },
                                onDragCancel = { mascot.isBeingDragged = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val awtPoint = java.awt.MouseInfo.getPointerInfo().location
                                mascot.anchor = com.group_finity.mascot.type.NeoPoint(awtPoint.x, awtPoint.y)
                            }
                        }
                )
            }
        }
    }
}
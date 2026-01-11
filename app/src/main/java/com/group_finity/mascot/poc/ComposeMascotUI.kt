package com.group_finity.mascot.poc

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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.group_finity.mascot.manager.MascotContext
import com.group_finity.mascot.manager.MascotManager
import com.group_finity.mascot.nativeaccess.NativeWindowUtil
import com.group_finity.mascot.type.NeoRect
import kotlinx.coroutines.isActive
import java.awt.Point
import java.lang.foreign.MemorySegment
import java.util.HashMap

@Composable
fun MascotWindow(
    mascotContext: MascotContext,
    mascotManager: MascotManager,
    workArea: NeoRect,
    allMascotsProvider: () -> List<MascotContext>,
    gravity: State<Float>,
    timeScale: State<Float>,
    onNewMascot: () -> Unit,
    onRemoveMascot: (MascotContext) -> Unit,
    onOpenSettings: () -> Unit
) {
    val mascot = mascotContext.mascot
    // Cast unsafely relying on ComposeLauncher setup - in production we'd fix the generic View type
    val adapter = mascotContext.view as? ComposeMascotAdapter ?: return

    // UI State
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    // Window Setup
    val windowState = rememberWindowState(
        width = 300.dp,
        height = 300.dp,
        position = WindowPosition(0.dp, 0.dp)
    )
    
    val currentGravity by rememberUpdatedState(gravity)
    val currentTimeScale by rememberUpdatedState(timeScale)

    Window(
        onCloseRequest = { onRemoveMascot(mascotContext) },
        state = windowState,
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        title = "Shimeji Neo " + mascot.toString() // Debug title
    ) {
        val hwnd = remember { mutableStateOf<MemorySegment?>(null) }

        // HWND Discovery
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            val pid = ProcessHandle.current().pid()
            val expectedTitle = "Shimeji Neo " + mascot.toString()
            
            // Retry a few times if not found immediately
            for (i in 0..20) {
                var found: MemorySegment? = null
                NativeWindowUtil.enumWindows({ h, _ ->
                    val wPid = NativeWindowUtil.getWindowThreadProcessId(h)
                    if (Integer.toUnsignedLong(wPid) == pid && NativeWindowUtil.isWindowVisible(h)) {
                        val title = NativeWindowUtil.getWindowText(h)
                        if (title == expectedTitle) {
                            found = h
                            false // Stop enumeration
                        } else {
                            true // Continue
                        }
                    } else {
                        true
                    }
                }, 0)
                
                if (found != null) {
                    hwnd.value = found
                    break
                }
                kotlinx.coroutines.delay(100)
            }
        }

        // Animation Loop
        LaunchedEffect(hwnd.value) {
            val h = hwnd.value ?: return@LaunchedEffect
            
            var tickCount = 0L
            
            while (isActive) {
                val loopStart = System.nanoTime()
                
                val mousePos = java.awt.MouseInfo.getPointerInfo().location
                val mouseMap = HashMap<String, Int>()
                mouseMap["x"] = mousePos.x
                mouseMap["y"] = mousePos.y

                try {
                    // Update: Logic
                    mascotManager.tick(mascotContext, allMascotsProvider(), workArea, currentGravity.value.toInt(), tickCount, mouseMap, null)

                    // Render: Update Image
                    val bufferedImage = adapter.getCurrentImage()
                    if (bufferedImage != null) {
                         currentImage = bufferedImage.toComposeImageBitmap()

                         // Render: Update Window Position
                         val anchor = adapter.getAnchor()
                         val winX = mascot.x - anchor.x
                         val winY = mascot.y - anchor.y
                         val w = bufferedImage.width
                         val hImg = bufferedImage.height
                         
                         NativeWindowUtil.setWindowPosPhysical(h, winX, winY, w, hImg)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                tickCount++
                val elapsed = System.nanoTime() - loopStart
                
                // Time Scale logic
                val targetDelayNano = (1_000_000_000 / 60) / currentTimeScale.value
                val wait = targetDelayNano - elapsed
                if (wait > 0) {
                    kotlinx.coroutines.delay(wait.toLong() / 1_000_000)
                }
            }
        }

        // Render UI
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentImage != null) {
                 // Context Menu
                if (showMenu) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            onNewMascot()
                            showMenu = false
                        }) {
                            Text("増やす")
                        }
                        DropdownMenuItem(onClick = {
                            onOpenSettings()
                            showMenu = false
                        }) {
                             Text("設定")
                        }
                        DropdownMenuItem(onClick = {
                            onRemoveMascot(mascotContext)
                        }) {
                            Text("ばいばい")
                        }
                    }
                }

                Image(
                    bitmap = currentImage!!,
                    contentDescription = "Mascot",
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
                            var dragStartOffset = Point(0, 0)
                            var lastMousePos = Point(0, 0)
                            detectDragGestures(
                                onDragStart = {
                                    val mouseAppPos = java.awt.MouseInfo.getPointerInfo().location                                
                                    dragStartOffset = Point(mouseAppPos.x - mascot.x, mouseAppPos.y - mascot.y)
                                    lastMousePos = mouseAppPos
                                    mascot.isBeingDragged = true
                                    mascot.velocityX = 0
                                    mascot.velocityY = 0
                                },
                                onDragEnd = {
                                    mascot.isBeingDragged = false
                                },
                                onDragCancel = {
                                    mascot.isBeingDragged = false
                                },
                                onDrag = { change, dragAmount -> // change is PointerInputChange
                                    val mousePos = java.awt.MouseInfo.getPointerInfo().location
                                    mascot.x = mousePos.x - dragStartOffset.x
                                    mascot.y = mousePos.y - dragStartOffset.y
                                    
                                    val vx = mousePos.x - lastMousePos.x
                                    val vy = mousePos.y - lastMousePos.y
                                    lastMousePos = mousePos
                                    
                                    mascot.velocityX = vx
                                    mascot.velocityY = vy
                                }
                            )
                        }
                )
            }
        }
    }
}

package com.group_finity.mascot.ui

import androidx.compose.runtime.*
import androidx.compose.ui.window.application
import com.group_finity.mascot.Mascot
import com.group_finity.mascot.behavior.Configuration
import com.group_finity.mascot.image.ImageCache
import com.group_finity.mascot.manager.MascotContext
import com.group_finity.mascot.manager.MascotManager
import com.group_finity.mascot.script.ScriptEngineManager
import com.group_finity.mascot.config.XmlToYamlConverter
import com.group_finity.mascot.trigger.EventDispatcher
import com.group_finity.mascot.platform.Platform
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext
import com.group_finity.mascot.nativeaccess.NativeWindowUtil
import com.group_finity.mascot.type.NeoPoint
import com.group_finity.mascot.type.NeoRect
import com.group_finity.mascot.view.MascotView
import java.awt.Point
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.nio.file.Files
import java.util.HashMap
import androidx.compose.ui.window.Tray
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.window.Window

fun main() = application {
    // 1. Initialize Global Resources (Once)
    
    // XML -> YAML 自動変換ロジック
    // YAMLファイルが存在しない場合、XMLから生成する
    val actionsXml = Path.of("conf/actions.xml")
    val actionsYaml = Path.of("conf/actions.yaml")
    if (Files.exists(actionsXml) && !Files.exists(actionsYaml)) {
        try {
            XmlToYamlConverter.convert(actionsXml, actionsYaml, "actions")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val behaviorsXml = Path.of("conf/behaviors.xml")
    val behaviorsYaml = Path.of("conf/behaviors.yaml")
    if (Files.exists(behaviorsXml) && !Files.exists(behaviorsYaml)) {
        try {
            XmlToYamlConverter.convert(behaviorsXml, behaviorsYaml, "behaviors")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // TODO: behavior.Configuration が YAML 対応したら、ここで .yaml パスを渡すように変更する
    val config = remember { Configuration(actionsXml, behaviorsXml) }
    
    // Separate ImageCache for each skin path
    val imageCaches = remember { mutableMapOf<Path, ImageCache>() }
    
    val workArea = remember {
        NativeWindowUtil.getPrimaryMonitorWorkArea() ?: NeoRect(0, 0, 1024, 768)
    }
    
    val mascotManager = remember { MascotManager() }

    // Factory Function
    fun createMascot(skinPath: Path): MascotContext? {
        return try {
            val mascot = Mascot()
            mascot.anchor = NeoPoint(workArea.x() + workArea.width() / 2, workArea.y() - 256)
            
            val contextVariables = HashMap<String, Any>()
            val workAreaMap = HashMap<String, Int>()
            workAreaMap["x"] = workArea.x()
            workAreaMap["y"] = workArea.y()
            workAreaMap["width"] = workArea.width()
            workAreaMap["height"] = workArea.height()
            workAreaMap["right"] = workArea.x() + workArea.width()
            workAreaMap["bottom"] = workArea.y() + workArea.height()
            contextVariables["workArea"] = workAreaMap
            contextVariables["mascot"] = mascot
            contextVariables["time"] = 0L
            contextVariables["mouse"] = object : HashMap<String, Int>() {
                init {
                    put("x", 0)
                    put("y", 0)
                }
            }
            contextVariables["distToWallTop"] = 0
            contextVariables["signedDistToWallTop"] = 0
            contextVariables["mascot.distToFloorLeft"] = 0
            contextVariables["mascot.distToFloorRight"] = 0
            contextVariables["isOnEdge"] = false
             val nearestMascotMap = HashMap<String, Any>()
            nearestMascotMap["distance"] = 999999.0
            nearestMascotMap["x"] = 0
            contextVariables["nearestMascot"] = nearestMascotMap

            // Script Engine
            val jsContext = ScriptEngineManager.createMascotContext(contextVariables)
            mascot.jsContext = jsContext.context

            // Dispatcher & Adapter
            val evalContext = EvaluationContext(contextVariables)
            val dispatcher = EventDispatcher(evalContext, mascot)
            
            // Get or create ImageCache for this skin
            val cache = imageCaches.getOrPut(skinPath) { ImageCache(skinPath) }
            val adapter = MascotViewImpl(mascot, cache)
            
            for (behavior in config.behaviors) {
                dispatcher.registerTrigger(behavior)
            }
            
            MascotContext(mascot, adapter, dispatcher, evalContext, System.currentTimeMillis())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 2. States
    val gravity = remember { mutableStateOf(1.0f) }
    val timeScale = remember { mutableStateOf(1.0f) }
    var showSettings by remember { mutableStateOf(false) }
    var showSkinSelector by remember { mutableStateOf(true) } // SHow selector at start

    // 3. State: List of Mascots
    val mascotList = remember { mutableStateListOf<MascotContext>() }

    // Initialize Platform for Actions (Breed, Dig, Gather)
    DisposableEffect(Unit) {
        val platform = object : Platform {
            override fun createMascot(x: Int, y: Int, vx: Int, vy: Int) {
                // Randomly select a skin for the new mascot
                val imgDir = File("img")
                val skins = imgDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                val skinPath = if (skins.isNotEmpty()) skins.random().toPath() else Path.of("img/Default")

                val newMascotCtx = createMascot(skinPath)
                if (newMascotCtx != null) {
                    newMascotCtx.mascot.anchor = NeoPoint(x, y)
                    newMascotCtx.mascot.velocityX = vx
                    newMascotCtx.mascot.velocityY = vy
                    mascotList.add(newMascotCtx)
                }
            }

            override fun removeMascot(mascot: Mascot) {
                val target = mascotList.find { it.mascot == mascot }
                if (target != null) {
                    mascotList.remove(target)
                }
            }

            override fun getNearestMascot(mascot: Mascot): Mascot? {
                return mascotManager.getNearestMascot(mascot, mascotList)
            }
        }
        Platform.setInstance(platform)
        onDispose {
            Platform.setInstance(null)
        }
    }

    // If no mascots and skin selector is closed, maybe exit? Or Keep open.
    // For PoC, let's just keep app running.

    // 4. Settings Window
    if (showSettings) {
        SettingsScreen(
            behaviors = config.behaviors,
            gravity = gravity,
            timeScale = timeScale,
            onClose = { showSettings = false }
        )
    }

    // 5. Skin Selector Window
    if (showSkinSelector) {
        SkinSelectorScreen(
            imgDir = Path.of("img"),
            onSkinSelected = { skinPath ->
                val newMascot = createMascot(skinPath)
                if (newMascot != null) {
                     // Offset slightly to avoid overlap if multiple added
                     if (mascotList.isNotEmpty()) {
                        val last = mascotList.last()
                        newMascot.mascot.anchor = NeoPoint(
                             last.mascot.anchor.x() - 50,
                             last.mascot.anchor.y() - 50
                        )
                     }
                     mascotList.add(newMascot)
                }
                showSkinSelector = false
            },
            onClose = {
                // If it's the very start and they cancel, maybe exit?
                if (mascotList.isEmpty()) {
                    exitApplication()
                }
                showSkinSelector = false
            }
        )
    }

    // 6. Restoration Manager Tick Loop
    LaunchedEffect(Unit) {
        while (isActive) {
            com.group_finity.mascot.manager.WindowRestorationManager.getInstance().tick()
            delay(16) // Update at ~60FPS for smooth restoration animation
        }
    }

    // 7. System Tray
    val trayIcon = remember {
        try {
            // Try to find an icon
            var iconFile = File("img/White/shime1.png")
            if (!iconFile.exists()) iconFile = File("img/White/icon.png")
            // Fallback scan
            if (!iconFile.exists()) {
                 val imgDir = File("img")
                 if (imgDir.exists()) {
                     imgDir.listFiles()?.firstOrNull { it.isDirectory }?.let { dir ->
                         val f = File(dir, "shime1.png")
                         if (f.exists()) iconFile = f
                     }
                 }
            }
            if (iconFile.exists()) {
                val bufferedImage = ImageIO.read(iconFile)
                bufferedImage.toComposeImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    if (trayIcon != null) {
        Tray(
            icon = BitmapPainter(trayIcon),
            menu = {
                Item("増やす (Random)", onClick = {
                    val imgDir = java.io.File("img")
                    if (imgDir.exists()) {
                        val skins = imgDir.listFiles()?.filter { it.isDirectory }
                        if (!skins.isNullOrEmpty()) {
                            val randomSkin = skins.random()
                            val newMascot = createMascot(randomSkin.toPath())
                            if (newMascot != null) {
                                if (mascotList.isNotEmpty()) {
                                    val last = mascotList.last()
                                    newMascot.mascot.anchor = NeoPoint(
                                         last.mascot.anchor.x() - 50,
                                         last.mascot.anchor.y() - 50
                                    )
                                }
                                mascotList.add(newMascot)
                            }
                        }
                    }
                })
                Item("あつまれ！", onClick = {
                    val mousePos = NativeWindowUtil.getCursorPos()
                    if (mousePos != null) {
                        mascotList.forEach { ctx ->
                            ctx.mascot.anchor = mousePos
                            ctx.mascot.isGrounded = false
                        }
                    }
                })
                Item("一匹にする", onClick = {
                    if (mascotList.size > 1) {
                        val survivor = mascotList.first()
                        mascotList.clear()
                        mascotList.add(survivor)
                    }
                })
                Item("ウィンドウを戻す", onClick = {
                    com.group_finity.mascot.manager.WindowRestorationManager.getInstance().restoreAllWindows()
                })
                Item("設定", onClick = { showSettings = true })
                Item("ばいばい", onClick = { exitApplication() })
            }
        )
    }

    // 8. Render Windows for each Mascot
    for (mascotContext in mascotList) {
        key(mascotContext) { // Compose Key
            MascotWindow(
                mascotContext = mascotContext,
                mascotManager = mascotManager,
                workArea = workArea,
                allMascotsProvider = { mascotList.toList() }, // Pass current list snapshot
                gravity = gravity,
                timeScale = timeScale,
                icon = trayIcon,
                onNewMascot = {
                     // Clicking "Add" shows selector
                     showSkinSelector = true
                },
                onRemoveMascot = { target ->
                    mascotList.remove(target)
                    // If last one removed, do we exit? Or show selector?
                    if (mascotList.isEmpty()) {
                        exitApplication()
                    }
                },
                onOpenSettings = {
                    showSettings = true
                }
            )
        }
    }
}
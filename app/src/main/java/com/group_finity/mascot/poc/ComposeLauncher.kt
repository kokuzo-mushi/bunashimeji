package com.group_finity.mascot.poc

import androidx.compose.runtime.*
import androidx.compose.ui.window.application
import com.group_finity.mascot.Mascot
import com.group_finity.mascot.behavior.Configuration
import com.group_finity.mascot.image.ImageCache
import com.group_finity.mascot.manager.MascotContext
import com.group_finity.mascot.manager.MascotManager
import com.group_finity.mascot.script.ScriptEngineManager
import com.group_finity.mascot.trigger.EventDispatcher
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext
import com.group_finity.mascot.type.NeoPoint
import com.group_finity.mascot.type.NeoRect
import com.group_finity.mascot.view.MascotView
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.HashMap

// --- Adapter for MascotView ---
class ComposeMascotAdapter(
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

    fun getCurrentImage(): BufferedImage? {
        val animation = mascot.animation
        val pose = animation?.pose ?: return null
        return if (mascot.isLookRight) {
            imageCache.getRightImage(pose.imageName)
        } else {
            imageCache.getImage(pose.imageName)
        }
    }
}

fun main() = application {
    // 1. Initialize Global Resources (Once)
    val config = remember { Configuration(Path.of("conf/actions.xml"), Path.of("conf/behaviors.xml")) }
    // Separate ImageCache for each skin path
    val imageCaches = remember { mutableMapOf<Path, ImageCache>() }
    
    val workArea = remember {
        val workAreaRect = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        NeoRect(workAreaRect.x, workAreaRect.y, workAreaRect.width, workAreaRect.height)
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
            val jsContext = ScriptEngineManager.getInstance().createMascotContext(contextVariables)
            mascot.jsContext = jsContext

            // Dispatcher & Adapter
            val evalContext = EvaluationContext(contextVariables)
            val dispatcher = EventDispatcher(evalContext, mascot)
            
            // Get or create ImageCache for this skin
            val cache = imageCaches.getOrPut(skinPath) { ImageCache(skinPath) }
            val adapter = ComposeMascotAdapter(mascot, cache)
            
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

    // 6. Render Windows for each Mascot
    for (mascotContext in mascotList) {
        key(mascotContext) { // Compose Key
            MascotWindow(
                mascotContext = mascotContext,
                mascotManager = mascotManager,
                workArea = workArea,
                allMascotsProvider = { mascotList.toList() }, // Pass current list snapshot
                gravity = gravity,
                timeScale = timeScale,
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

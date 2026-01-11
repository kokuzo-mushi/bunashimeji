package com.group_finity.mascot.poc

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.name

data class SkinInfo(
    val name: String,
    val path: Path,
    val preview: ImageBitmap?
)

@Composable
fun SkinSelectorScreen(
    imgDir: Path,
    onSkinSelected: (Path) -> Unit,
    onClose: () -> Unit
) {
    var skins by remember { mutableStateOf<List<SkinInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val loadedSkins = mutableListOf<SkinInfo>()
        val dir = imgDir.toFile()
        
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isDirectory }?.forEach { skinDir ->
                // Try to load Stay1.png or icon.png
                var previewFile = File(skinDir, "Stay1.png")
                if (!previewFile.exists()) {
                    previewFile = File(skinDir, "icon.png")
                }
                if (!previewFile.exists()) {
                    previewFile = File(skinDir, "shime1.png")
                }

                var bitmap: ImageBitmap? = null
                if (previewFile.exists()) {
                    try {
                        val buf = ImageIO.read(previewFile)
                        bitmap = buf.toComposeImageBitmap()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Only add if it looks like a valid skin (has images)
                // Or if directory has any pngs
                if (skinDir.listFiles()?.any { it.name.endsWith(".png") } == true) {
                    loadedSkins.add(SkinInfo(skinDir.name, skinDir.toPath(), bitmap))
                }
            }
        }
        skins = loadedSkins
    }

    Window(
        onCloseRequest = onClose,
        title = "Select Skin",
        state = rememberWindowState(width = 600.dp, height = 500.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Select a Skin", style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(skins) { skin ->
                    SkinItem(skin) {
                         onSkinSelected(skin.path)
                    }
                }
            }
        }
    }
}

@Composable
fun SkinItem(skin: SkinInfo, onClick: () -> Unit) {
    Card(
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                if (skin.preview != null) {
                    Image(
                        bitmap = skin.preview,
                        contentDescription = skin.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No Image")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(skin.name, style = MaterialTheme.typography.subtitle1)
        }
    }
}

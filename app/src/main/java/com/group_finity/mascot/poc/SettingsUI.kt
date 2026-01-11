package com.group_finity.mascot.poc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.group_finity.mascot.behavior.Behavior

@Composable
fun SettingsScreen(
    behaviors: List<Behavior>,
    gravity: MutableState<Float>,
    timeScale: MutableState<Float>,
    onClose: () -> Unit
) {
    Window(
        onCloseRequest = onClose,
        title = "Settings",
        state = rememberWindowState(width = 400.dp, height = 600.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text("Global Settings", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))

            // Gravity
            Text("Gravity: ${gravity.value.toInt()}")
            Slider(
                value = gravity.value,
                onValueChange = { gravity.value = it },
                valueRange = 0f..100f
            )

            // Time Scale (Speed)
            Text("Time Scale (Speed): ${"%.1f".format(timeScale.value)}")
            Slider(
                value = timeScale.value,
                onValueChange = { timeScale.value = it },
                valueRange = 0.1f..5.0f
            )
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Behaviors", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(behaviors) { behavior ->
                    BehaviorItem(behavior)
                }
            }
        }
    }
}

@Composable
fun BehaviorItem(behavior: Behavior) {
    // Wrap java fields in compose state for UI reactivity
    var enabled by remember(behavior) { mutableStateOf(behavior.isEnabled) }
    var frequency by remember(behavior) { mutableStateOf(behavior.frequency.toFloat()) }

    Card(
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        behavior.isEnabled = it
                    }
                )
                Text(
                    text = behavior.name,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Freq: ${frequency.toInt()}",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.width(60.dp)
                )
                Slider(
                    value = frequency,
                    onValueChange = { 
                        frequency = it
                        behavior.frequency = it.toInt()
                    },
                    valueRange = 0f..100f,
                    enabled = enabled
                )
            }
        }
    }
}

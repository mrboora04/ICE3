package com.mrboora.plate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.AppUpdates
import com.mrboora.plate.data.UpdateInfo
import com.mrboora.plate.ui.theme.Plate
import kotlinx.coroutines.launch

/** "Plate 1.15 is available" with what's new, downloading and installing in place. */
@Composable
fun UpdateCard(info: UpdateInfo, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Plate.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    PlateCard(modifier) {
        Eyebrow("Update available", color = c.accent)
        Text("Plate ${info.versionName} is ready", style = MaterialTheme.typography.titleLarge)
        if (info.notes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column {
                info.notes.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            }
        }
        Spacer(Modifier.height(12.dp))
        progress?.let {
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
            Text("Downloading… ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (progress == null) {
            Row {
                Button({
                    if (!AppUpdates.canInstall(context)) {
                        error = "Allow Plate to install updates, then tap Update again."
                        context.startActivity(AppUpdates.installSettings(context))
                        return@Button
                    }
                    error = null
                    progress = 0f
                    scope.launch {
                        runCatching { AppUpdates.download(context, info) { progress = it } }
                            .onSuccess { progress = null; AppUpdates.install(context, it) }
                            .onFailure { progress = null; error = "Download failed. Check your connection and try again." }
                    }
                }) { Text("Update") }
                TextButton(onLater) { Text("Later") }
            }
        }
    }
}

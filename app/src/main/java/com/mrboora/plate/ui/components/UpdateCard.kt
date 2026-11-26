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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.AppUpdates
import com.mrboora.plate.data.AppUpdates.InstallState
import com.mrboora.plate.data.UpdateInfo
import com.mrboora.plate.ui.theme.Plate

/**
 * "Plate 2.1 is ready": Update now downloads it inside Plate (with a %), then Android's own
 * "Install this update?" screen opens. Nothing is left in Downloads.
 */
@Composable
fun UpdateCard(info: UpdateInfo, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Plate.colors
    val context = LocalContext.current
    val state by AppUpdates.install.collectAsState()

    PlateCard(modifier) {
        Eyebrow("Update available", color = c.accent)
        Text("Plate ${info.versionName} is ready", style = MaterialTheme.typography.titleLarge)
        if (info.notes.isNotEmpty() && state == InstallState.None) {
            Spacer(Modifier.height(6.dp))
            Column {
                info.notes.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is InstallState.Downloading -> {
                if (s.progress != null) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (s.progress != null) "Downloading… ${(s.progress * 100).toInt()}%" else "Downloading…",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            InstallState.Installing -> {
                Text(
                    "Tap Update on Android's screen to finish. Your diary is kept.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                TextButton({ AppUpdates.reset(); AppUpdates.oneTap(context, info) }) { Text("Didn't see it? Try again") }
            }
            is InstallState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button({ AppUpdates.reset(); AppUpdates.oneTap(context, info) }) { Text("Try again") }
                    TextButton({ AppUpdates.reset(); onLater() }) { Text("Later") }
                }
            }
            InstallState.None -> Row {
                Button({ AppUpdates.oneTap(context, info) }) { Text("Update now") }
                TextButton(onLater) { Text("Later") }
            }
        }
    }
}

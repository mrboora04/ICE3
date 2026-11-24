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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.AppUpdates
import com.mrboora.plate.data.AppUpdates.DownloadState
import com.mrboora.plate.data.UpdateInfo
import com.mrboora.plate.ui.theme.Plate
import kotlinx.coroutines.delay

/**
 * "Plate 2.1 is ready": Update downloads it through Android's download manager (once), then
 * Install opens the finished download. The system notification can be used instead.
 */
@Composable
fun UpdateCard(info: UpdateInfo, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Plate.colors
    val context = LocalContext.current
    var state by remember { mutableStateOf(AppUpdates.state(context)) }

    // Follow the download while this card is on screen.
    LaunchedEffect(Unit) {
        while (true) {
            state = AppUpdates.state(context)
            delay(if (state is DownloadState.Running) 500 else 2000)
        }
    }

    PlateCard(modifier) {
        Eyebrow("Update available", color = c.accent)
        Text("Plate ${info.versionName} is ready", style = MaterialTheme.typography.titleLarge)
        if (info.notes.isNotEmpty() && state == DownloadState.None) {
            Spacer(Modifier.height(6.dp))
            Column {
                info.notes.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.muted) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is DownloadState.Running -> {
                if (s.progress != null) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    if (s.progress != null) "Downloading… ${(s.progress * 100).toInt()}%" else "Downloading…",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            DownloadState.Ready -> {
                Text(
                    "Downloaded. Tap the “Plate ${info.versionName} update” notification, or Install below, then Install again. Your diary is kept.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button({ AppUpdates.openDownloads(context) }) { Text("Install") }
                    TextButton({ AppUpdates.clearDownload(context); state = DownloadState.None }) { Text("Download again") }
                }
            }
            DownloadState.Failed -> {
                Text("The download didn’t finish. Check your connection and try again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button({ AppUpdates.startDownload(context, info); state = AppUpdates.state(context) }) { Text("Try again") }
                    TextButton(onLater) { Text("Later") }
                }
            }
            DownloadState.None -> Row {
                Button({ state = AppUpdates.oneTap(context, info) }) { Text("Update now") }
                TextButton(onLater) { Text("Later") }
            }
        }
    }
}

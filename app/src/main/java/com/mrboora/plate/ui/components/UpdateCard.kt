package com.mrboora.plate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.AppUpdates
import com.mrboora.plate.data.UpdateInfo
import com.mrboora.plate.ui.theme.Plate

/** "Plate 2.0 is available" with what's new; Get the update opens the download page. */
@Composable
fun UpdateCard(info: UpdateInfo, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Plate.colors
    val context = LocalContext.current
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
        Row {
            Button({ AppUpdates.openDownloadPage(context) }) { Text("Get the update") }
            TextButton(onLater) { Text("Later") }
        }
    }
}

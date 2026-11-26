package com.mrboora.plate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mrboora.plate.ui.PlateApp
import com.mrboora.plate.ui.theme.PlateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.mrboora.plate.data.MenuUpdates.schedule(this)
        takeUpdateRequest(intent)
        takeShortcut(intent)
        takeInstallStatus(intent)
        com.mrboora.plate.widget.Shortcuts.publish(this)
        setContent { PlateTheme { PlateApp() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        takeUpdateRequest(intent)
        takeShortcut(intent)
        takeInstallStatus(intent)
    }

    /** Android's installer answering: show its confirm screen, or why the update didn't install. */
    private fun takeInstallStatus(intent: android.content.Intent?) {
        if (intent?.action == com.mrboora.plate.data.AppUpdates.ACTION_STATUS) {
            com.mrboora.plate.data.AppUpdates.onStatus(this, intent)
            intent.action = android.content.Intent.ACTION_MAIN
        }
    }

    /** Opened from a long-press shortcut: the app acts on it once it's ready. */
    private fun takeShortcut(intent: android.content.Intent?) {
        val a = intent?.action ?: return
        if (a.startsWith("com.mrboora.plate.shortcut.")) {
            shortcut.value = a
            intent.action = android.content.Intent.ACTION_MAIN
        }
    }

    /** Opened from the update notification: the app starts the download as soon as it knows the version. */
    private fun takeUpdateRequest(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(com.mrboora.plate.data.AppUpdates.EXTRA_START_UPDATE, false) == true) {
            updateRequested.value = true
            intent.removeExtra(com.mrboora.plate.data.AppUpdates.EXTRA_START_UPDATE)
        }
    }

    companion object {
        val updateRequested = androidx.compose.runtime.mutableStateOf(false)
        val shortcut = androidx.compose.runtime.mutableStateOf<String?>(null)
    }
}

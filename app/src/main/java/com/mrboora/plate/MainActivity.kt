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
        setContent { PlateTheme { PlateApp() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        takeUpdateRequest(intent)
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
    }
}

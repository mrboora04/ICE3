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
        setContent { PlateTheme { PlateApp() } }
    }
}

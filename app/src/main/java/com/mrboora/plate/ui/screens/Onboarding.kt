package com.mrboora.plate.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.Profile
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.MorphBlob
import com.mrboora.plate.ui.theme.Plate

@Composable
fun OnboardingScreen(onDone: (Profile) -> Unit) {
    val c = Plate.colors
    Box(Modifier.fillMaxSize()) {
        MorphBlob(listOf(c.peach, c.pink, c.lilac), Modifier.size(260.dp).align(Alignment.TopEnd).offset(130.dp, (-80).dp).alpha(0.75f))
        MorphBlob(listOf(c.sky, c.skyDeep), Modifier.size(110.dp).align(Alignment.TopEnd).offset(45.dp, 120.dp).alpha(0.7f), periodMs = 12000, seed = 2f, reverse = true)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Eyebrow("Plate · calorie tracker")
            Spacer(Modifier.height(8.dp))
            Text("Care that keeps\nup with you", style = MaterialTheme.typography.displayMedium, modifier = Modifier.fillMaxWidth(0.85f))
            Spacer(Modifier.height(10.dp))
            Text(
                "A few details and we'll work out a daily calorie and macro target. Everything stays on your phone.",
                style = MaterialTheme.typography.bodyLarge, color = c.muted,
            )
            Spacer(Modifier.height(28.dp))
            ProfileForm(initial = null, saveLabel = "Start tracking", onSave = onDone)
            Spacer(Modifier.height(24.dp))
        }
    }
}

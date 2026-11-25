package com.mrboora.plate.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.mrboora.plate.data.Units

/** The user's units (kg or lb, cm or ft in, ml or fl oz), for every screen to show measures in. */
val LocalUnits = compositionLocalOf { Units.Prefs() }

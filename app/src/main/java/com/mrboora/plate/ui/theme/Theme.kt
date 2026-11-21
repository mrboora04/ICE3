package com.mrboora.plate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Clinical, but warm" from the Next Design Atlas: a soft blush ground, deep plum ink,
 * living peach-pink-lilac shapes, and exact numbers drawn as rings.
 */
@Immutable
data class PlateColors(
    val ground: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color,
    val peach: Color,
    val pink: Color,
    val lilac: Color,
    val sky: Color,
    val skyDeep: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,
    val good: Color,
    val over: Color,
    /** Day status pastels from Plate's own palette: mint = on target, peach = over, lilac-blue = under. */
    val dayOn: Color,
    val dayOver: Color,
    val dayUnder: Color,
    /** Text on those pastels. */
    val onDay: Color,
)

val LightPlate = PlateColors(
    ground = Color(0xFFF7EEEA), card = Color(0xFFFFF9F6), ink = Color(0xFF3A2230),
    muted = Color(0xFF6E4A5C), line = Color(0xFFEBDAD6), accent = Color(0xFFD2477A),
    onAccent = Color(0xFFFFFFFF), track = Color(0xFFEED6DD), peach = Color(0xFFFFC9B5),
    pink = Color(0xFFF39BB0), lilac = Color(0xFFC98BD8), sky = Color(0xFFD9E6FF),
    skyDeep = Color(0xFFA9B7F2), protein = Color(0xFFD2477A), carbs = Color(0xFFE88A3C),
    fat = Color(0xFF8A6BD6), water = Color(0xFF5C7CE0), good = Color(0xFF2F8A5B), over = Color(0xFFB3261E),
    dayOn = Color(0xFFB4E3C4), dayOver = Color(0xFFFFCFB3), dayUnder = Color(0xFFCAD3FB), onDay = Color(0xFF3A2230),
)

val DarkPlate = PlateColors(
    ground = Color(0xFF1B1317), card = Color(0xFF261B21), ink = Color(0xFFF6E7ED),
    muted = Color(0xFFC6A9B7), line = Color(0xFF3A2A32), accent = Color(0xFFFF7AA8),
    onAccent = Color(0xFF3A0F22), track = Color(0xFF3D2A34), peach = Color(0xFF8A4E40),
    pink = Color(0xFF9C4864), lilac = Color(0xFF6E4682), sky = Color(0xFF2C3557),
    skyDeep = Color(0xFF4A5794), protein = Color(0xFFFF7AA8), carbs = Color(0xFFFFB067),
    fat = Color(0xFFB9A2FF), water = Color(0xFF8FA8FF), good = Color(0xFF6FD29B), over = Color(0xFFFF8A80),
    dayOn = Color(0xFF9FD8B4), dayOver = Color(0xFFF7BD9E), dayUnder = Color(0xFFB4C0F2), onDay = Color(0xFF2A1A22),
)

val LocalPlateColors = staticCompositionLocalOf { LightPlate }

object Plate {
    val colors: PlateColors @Composable get() = LocalPlateColors.current
}

@Composable
fun PlateTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkPlate else LightPlate
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.lilac, background = c.ground,
            onBackground = c.ink, surface = c.ground, onSurface = c.ink, surfaceVariant = c.card,
            onSurfaceVariant = c.muted, outline = c.line, surfaceContainer = c.card,
            surfaceContainerHigh = c.card, surfaceContainerLow = c.card, error = c.over,
            primaryContainer = c.track, onPrimaryContainer = c.ink, secondaryContainer = c.track,
            onSecondaryContainer = c.ink, outlineVariant = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.lilac, background = c.ground,
            onBackground = c.ink, surface = c.ground, onSurface = c.ink, surfaceVariant = c.card,
            onSurfaceVariant = c.muted, outline = c.line, surfaceContainer = c.card,
            surfaceContainerHigh = c.card, surfaceContainerLow = c.card, error = c.over,
            primaryContainer = c.track, onPrimaryContainer = c.ink, secondaryContainer = c.track,
            onSecondaryContainer = c.ink, outlineVariant = c.line,
        )
    }
    CompositionLocalProvider(LocalPlateColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = PlateTypography) {
            // Text without an explicit colour uses the ink colour in both themes.
            CompositionLocalProvider(LocalContentColor provides c.ink, content = content)
        }
    }
}

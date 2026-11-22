package com.mrboora.plate.ui.components

import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PosterType

/**
 * A slow food-label ribbon: items in condensed capitals separated by dots, scrolling sideways.
 * Stays still when the phone has animations turned off. Screen readers get the plain list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Ticker(
    items: List<String>,
    modifier: Modifier = Modifier,
    background: Color = Plate.colors.dayOver,
    color: Color = Plate.colors.onDay,
) {
    val context = LocalContext.current
    val moving = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val line = items.joinToString(separator = "   •   ", postfix = "   •   ") { it.uppercase() }
    Box(
        modifier.fillMaxWidth().background(background)
            .semantics(mergeDescendants = true) { contentDescription = items.joinToString(", ") }
            .padding(vertical = 9.dp),
    ) {
        Text(
            if (moving) line.repeat(2) else line,
            style = PosterType.ticker,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = if (moving) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = if (moving) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    initialDelayMillis = 600,
                    repeatDelayMillis = 0,
                    velocity = 28.dp,
                )
            } else Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** "01", "02"… in the poster cut, for numbered lists. */
@Composable
fun IndexLabel(n: Int, modifier: Modifier = Modifier, color: Color = Plate.colors.accent) {
    Text("%02d".format(n), style = PosterType.index, color = color, modifier = modifier)
}

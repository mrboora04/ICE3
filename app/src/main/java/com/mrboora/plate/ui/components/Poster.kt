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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.MaterialTheme
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

/**
 * A menu-board line: name, a dotted leader, then the number set like a price.
 * "Poha ··········· 260". The dots run behind the text and are covered by [background],
 * so they fill exactly the gap whatever the lengths.
 */
@Composable
fun MenuLine(
    name: String,
    number: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    nameStyle: TextStyle = MaterialTheme.typography.titleSmall,
    numberStyle: TextStyle = PosterType.number,
    numberColor: Color = Plate.colors.ink,
    background: Color = Plate.colors.card,
    maxLines: Int = 1,
) {
    val dots = Plate.colors.muted.copy(alpha = 0.45f)
    Row(
        modifier.fillMaxWidth().drawBehind {
            val y = size.height - 6.dp.toPx()
            val step = 5.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawCircle(dots, radius = 1.1.dp.toPx(), center = Offset(x, y))
                x += step
            }
        },
    ) {
        // Name, number and unit share one baseline, like type on a printed menu.
        Text(
            name, style = nameStyle, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline().weight(1f).wrapContentWidth(Alignment.Start).background(background).padding(end = 6.dp),
        )
        Row(Modifier.alignByBaseline().background(background).padding(start = 6.dp)) {
            Text(number, style = numberStyle, color = numberColor, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
            if (unit.isNotEmpty()) {
                Text(
                    " $unit", style = PosterType.index, color = Plate.colors.muted, maxLines = 1, softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

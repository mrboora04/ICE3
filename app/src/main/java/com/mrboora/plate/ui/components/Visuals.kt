package com.mrboora.plate.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboora.plate.ui.theme.Plate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Tabular digits so numbers don't jitter as they change. */
const val TABULAR = "tnum"

/** A soft shape whose outline slowly breathes, like the blobs on the health plate. */
@Composable
fun MorphBlob(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    periodMs: Int = 9000,
    seed: Float = 0f,
    reverse: Boolean = false,
) {
    val t by rememberInfiniteTransition(label = "blob").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "blobT",
    )
    Canvas(modifier) {
        val time = if (reverse) -t else t
        val r0 = size.minDimension / 2f * 0.86f
        val c = center
        val path = Path()
        val n = 120
        for (i in 0..n) {
            val th = i / n.toFloat() * 2f * PI.toFloat()
            val r = r0 * (1f + 0.075f * sin(3f * th + time + seed) +
                0.05f * cos(2f * th - time + seed * 2f) + 0.025f * sin(5f * th + 2f * time))
            val x = c.x + r * cos(th)
            val y = c.y + r * sin(th)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path,
            Brush.radialGradient(colors, center = Offset(c.x - r0 * 0.4f, c.y - r0 * 0.45f), radius = r0 * 2.1f),
        )
    }
}

/** Ring progress (the conic ring from the design), animated on change. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    color: Color = Plate.colors.accent,
    track: Color = Plate.colors.track,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "ring")
    Box(
        modifier.drawBehind {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(s))
            if (p > 0.001f) drawArc(color, -90f, 360f * p, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun PlateCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    /** The card's fill: Plate's card colour, or a day's status colour. */
    color: Color = Plate.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Plate.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .border(1.dp, if (color == c.card) c.line else color, RoundedCornerShape(24.dp))
            .padding(padding),
        content = content,
    )
}

/** Small uppercase label, like the mono eyebrows in the design. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Plate.colors.muted) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

/** A gently pulsing status dot. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val a by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulseA",
    )
    Box(modifier.size(8.dp).alpha(a).clip(CircleShape).background(color))
}

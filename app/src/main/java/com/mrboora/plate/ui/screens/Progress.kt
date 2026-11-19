package com.mrboora.plate.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.DayTotal
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Targets
import com.mrboora.plate.data.WeightEntry
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Plate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val tab = TextStyle(fontFeatureSettings = TABULAR)

@Composable
fun ProgressScreen(
    profile: Profile,
    targets: Targets,
    weights: List<WeightEntry>,
    week: List<DayTotal>,
    contentPadding: PaddingValues,
    onLog: (Float) -> Unit,
    onDelete: (WeightEntry) -> Unit,
) {
    val c = Plate.colors
    var logging by remember { mutableStateOf(false) }
    val current = weights.lastOrNull()?.kg ?: profile.weightKg
    val start = weights.firstOrNull()?.kg ?: profile.weightKg
    val change = current - start

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Eyebrow("Progress")
                Text("Calm numbers", style = MaterialTheme.typography.displayMedium)
            }
        }
        item {
            PlateCard(Modifier.padding(horizontal = 16.dp)) {
                Eyebrow("Weight now")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(current.clean(), style = MaterialTheme.typography.displayLarge.merge(tab))
                    Text(" kg", style = MaterialTheme.typography.headlineSmall, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                val sign = if (change > 0) "+" else if (change < 0) "−" else "±"
                Text(
                    "$sign${abs(change).clean()} kg since you started · goal ${profile.goalWeightKg.clean()} kg",
                    style = MaterialTheme.typography.bodyMedium.merge(tab), color = c.muted,
                )
                Spacer(Modifier.height(14.dp))
                WeightChart(weights, profile.goalWeightKg, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(14.dp))
                Button({ logging = true }, Modifier.fillMaxWidth()) { Text("Log today's weight") }
            }
        }
        item {
            PlateCard(Modifier.padding(horizontal = 16.dp)) {
                Eyebrow("Last 7 days")
                val logged = week.filter { it.kcal > 0f }
                val avg = if (logged.isEmpty()) 0 else (logged.sumOf { it.kcal.toDouble() } / logged.size).roundToInt()
                Text("%,d kcal average".format(avg), style = MaterialTheme.typography.headlineSmall.merge(tab))
                Text("Target %,d kcal · days with food logged only".format(targets.kcal), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(14.dp))
                WeekBars(week, targets.kcal)
            }
        }
        if (weights.isNotEmpty()) {
            item { Eyebrow("Weigh-ins", Modifier.padding(horizontal = 20.dp)) }
            items(weights.reversed(), key = { it.day }) { w ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalDate.ofEpochDay(w.day).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${w.kg.clean()} kg", style = MaterialTheme.typography.titleMedium.merge(tab))
                        IconButton({ onDelete(w) }) { Icon(Icons.Rounded.Close, "Delete weigh-in", tint = c.muted) }
                    }
                    HorizontalDivider(color = c.line)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (logging) {
        var text by remember { mutableStateOf(current.clean()) }
        val kg = text.toNumber()?.takeIf { it in 30f..350f }
        AlertDialog(
            onDismissRequest = { logging = false },
            title = { Text("Today's weight") },
            text = {
                OutlinedTextField(
                    text, { text = it }, label = { Text("Weight (kg)") }, singleLine = true, isError = kg == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = { TextButton({ kg?.let(onLog); logging = false }, enabled = kg != null) { Text("Save") } },
            dismissButton = { TextButton({ logging = false }) { Text("Cancel") } },
        )
    }
}

/** A thin line that draws itself in, like the ledger chart on the fintech plate. */
@Composable
private fun WeightChart(weights: List<WeightEntry>, goal: Float, modifier: Modifier) {
    val c = Plate.colors
    val draw = remember { Animatable(0f) }
    LaunchedEffect(weights.size) { draw.snapTo(0f); draw.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
    if (weights.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Log your weight on two or more days to see a trend.", style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        }
        return
    }
    Canvas(modifier) {
        val values = weights.map { it.kg } + goal
        val lo = values.min() - 0.5f
        val hi = values.max() + 0.5f
        val d0 = weights.first().day
        val span = (weights.last().day - d0).coerceAtLeast(1)
        fun pt(w: WeightEntry) = Offset(
            (w.day - d0).toFloat() / span * size.width,
            size.height - (w.kg - lo) / (hi - lo) * size.height,
        )
        val gy = size.height - (goal - lo) / (hi - lo) * size.height
        drawLine(c.muted.copy(alpha = 0.5f), Offset(0f, gy), Offset(size.width, gy), 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        val path = Path().apply { weights.forEachIndexed { i, w -> pt(w).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * draw.value, partial, true)
        drawPath(partial, c.good, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        if (draw.value >= 1f) drawCircle(c.good, 5.dp.toPx(), pt(weights.last()))
    }
}

/** Seven bars, one per day; the dashed line is the target. */
@Composable
private fun WeekBars(week: List<DayTotal>, target: Int) {
    val c = Plate.colors
    val max = maxOf(target.toFloat() * 1.25f, week.maxOfOrNull { it.kcal } ?: 0f, 1f)
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEach { d ->
            val anim = remember { Animatable(0f) }
            LaunchedEffect(d.kcal) { anim.animateTo(d.kcal / max, tween(900, easing = FastOutSlowInEasing)) }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                        val ty = size.height * (1f - target / max)
                        drawLine(c.muted.copy(alpha = 0.45f), Offset(0f, ty), Offset(size.width, ty), 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    }
                    val color = when {
                        d.kcal <= 0f -> c.track
                        d.kcal > target * 1.05f -> c.carbs
                        else -> c.accent
                    }
                    Box(
                        Modifier.fillMaxWidth(0.72f).fillMaxHeight(anim.value.coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(color),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(LocalDate.ofEpochDay(d.day).format(DateTimeFormatter.ofPattern("EEEEE")), style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
    }
}

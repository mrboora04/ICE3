package com.mrboora.plate.ui.screens

import com.mrboora.plate.ui.components.LocalUnits
import com.mrboora.plate.data.weightValue
import com.mrboora.plate.data.kgFrom
import com.mrboora.plate.data.weight
import com.mrboora.plate.data.volume
import com.mrboora.plate.data.num1
import com.mrboora.plate.ui.components.MenuLine
import androidx.compose.ui.unit.sp
import com.mrboora.plate.ui.theme.PosterType
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.DayStatus
import com.mrboora.plate.data.DaySummary
import com.mrboora.plate.data.History
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Targets
import com.mrboora.plate.data.WeightEntry
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PlateColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val tab = TextStyle(fontFeatureSettings = TABULAR)

private fun statusColor(s: DayStatus, c: PlateColors): Color = when (s) {
    DayStatus.ON_TARGET -> c.dayOn
    DayStatus.OVER -> c.dayOver
    DayStatus.UNDER -> c.dayUnder
    DayStatus.NOT_LOGGED -> c.track
}

@Composable
fun ProgressScreen(
    profile: Profile,
    targets: Targets,
    weights: List<WeightEntry>,
    history: List<DaySummary>,
    contentPadding: PaddingValues,
    onLog: (Float) -> Unit,
    onDelete: (WeightEntry) -> Unit,
    onOpenDay: (Long) -> Unit = {},
    /** Selfie compare card; empty to leave it out. */
    selfies: @Composable () -> Unit = {},
    /** Opened from the "Log weight" shortcut: show the weight dialog straight away. */
    startLogging: Boolean = false,
) {
    val c = Plate.colors
    var logging by remember { mutableStateOf(startLogging) }
    var weightOpen by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(14) }
    val byDay = remember(history) { history.associateBy { it.day } }
    val streak = remember(history) { History.streak(history) }
    val current = weights.lastOrNull()?.kg ?: profile.weightKg
    val start = weights.firstOrNull()?.kg ?: profile.weightKg

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Eyebrow("Progress")
                if (streak > 0) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text("$streak", style = PosterType.hero.copy(fontSize = 96.sp, lineHeight = 84.sp), color = c.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (streak == 1) "DAY ON\nTARGET" else "DAYS ON\nTARGET",
                            style = PosterType.section, modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                } else {
                    Text("EVERY DAY COUNTS", style = PosterType.title)
                }
                Text(
                    if (streak > 0) "Your current run. Days you didn’t log don’t break it."
                    else "Log a day within your target to start a streak.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
            }
        }
        item { MonthCalendar(byDay, onOpenDay, Modifier.padding(horizontal = 16.dp)) }
        item { selfies() }
        item {
            WeightCard(
                current, start, profile.goalWeightKg, weights, weightOpen,
                onToggle = { weightOpen = !weightOpen }, onLog = { logging = true }, onDelete = onDelete,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    "Your days will show up here as you log food: what you ate, and whether you hit your target.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else {
            item { Eyebrow("Your days", Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            items(history.take(shown), key = { it.day }) { d ->
                DayCard(d, targets.kcal, { onOpenDay(d.day) }, Modifier.padding(horizontal = 16.dp))
            }
            if (history.size > shown) {
                item {
                    TextButton({ shown += 14 }, Modifier.padding(horizontal = 12.dp)) { Text("Show earlier days") }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (logging) {
        val u = LocalUnits.current
        var text by remember { mutableStateOf(u.weightValue(current).clean()) }
        val kg = text.toNumber()?.let(u::kgFrom)?.takeIf { it in 30f..350f }
        AlertDialog(
            onDismissRequest = { logging = false },
            title = { Text("Today's weight") },
            text = {
                OutlinedTextField(
                    text, { text = it }, label = { Text("Weight (${u.weight.label})") }, singleLine = true, isError = kg == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = { TextButton({ kg?.let(onLog); logging = false }, enabled = kg != null) { Text("Save") } },
            dismissButton = { TextButton({ logging = false }) { Text("Cancel") } },
        )
    }
}

/** This month as dots: green on target, orange over, blue under, grey not logged. */
@Composable
private fun MonthCalendar(byDay: Map<Long, DaySummary>, onOpenDay: (Long) -> Unit, modifier: Modifier) {
    val c = Plate.colors
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val ym = YearMonth.parse(month)
    val first = ym.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sunday first
    val days = ym.lengthOfMonth()
    val inMonth = (1..days).mapNotNull { byDay[ym.atDay(it).toEpochDay()] }
    val onTarget = inMonth.count { it.status == DayStatus.ON_TARGET }

    PlateCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (inMonth.isEmpty()) "Nothing logged yet" else "$onTarget of ${inMonth.size} logged days on target",
                    style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted,
                )
            }
            IconButton({ month = ym.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
            IconButton({ month = ym.plusMonths(1).toString() }, enabled = ym < YearMonth.from(today)) {
                Icon(Icons.Rounded.ChevronRight, "Next month")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
        val cells = lead + days
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.padding(top = 6.dp)) {
                for (col in 0 until 7) {
                    val n = row * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val date = ym.atDay(n)
                            val sum = byDay[date.toEpochDay()]
                            val status = sum?.status ?: DayStatus.NOT_LOGGED
                            val future = date.isAfter(today)
                            val label = when (status) {
                                DayStatus.ON_TARGET -> "on target"; DayStatus.OVER -> "over"
                                DayStatus.UNDER -> "under"; DayStatus.NOT_LOGGED -> "not logged"
                            }
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (future) Color.Transparent else statusColor(status, c).copy(alpha = if (sum == null) 0.35f else 1f))
                                    .let { if (date == today) it.border(2.dp, c.ink, CircleShape) else it }
                                    .let { if (!future) it.clickable { onOpenDay(date.toEpochDay()) } else it }
                                    .semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("MMMM d"))}, $label" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$n", style = MaterialTheme.typography.labelMedium.merge(tab),
                                    color = when {
                                        future -> c.muted.copy(alpha = 0.5f)
                                        sum == null -> c.muted
                                        else -> c.onDay
                                    },
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(DayStatus.ON_TARGET to "On target", DayStatus.OVER to "Over", DayStatus.UNDER to "Under").forEach { (s, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(s, c)))
                    Text(" $l", style = MaterialTheme.typography.labelSmall, color = c.muted)
                }
            }
        }
    }
}

/** Weight now and the change, with the trend line and weigh-ins one tap away. */
@Composable
private fun WeightCard(
    current: Float, start: Float, goal: Float, weights: List<WeightEntry>, open: Boolean,
    onToggle: () -> Unit, onLog: () -> Unit, onDelete: (WeightEntry) -> Unit, modifier: Modifier,
) {
    val c = Plate.colors
    val u = LocalUnits.current
    val change = current - start
    PlateCard(modifier.animateContentSize()) {
        Row(Modifier.clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Weight")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(num1(u.weightValue(current)), style = PosterType.number.copy(fontSize = 34.sp, lineHeight = 32.sp))
                    Text(" ${u.weight.label.uppercase()}", style = PosterType.index, color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
                }
                val sign = if (change > 0) "+" else if (change < 0) "−" else "±"
                Text("$sign${u.weight(abs(change))} since you started · goal ${u.weight(goal)}", style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                if (open) "Hide weight trend" else "Show weight trend",
                tint = c.muted,
                modifier = Modifier.padding(start = 8.dp).let { if (open) it.then(Modifier) else it },
            )
        }
        Spacer(Modifier.height(10.dp))
        WeightChart(weights, goal, Modifier.fillMaxWidth().height(if (open) 150.dp else 56.dp))
        Spacer(Modifier.height(10.dp))
        Button(onLog, Modifier.fillMaxWidth()) { Text("Log today’s weight") }
        if (open && weights.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            weights.reversed().take(30).forEach { w ->
                HorizontalDivider(color = c.line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDate.ofEpochDay(w.day).format(DateTimeFormatter.ofPattern("EEE, MMM d")), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(u.weight(w.kg), style = MaterialTheme.typography.titleSmall.merge(tab))
                    IconButton({ onDelete(w) }) { Icon(Icons.Rounded.Close, "Delete weigh-in", tint = c.muted) }
                }
            }
        }
    }
}

/** One day: eaten vs target, hit or missed, what was eaten, and the rest of the day's numbers. */
@Composable
private fun DayCard(d: DaySummary, target: Int, onOpen: () -> Unit, modifier: Modifier) {
    val c = Plate.colors
    val u = LocalUnits.current
    val color = statusColor(d.status, c)
    val diff = (d.kcal - target).roundToInt()
    val badge = when (d.status) {
        DayStatus.ON_TARGET -> "✓ On target"
        DayStatus.OVER -> "▲ Over by %,d".format(diff)
        DayStatus.UNDER -> "▼ Under by %,d".format(-diff)
        DayStatus.NOT_LOGGED -> "Not logged"
    }
    val date = LocalDate.ofEpochDay(d.day)
    val title = when (d.day) {
        LocalDate.now().toEpochDay() -> "Today"
        LocalDate.now().toEpochDay() - 1 -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    PlateCard(modifier.clickable(onClick = onOpen), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(
                badge, style = MaterialTheme.typography.labelLarge, color = c.onDay,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(d.kcal.roundToInt()), style = PosterType.number.copy(fontSize = 34.sp, lineHeight = 32.sp))
            Text(" / %,d KCAL".format(target), style = PosterType.index, color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            if (d.status == DayStatus.ON_TARGET && diff < 0) {
                Text("%,d left".format(-diff), style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        // Bar: filled to eaten / target, capped at 125% with the target marked.
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.track)) {
            Box(Modifier.fillMaxWidth((d.kcal / (target * 1.25f)).coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(color))
            Box(Modifier.fillMaxWidth(0.8f).height(10.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(10.dp).background(c.ink.copy(alpha = 0.6f)))
            }
        }
        Spacer(Modifier.height(10.dp))
        val bits = buildList {
            add("P ${d.protein.roundToInt()} g · C ${d.carbs.roundToInt()} g · F ${d.fat.roundToInt()} g")
            if (d.waterMl > 0) add("💧 ${u.volume(d.waterMl)}")
            d.weightKg?.let { add("⚖ ${u.weight(it)}") }
        }
        Text(bits.joinToString("   "), style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted)
        if (d.foods.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val names = d.foods.take(4).joinToString(" · ") { it.first }
            val more = d.foods.size - 4
            Text(
                if (more > 0) "$names  +$more more" else names,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (d.status == DayStatus.OVER) {
                val (name, kcal) = d.foods.first()
                Text(
                    "Biggest: $name (%,d kcal)".format(kcal.roundToInt()),
                    style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

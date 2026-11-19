package com.mrboora.plate.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.FoodEntry
import com.mrboora.plate.data.Meal
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Targets
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.MorphBlob
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.ProgressRing
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Plate
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val tab = TextStyle(fontFeatureSettings = TABULAR)

fun dayLabel(day: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (day) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

@Composable
fun TodayScreen(
    profile: Profile,
    targets: Targets,
    day: Long,
    entries: List<FoodEntry>,
    waterMl: Int,
    contentPadding: PaddingValues,
    onShiftDay: (Long) -> Unit,
    onToday: () -> Unit,
    onAdd: (Meal) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onWater: (Int) -> Unit,
) {
    val c = Plate.colors
    val eaten = entries.sumOf { it.kcal.toDouble() }.toFloat()
    val isToday = day == LocalDate.now().toEpochDay()

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onShiftDay(-1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
                TextButton(onClick = onToday, enabled = !isToday) {
                    Text(dayLabel(day), style = MaterialTheme.typography.titleMedium, color = c.ink)
                }
                IconButton({ onShiftDay(1) }, enabled = !isToday) { Icon(Icons.Rounded.ChevronRight, "Next day") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(greeting() + ",", style = MaterialTheme.typography.headlineMedium, color = c.muted)
                Text(profile.name, style = MaterialTheme.typography.displayMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item { Hero(eaten, targets, entries, Modifier.padding(horizontal = 16.dp)) }
        item { WaterCard(waterMl, profile.waterGoalMl, onWater, Modifier.padding(horizontal = 16.dp)) }
        Meal.entries.forEach { meal ->
            item(key = meal.name) {
                MealCard(meal, entries.filter { it.meal == meal }, { onAdd(meal) }, onDelete, Modifier.padding(horizontal = 16.dp))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun Hero(eaten: Float, t: Targets, entries: List<FoodEntry>, modifier: Modifier) {
    val c = Plate.colors
    val left = t.kcal - eaten.roundToInt()
    val over = left < 0
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card)) {
        MorphBlob(listOf(c.peach, c.pink, c.lilac), Modifier.size(190.dp).align(Alignment.TopEnd).offset(95.dp, (-70).dp).alpha(0.8f))
        MorphBlob(listOf(c.sky, c.skyDeep), Modifier.size(90.dp).align(Alignment.CenterEnd).offset(50.dp, (-10).dp).alpha(0.7f), periodMs = 12000, seed = 1.7f, reverse = true)
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = if (t.kcal > 0) eaten / t.kcal else 0f,
                    modifier = Modifier.size(156.dp),
                    stroke = 16.dp,
                    color = if (over) c.over else c.accent,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%,d".format(abs(left)), style = MaterialTheme.typography.displaySmall.merge(tab))
                        Text(if (over) "kcal over" else "kcal left", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Eaten", "%,d".format(eaten.roundToInt()))
                    Stat("Goal", "%,d".format(t.kcal))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Macro("Protein", entries.sumOf { it.protein.toDouble() }.toFloat(), t.proteinG, c.protein, Modifier.weight(1f))
                Macro("Carbs", entries.sumOf { it.carbs.toDouble() }.toFloat(), t.carbsG, c.carbs, Modifier.weight(1f))
                Macro("Fat", entries.sumOf { it.fat.toDouble() }.toFloat(), t.fatG, c.fat, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Eyebrow(label)
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(tab))
    }
}

@Composable
private fun Macro(label: String, g: Float, goal: Int, color: Color, modifier: Modifier) {
    val c = Plate.colors
    Column(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.track)) {
            Box(
                Modifier.fillMaxWidth(if (goal > 0) (g / goal).coerceIn(0f, 1f) else 0f).height(8.dp)
                    .clip(CircleShape).background(color).animateContentSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${g.roundToInt()} / $goal g", style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted)
    }
}

@Composable
private fun WaterCard(ml: Int, goal: Int, onWater: (Int) -> Unit, modifier: Modifier) {
    val c = Plate.colors
    val cups = (goal / 250).coerceIn(1, 16)
    val filled = ml / 250
    PlateCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Water")
                Text("%,d / %,d ml".format(ml, goal), style = MaterialTheme.typography.titleLarge.merge(tab))
            }
            FilledTonalIconButton({ onWater(-250) }, enabled = ml > 0) { Icon(Icons.Rounded.Remove, "Remove 250 ml") }
            FilledTonalIconButton({ onWater(250) }) { Icon(Icons.Rounded.Add, "Add 250 ml") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(cups) { i ->
                Icon(
                    Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    tint = if (i < filled) c.water else c.track,
                    modifier = Modifier.weight(1f).height(26.dp).clickable { onWater((i + 1) * 250 - ml) },
                )
            }
        }
    }
}

@Composable
private fun MealCard(meal: Meal, items: List<FoodEntry>, onAdd: () -> Unit, onDelete: (FoodEntry) -> Unit, modifier: Modifier) {
    val c = Plate.colors
    PlateCard(modifier.animateContentSize(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(meal.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (items.isEmpty()) "Nothing yet" else "%,d kcal".format(items.sumOf { it.kcal.toDouble() }.roundToInt()),
                    style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted,
                )
            }
            FilledTonalIconButton(onAdd) { Icon(Icons.Rounded.Add, "Add food to ${meal.label}") }
        }
        items.forEach { e ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val brand = if (e.brand.isNotBlank()) "${e.brand} · " else ""
                    Text(
                        "$brand${e.grams.roundToInt()} g · P ${e.protein.roundToInt()} · C ${e.carbs.roundToInt()} · F ${e.fat.roundToInt()}",
                        style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("${e.kcal.roundToInt()}", style = MaterialTheme.typography.titleMedium.merge(tab))
                IconButton({ onDelete(e) }) { Icon(Icons.Rounded.Close, "Remove ${e.name}", tint = c.muted) }
            }
        }
    }
}

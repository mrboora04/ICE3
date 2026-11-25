package com.mrboora.plate.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboora.plate.data.FoodEntry
import com.mrboora.plate.data.Forecast
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.WeightEntry
import com.mrboora.plate.data.Workout
import com.mrboora.plate.data.Workouts
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.MenuLine
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PosterType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private fun kg(v: Float) = "%.1f".format(v)
private fun signed(v: Float, fmt: String) = (if (v > 0) "+" else if (v < 0) "−" else "±") + fmt.format(abs(v))
private fun date(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("MMM d"))

/**
 * "If every day were like today": the forecast from today's real meals, water and workouts.
 * Nothing is averaged or guessed: burn is resting energy × 1.2 for everyday life plus the
 * workouts you log here.
 */
@Composable
fun ForecastCard(
    profile: Profile,
    currentKg: Float,
    meals: List<FoodEntry>,
    waterMl: Int,
    workouts: List<Workout>,
    weights: List<WeightEntry>,
    today: Long,
    onAddWorkout: (kind: String, minutes: Int, kcal: Int) -> Unit,
    onDeleteWorkout: (Workout) -> Unit,
    modifier: Modifier = Modifier,
    /** Every logged day (newest first): the forecast uses their average, not just today. */
    history: List<com.mrboora.plate.data.DaySummary> = emptyList(),
    /** Workout kcal per day, for the same average. */
    workoutsByDay: Map<Long, Int> = emptyMap(),
) {
    val c = Plate.colors
    var adding by remember { mutableStateOf(false) }
    if (adding) WorkoutDialog(currentKg, onDismiss = { adding = false }) { k, m, kcal -> onAddWorkout(k, m, kcal); adding = false }

    val eatenToday = meals.sumOf { it.kcal.toDouble() }.toFloat()
    val workoutToday = workouts.sumOf { it.kcal }.toFloat()
    // Based on the average of every finished logged day; today only until there is one.
    val b = remember(history, workoutsByDay, today, eatenToday, workoutToday) {
        Forecast.basis(history, workoutsByDay, today, eatenToday, workoutToday)
    }
    val eaten = b.intake
    val workoutKcal = b.workout
    val f = remember(profile, currentKg, eaten, workoutKcal, today) { Forecast.today(profile, currentKg, eaten, workoutKcal, today) }
    val daily = Forecast.dailyLife(profile, currentKg)
    val scale = remember(weights, today) { Forecast.scaleTrend(weights, today) }
    val num = PosterType.number.copy(fontSize = 22.sp, lineHeight = 22.sp)
    val label = MaterialTheme.typography.bodyMedium

    PlateCard(modifier.animateContentSize()) {
        Text("FORECAST", style = PosterType.section)
        Text(
            if (b.todayOnly) "From today so far · your average takes over after your first full day"
            else "Your average of ${b.days} logged day${if (b.days == 1) "" else "s"} · today counts once it's over",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        Spacer(Modifier.height(12.dp))

        if ((b.todayOnly && meals.isEmpty())) {
            Text("Log today's meals and the forecast appears here.", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(kg(f.in12Weeks), style = PosterType.hero.copy(fontSize = 56.sp, lineHeight = 52.sp), color = c.accent)
                Text(" KG", style = PosterType.section, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "in 12 weeks, ${date(today + 84)}\n${kg(f.in4Weeks)} kg in 4 weeks",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Curve(f, profile.goalWeightKg, Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(14.dp))
        }

        Eyebrow("Today, step by step")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuLine(
                if (b.todayOnly) "1. Meals today (${meals.size} logged)" else "1. Eaten, a day on average",
                "%,d".format(eaten.roundToInt()), unit = "KCAL", nameStyle = label, numberStyle = num,
            )
            if (!b.todayOnly) {
                Text(
                    "Today so far: %,d kcal from ${meals.size} food${if (meals.size == 1) "" else "s"}".format(eatenToday.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(start = 16.dp),
                )
            }
            MenuLine("2. Water, no calories", "%,d".format(waterMl), unit = "ML", nameStyle = label, numberStyle = num, numberColor = c.water)
            MenuLine("3. Resting + daily life", "%,d".format(daily.roundToInt()), unit = "KCAL", nameStyle = label, numberStyle = num)
            MenuLine(
                if (b.todayOnly) "4. Workouts" else "4. Workouts, a day on average",
                "+%,d".format(workoutKcal.roundToInt()), unit = "KCAL", nameStyle = label, numberStyle = num,
            )
            workouts.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    Text("${w.kind} · ${w.minutes} min · ${w.kcal} kcal", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
                    IconButton({ onDeleteWorkout(w) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Remove ${w.kind}", Modifier.size(16.dp), tint = c.muted) }
                }
            }
            OutlinedButton({ adding = true }, Modifier.padding(start = 16.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add workout")
            }
            if (!(b.todayOnly && meals.isEmpty())) {
                MenuLine("5. Eaten − burned", signed(f.balance, "%,.0f"), unit = "KCAL", nameStyle = label, numberStyle = num,
                    numberColor = if (f.balance < 0) c.good else if (f.balance > 0) c.over else c.ink)
                MenuLine("6. ÷ 7,700 kcal per kg", signed(f.kgPerWeek, "%.2f"), unit = "KG/WK", nameStyle = label, numberStyle = num)
                if (profile.goal != Goal.MAINTAIN) {
                    MenuLine(
                        "7. Goal ${kg(profile.goalWeightKg)} kg", f.goalDay?.let { date(it).uppercase() } ?: "NOT AT THIS RATE",
                        nameStyle = label, numberStyle = num, numberColor = if (f.goalDay != null) c.accent else c.muted,
                    )
                }
                val holdKg = if (profile.goal == Goal.MAINTAIN) currentKg else profile.goalWeightKg
                MenuLine(
                    "${if (profile.goal != Goal.MAINTAIN) 8 else 7}. To stay at ${kg(holdKg)} kg, eat",
                    "%,d".format(((Forecast.dailyLife(profile, holdKg) + workoutKcal) / 10f).roundToInt() * 10),
                    unit = "KCAL", nameStyle = label, numberStyle = num,
                )
            }
        }
        Text(
            "Burn is your resting energy × 1.2 for everyday moving, plus the workouts you add, so nothing about " +
                "exercise is guessed. Today isn't over: it updates as you log. The forecast slows over time because " +
                "a lighter body burns a little less.",
            style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
        )
        if (!(b.todayOnly && meals.isEmpty()) && Nutrition.bmi(f.in12Weeks, profile.heightCm) < 18.5f) {
            Text(
                "Every day like today would take you below a healthy weight (BMI 18.5) within 12 weeks. Eat more, " +
                    "and talk to a doctor before eating this little.",
                style = MaterialTheme.typography.bodySmall, color = c.over, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (scale != null) {
            Text(
                "Your scale says ${signed(scale, "%.2f")} kg a week over your recent weigh-ins.",
                style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Pick a workout and minutes; or type the calories from a watch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutDialog(weightKg: Float, onDismiss: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var kind by remember { mutableStateOf(Workouts.kinds.first()) }
    var minutes by remember { mutableStateOf("30") }
    var fromWatch by remember { mutableStateOf(false) }
    var watchKcal by remember { mutableStateOf("") }
    val m = minutes.toIntOrNull()?.takeIf { it in 1..600 }
    val kcal = if (fromWatch) watchKcal.toIntOrNull()?.takeIf { it in 1..5000 } else m?.let { Workouts.kcal(kind, it, weightKg) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a workout") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Workouts.kinds.forEach { k -> FilterChip(k == kind, { kind = k }, label = { Text(k.name) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    minutes, { minutes = it }, label = { Text("Minutes") }, singleLine = true, isError = m == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("I have the calories from my watch", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(fromWatch, { fromWatch = it })
                }
                if (fromWatch) {
                    OutlinedTextField(
                        watchKcal, { watchKcal = it }, label = { Text("Active kcal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kcal != null) {
                    Text(
                        if (fromWatch) "$kcal kcal from your watch" else "≈ $kcal kcal extra burned (${kind.name}, $m min at ${kg(weightKg)} kg)",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton({ if (m != null && kcal != null) onSave(kind.name, m, kcal) }, enabled = m != null && kcal != null) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** 12 weeks of forecast as a line, with the goal weight dashed. */
@Composable
private fun Curve(f: Forecast.Result, goalKg: Float, modifier: Modifier) {
    val c = Plate.colors
    val pts = f.curve
    val lo = minOf(pts.min(), goalKg) - 0.5f
    val hi = maxOf(pts.max(), goalKg) + 0.5f
    Canvas(modifier) {
        fun y(v: Float) = size.height * (1f - (v - lo) / (hi - lo))
        val gy = y(goalKg)
        drawLine(c.muted.copy(alpha = 0.5f), Offset(0f, gy), Offset(size.width, gy), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        val path = Path()
        pts.forEachIndexed { i, v ->
            val x = size.width * i / (pts.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(c.accent, 5.dp.toPx(), Offset(size.width, y(pts.last())))
        drawCircle(c.ink, 4.dp.toPx(), Offset(0f, y(pts.first())))
    }
}

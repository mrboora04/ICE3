package com.mrboora.plate.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboora.plate.data.DaySummary
import com.mrboora.plate.data.Forecast
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Targets
import com.mrboora.plate.data.WeightEntry
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
 * "Where is my weight heading?" Worked out offline from the diary, with every step shown,
 * and a what-if slider for trying another daily intake.
 */
@Composable
fun ForecastCard(
    profile: Profile,
    targets: Targets,
    currentKg: Float,
    history: List<DaySummary>,
    weights: List<WeightEntry>,
    today: Long,
    modifier: Modifier = Modifier,
) {
    val c = Plate.colors
    val avg = remember(history, today) { Forecast.averageIntake(history, today) }
    val base = avg?.first ?: targets.kcal.toFloat()
    var whatIf by remember(base) { mutableStateOf<Float?>(null) }
    val intake = whatIf ?: base
    val f = remember(profile, currentKg, intake, today) { Forecast.run(profile, currentKg, intake, today) }
    val scale = remember(weights, today) { Forecast.scaleTrend(weights, today) }

    PlateCard(modifier.animateContentSize()) {
        Text("FORECAST", style = PosterType.section)
        Text(
            when {
                whatIf != null -> "What if you ate %,d kcal a day".format(intake.roundToInt())
                avg != null -> "From your last ${avg.second} logged days · worked out on your phone"
                else -> "Using your target until you've logged ${Forecast.MIN_DAYS} full days"
            },
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        Spacer(Modifier.height(12.dp))

        // The answer first, big.
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
        Curve(f, profile.goalWeightKg, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(14.dp))

        // The reasoning, one line per step.
        Eyebrow("How it's worked out")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val num = PosterType.number.copy(fontSize = 22.sp, lineHeight = 22.sp)
            val label = MaterialTheme.typography.bodyMedium
            MenuLine(
                if (whatIf != null) "1. You'd eat" else if (avg != null) "1. You eat (average)" else "1. Your target",
                "%,d".format(intake.roundToInt()), unit = "KCAL", nameStyle = label, numberStyle = num,
            )
            MenuLine("2. You burn at ${kg(currentKg)} kg", "%,d".format(f.burn.roundToInt()), unit = "KCAL", nameStyle = label, numberStyle = num)
            MenuLine("3. Difference per day", signed(f.balance, "%,.0f"), unit = "KCAL", nameStyle = label, numberStyle = num,
                numberColor = if (f.balance < 0) c.good else if (f.balance > 0) c.over else c.ink)
            MenuLine("4. ÷ 7,700 kcal per kg", signed(f.kgPerWeek, "%.2f"), unit = "KG/WK", nameStyle = label, numberStyle = num)
            if (profile.goal != Goal.MAINTAIN) {
                MenuLine(
                    "5. Goal ${kg(profile.goalWeightKg)} kg", f.goalDay?.let { date(it).uppercase() } ?: "NOT AT THIS RATE",
                    nameStyle = label, numberStyle = num, numberColor = if (f.goalDay != null) c.accent else c.muted,
                )
            }
            // Then what keeps you there: your burn at the goal weight.
            val holdKg = if (profile.goal == Goal.MAINTAIN) currentKg else profile.goalWeightKg
            MenuLine(
                "${if (profile.goal != Goal.MAINTAIN) 6 else 5}. To stay at ${kg(holdKg)} kg, eat",
                "%,d".format((Forecast.burn(profile, holdKg) / 10f).roundToInt() * 10), unit = "KCAL", nameStyle = label, numberStyle = num,
            )
        }
        Text(
            "You burn a little less as you get lighter (more as you get heavier), so the forecast slows down " +
                "over time instead of following a straight line.",
            style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
        )
        // Never cheer on a forecast that heads below a healthy weight.
        val bmi12 = com.mrboora.plate.data.Nutrition.bmi(f.in12Weeks, profile.heightCm)
        if (bmi12 < 18.5f) {
            Text(
                "At this intake you'd go below a healthy weight (BMI 18.5) within 12 weeks. Eat more than this, " +
                    "and talk to a doctor before eating this little.",
                style = MaterialTheme.typography.bodySmall, color = c.over, modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Scale check: compare with real weigh-ins.
        if (scale != null && whatIf == null) {
            Spacer(Modifier.height(10.dp))
            val gap = scale - f.kgPerWeek
            Text(
                "Your scale: ${signed(scale, "%.2f")} kg a week lately. " + when {
                    avg == null -> "Log a few full days to compare."
                    abs(gap) < 0.25f -> "That matches your log. Nice, accurate logging."
                    gap > 0 -> "That's slower than your log predicts; some snacks, drinks or oil may be missing from the log, or portions are bigger than entered."
                    else -> "That's faster than your log predicts; early on this is often water, or your activity is higher than set."
                },
                style = MaterialTheme.typography.bodySmall, color = c.ink,
            )
        }

        // What if.
        Spacer(Modifier.height(14.dp))
        Eyebrow("What if I eat…")
        Slider(
            value = intake, onValueChange = { whatIf = (it / 50f).roundToInt() * 50f },
            valueRange = 1000f..4500f,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.track),
            modifier = Modifier.semantics { contentDescription = "Daily calories for the forecast" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(whatIf == null, { whatIf = null }, label = { Text(if (avg != null) "My average" else "My target") })
            FilterChip(whatIf == targets.kcal.toFloat(), { whatIf = targets.kcal.toFloat() }, label = { Text("Target %,d".format(targets.kcal)) })
        }
    }
}

/** 12 weeks of forecast as a soft line, with the goal weight dashed. */
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

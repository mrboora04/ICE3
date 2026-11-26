package com.mrboora.plate.ui.screens

import androidx.compose.material3.SegmentedButton
import com.mrboora.plate.ui.components.LocalUnits
import com.mrboora.plate.data.Units
import com.mrboora.plate.data.weight
import com.mrboora.plate.data.weightRaw
import com.mrboora.plate.data.height
import com.mrboora.plate.data.volume
import com.mrboora.plate.ui.theme.PosterType
import com.mrboora.plate.ui.components.MenuLine
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Targets
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.MorphBlob
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.ProgressRing
import com.mrboora.plate.ui.components.PulseDot
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Display
import com.mrboora.plate.ui.theme.Plate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val tab = TextStyle(fontFeatureSettings = TABULAR)

/**
 * The profile combines two plates from the Next Design Atlas:
 * "A name that responds" (letters that thicken, a hand-drawn stroke) and
 * "Clinical, but warm" (living shapes and exact numbers in rings).
 */
@Composable
fun ProfileScreen(
    profile: Profile,
    targets: Targets,
    currentKg: Float,
    startKg: Float,
    contentPadding: PaddingValues,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    /** Extra settings cards (selfie of the day), shown above sharing and reset. */
    extra: @Composable () -> Unit = {},
    /** Today's forecast card, under the goal ring. */
    forecast: @Composable () -> Unit = {},
    /** Opens the file picker to delete old Plate….apk installers from Downloads. */
    onCleanInstallers: (() -> Unit)? = null,
    /** Change kg/lb, cm/ft in, ml/fl oz. */
    onUnits: (Units.Prefs) -> Unit = {},
) {
    val c = Plate.colors
    val u = LocalUnits.current
    var confirmReset by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val bmi = Nutrition.bmi(currentKg, profile.heightCm)
    val weeks = Nutrition.weeksToGoal(profile, currentKg)

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                MorphBlob(listOf(c.peach, c.pink, c.lilac), Modifier.size(320.dp).align(Alignment.TopEnd).offset(90.dp, (-40).dp))
                MorphBlob(listOf(c.sky, c.skyDeep), Modifier.size(150.dp).align(Alignment.BottomEnd).offset((-30).dp, 0.dp), periodMs = 12000, seed = 2.3f, reverse = true)
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Eyebrow("Profile")
                    Spacer(Modifier.height(6.dp))
                    ResponsiveName(profile.name)
                    PenStroke(Modifier.fillMaxWidth(0.7f).height(18.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(c.good)
                        Spacer(Modifier.width(8.dp))
                        Text(goalLine(profile, targets, u), style = MaterialTheme.typography.labelMedium.merge(tab), color = c.ink)
                    }
                }
            }
        }

        item {
            PlateCard(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val progress = goalProgress(profile.goal, startKg, currentKg, profile.goalWeightKg)
                    ProgressRing(progress, Modifier.size(112.dp), stroke = 12.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(progress * 100).roundToInt()}%", style = PosterType.number)
                            Text("to goal", style = MaterialTheme.typography.labelSmall, color = c.muted)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow("Goal weight")
                        Text(u.weight(profile.goalWeightKg), style = MaterialTheme.typography.headlineMedium.merge(tab))
                        Text(
                            when {
                                profile.goal == Goal.MAINTAIN -> "Holding steady around here"
                                weeks == 0 -> "You've reached it"
                                weeks != null -> "About $weeks week${if (weeks == 1) "" else "s"} at your pace"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                }
            }
        }

        item { forecast() }

        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Weight", u.weight(currentKg), "started at ${u.weight(startKg)}", Modifier.weight(1f))
                    Tile("Height", u.height(profile.heightCm), "${profile.age} years · ${profile.sex.label.lowercase()}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("BMI", "%.1f".format(bmi), Nutrition.bmiLabel(bmi), Modifier.weight(1f))
                    Tile("Water", u.volume(profile.waterGoalMl), "daily goal", Modifier.weight(1f))
                }
            }
        }

        item {
            PlateCard(Modifier.padding(horizontal = 16.dp)) {
                Eyebrow("How your target is set")
                Spacer(Modifier.height(8.dp))
                Line("Resting energy (Mifflin-St Jeor)", "%,d kcal".format(targets.bmr))
                Line("× ${profile.activity.label.lowercase()}", "%,d kcal".format(targets.tdee))
                val adj = targets.kcal - targets.tdee
                Line(
                    if (profile.goal == Goal.MAINTAIN) "No adjustment" else "${profile.goal.label} ${num2(u.weightRaw(profile.paceKgPerWeek))} ${u.weight.label} / week",
                    (if (adj > 0) "+" else if (adj < 0) "−" else "") + "%,d kcal".format(abs(adj)),
                )
                Spacer(Modifier.height(6.dp))
                MenuLine(
                    "Daily target", "%,d".format(targets.kcal), unit = "KCAL",
                    nameStyle = MaterialTheme.typography.titleMedium, numberColor = c.accent,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Protein ${targets.proteinG} g · Carbs ${targets.carbsG} g · Fat ${targets.fatG} g",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Text(
                    "These are estimates, not medical advice. Ask a doctor or dietitian before big changes.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item { UnitsCard(u, onUnits, Modifier.padding(horizontal = 16.dp)) }

        item { extra() }

        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onEdit, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit profile and goals")
                }
                OutlinedButton({ shareApp(context) }, Modifier.fillMaxWidth()) { Text("Share Plate with a friend") }
                if (onCleanInstallers != null) {
                    OutlinedButton(onCleanInstallers, Modifier.fillMaxWidth()) { Text("Clean up old Plate installers") }
                }
                OutlinedButton({ confirmReset = true }, Modifier.fillMaxWidth()) { Text("Erase all data") }
                Text(
                    "Built-in foods include information from Health Canada's Canadian Nutrient File, licensed under the Open Government Licence – Canada. Restaurant menus from USDA FoodData Central (public domain). Online food data from Open Food Facts, available under the Open Database License.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp),
                )
                // The installed version, read from the app itself, so it's always the one you have.
                val version = remember {
                    runCatching {
                        val info = context.packageManager.getPackageInfo(context.packageName, 0)
                        "Plate ${info.versionName} · build ${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)}"
                    }.getOrNull()
                }
                if (version != null) {
                    Text(
                        version, style = MaterialTheme.typography.labelMedium.merge(tab), color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Erase everything?") },
            text = { Text("This deletes your profile, food diary, weigh-ins and saved foods from this phone. It can't be undone.") },
            confirmButton = { TextButton({ confirmReset = false; onReset() }) { Text("Erase", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun goalLine(p: Profile, t: Targets, u: Units.Prefs): String = when (p.goal) {
    Goal.MAINTAIN -> "Maintaining · %,d kcal a day".format(t.kcal)
    else -> "${p.goal.label} ${num2(u.weightRaw(p.paceKgPerWeek))} ${u.weight.label}/week · %,d kcal a day".format(t.kcal)
}

/** Up to two decimals: 0.5 kg, 1.1 lb, 0.25 kg. */
internal fun num2(v: Float): String = ((v * 100f).roundToInt() / 100f).clean()

/** kg or lb, cm or ft in, ml or fl oz: each on its own, saved at once. */
@Composable
fun UnitsCard(u: Units.Prefs, onUnits: (Units.Prefs) -> Unit, modifier: Modifier = Modifier) {
    PlateCard(modifier) {
        Eyebrow("Units")
        Spacer(Modifier.height(4.dp))
        Text("Plate keeps kg, cm and ml inside; this only changes what you see and type.", style = MaterialTheme.typography.bodySmall, color = Plate.colors.muted)
        UnitRow("Body weight", Units.Weight.entries, u.weight, { it.label }) { onUnits(u.copy(weight = it)) }
        UnitRow("Height", Units.Height.entries, u.height, { it.label }) { onUnits(u.copy(height = it)) }
        UnitRow("Drinks", Units.Volume.entries, u.volume, { it.label }) { onUnits(u.copy(volume = it)) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun <T> UnitRow(label: String, options: List<T>, chosen: T, name: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.width(170.dp)) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    chosen == o, { onPick(o) }, androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {},
                ) { Text(name(o), maxLines = 1) }
            }
        }
    }
}

fun goalProgress(goal: Goal, start: Float, now: Float, target: Float): Float {
    if (goal == Goal.MAINTAIN) return if (abs(now - target) <= 1f) 1f else 0.5f
    val total = target - start
    if (abs(total) < 0.01f) return 1f
    return ((now - start) / total).coerceIn(0f, 1f)
}

/** Each letter thickens in a wave on arrival, and on tap. Sized to fit the width. */
@Composable
private fun ResponsiveName(name: String) {
    val letters = name.ifBlank { "You" }
    val bold = remember(letters) { mutableStateListOf(*Array(letters.length) { false }) }
    LaunchedEffect(letters) {
        delay(300)
        letters.indices.forEach { i ->
            launch {
                delay(i * 140L)
                bold[i] = true
                delay(600)
                bold[i] = false
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = letters }) {
        val density = LocalDensity.current
        val widthSp = with(density) { maxWidth.toSp().value }
        val size = (widthSp / (letters.length * 0.62f)).coerceIn(34f, 84f).sp
        Row {
            letters.forEachIndexed { i, ch ->
                val w by animateIntAsState(if (bold[i]) 800 else 500, tween(350), label = "w$i")
                val lift by animateDpAsState(if (bold[i]) (-5).dp else 0.dp, tween(350), label = "y$i")
                Text(
                    ch.toString(),
                    style = TextStyle(fontFamily = Display, fontSize = size, lineHeight = size, letterSpacing = (-1.5).sp, fontWeight = FontWeight(w)),
                    color = Plate.colors.ink,
                    modifier = Modifier.offset(y = lift).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { bold[i] = !bold[i] },
                )
            }
        }
    }
}

/** A hand-drawn underline that draws itself once. */
@Composable
private fun PenStroke(modifier: Modifier) {
    val c = Plate.colors
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(600); p.animateTo(1f, tween(2200, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0.01f * w, 0.6f * h)
            cubicTo(0.15f * w, 0.2f * h, 0.3f * w, 0.93f * h, 0.45f * w, 0.47f * h)
            cubicTo(0.6f * w, 0.1f * h, 0.75f * w, 0.4f * h, 0.99f * w, 0.67f * h)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * p.value, seg, true)
        drawPath(seg, c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, modifier: Modifier) {
    PlateCard(modifier, padding = 16.dp) {
        Eyebrow(label)
        Spacer(Modifier.height(4.dp))
        Text(value.uppercase(), style = PosterType.number)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Plate.colors.muted)
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Plate.colors.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(tab))
    }
}

/** The download page for Plate. */
const val SITE_URL = "https://mrboora04.github.io/ICE3/"

/** Opens Android's share sheet with a short message and the download page. */
private fun shareApp(context: android.content.Context) {
    val text = "I’m tracking food with Plate, a free calorie tracker made for Canada (Tim Hortons, A&W and more built in). Get it here: $SITE_URL"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    context.startActivity(android.content.Intent.createChooser(send, "Share Plate"))
}

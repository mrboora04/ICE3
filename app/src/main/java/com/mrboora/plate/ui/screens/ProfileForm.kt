package com.mrboora.plate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Sex
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.LocalUnits
import com.mrboora.plate.data.Units
import com.mrboora.plate.data.cmFrom
import com.mrboora.plate.data.feetInches
import com.mrboora.plate.data.height
import com.mrboora.plate.data.kgFrom
import com.mrboora.plate.data.mlFrom
import com.mrboora.plate.data.paceSteps
import com.mrboora.plate.data.volume
import com.mrboora.plate.data.volumeValue
import com.mrboora.plate.data.weight
import com.mrboora.plate.data.weightValue
import com.mrboora.plate.data.weightRaw
import kotlin.math.roundToInt
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Plate

fun String.toNumber(): Float? = trim().replace(',', '.').toFloatOrNull()

/** Up to two decimals, trailing zeros dropped: 80 → "80", 0.25 → "0.25", 79.9 → "79.9". */
fun Float.clean(): String = "%.2f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')

/**
 * Shared by onboarding and "Edit profile". Calls [onSave] only with valid values. Weight, height
 * and water are typed in the user's units ([onUnits] changes them right here) and saved metric.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileForm(initial: Profile?, saveLabel: String, onUnits: (Units.Prefs) -> Unit = {}, onSave: (Profile) -> Unit) {
    val u = LocalUnits.current
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var sex by rememberSaveable { mutableStateOf(initial?.sex ?: Sex.FEMALE) }
    var age by rememberSaveable { mutableStateOf(initial?.age?.toString() ?: "") }
    // Height: cm, or feet and inches in two boxes.
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.roundToInt()?.toString() ?: "") }
    var feet by rememberSaveable { mutableStateOf(initial?.heightCm?.let { feetInches(it).first.toString() } ?: "") }
    var inches by rememberSaveable { mutableStateOf(initial?.heightCm?.let { feetInches(it).second.toString() } ?: "") }
    var weight by rememberSaveable { mutableStateOf(initial?.weightKg?.let { u.weightValue(it).clean() } ?: "") }
    var goalWeight by rememberSaveable { mutableStateOf(initial?.goalWeightKg?.let { u.weightValue(it).clean() } ?: "") }
    var activity by rememberSaveable { mutableStateOf(initial?.activity ?: Activity.LIGHT) }
    var goal by rememberSaveable { mutableStateOf(initial?.goal ?: Goal.LOSE) }
    var pace by rememberSaveable { mutableStateOf(initial?.paceKgPerWeek ?: 0.5f) }
    var water by rememberSaveable { mutableStateOf(u.volumeValue(initial?.waterGoalMl ?: 2000).clean()) }

    // Switching units converts what's already typed, so nothing has to be typed again.
    fun switch(to: Units.Prefs) {
        if (to.weight != u.weight) {
            fun conv(t: String) = t.toNumber()?.let { to.weightValue(u.kgFrom(it)).clean() } ?: t
            weight = conv(weight); goalWeight = conv(goalWeight)
        }
        if (to.volume != u.volume) water = water.toNumber()?.let { to.volumeValue(u.mlFrom(it)).clean() } ?: water
        onUnits(to)
    }

    // A box left as it was keeps its exact saved value: 2,500 ml shows as 84.5 fl oz, which
    // would come back as 2,499 ml.
    fun <T> same(shown: String, metric: T?, show: (T) -> String): T? = metric?.takeIf { shown == show(it) }
    val ageN = age.trim().toIntOrNull()
    val heightN = if (u.height == Units.Height.CM) same(height, initial?.heightCm) { it.roundToInt().toString() } ?: height.toNumber()
    else same("$feet $inches", initial?.heightCm) { feetInches(it).let { (f, i) -> "$f $i" } }
        ?: feet.toNumber()?.let { f -> cmFrom(f, inches.toNumber() ?: 0f) }
    val weightN = same(weight, initial?.weightKg) { u.weightValue(it).clean() } ?: weight.toNumber()?.let(u::kgFrom)
    val goalN = if (goal == Goal.MAINTAIN && goalWeight.isBlank()) weightN
    else same(goalWeight, initial?.goalWeightKg) { u.weightValue(it).clean() } ?: goalWeight.toNumber()?.let(u::kgFrom)
    val waterN = same(water, initial?.waterGoalMl) { u.volumeValue(it).clean() } ?: water.toNumber()?.let(u::mlFrom)

    // The same limits whatever the units, said in the user's units.
    val weightRange = "${u.weight(30f)}–${u.weight(350f)}"
    val errors = buildMap {
        if (name.isBlank()) put("name", "Tell us what to call you")
        if (ageN == null || ageN !in 13..100) put("age", "Age 13–100")
        if (heightN == null || heightN !in 100f..250f) put("height", "${u.height(100f)}–${u.height(250f)}")
        if (weightN == null || weightN !in 30f..350f) put("weight", weightRange)
        if (goalN == null || goalN !in 30f..350f) put("goal", weightRange)
        else if (weightN != null && goal == Goal.LOSE && goalN >= weightN) put("goal", "Should be below your current weight")
        else if (weightN != null && goal == Goal.GAIN && goalN <= weightN) put("goal", "Should be above your current weight")
        if (waterN == null || waterN !in 500..6000) put("water", "${u.volume(500)}–${u.volume(6000)}")
    }
    var tried by rememberSaveable { mutableStateOf(false) }
    fun err(k: String) = if (tried) errors[k] else null

    val preview = if (errors.isEmpty()) Profile(
        name = name.trim(), sex = sex, age = ageN!!, heightCm = heightN!!, weightKg = weightN!!,
        goalWeightKg = goalN!!, activity = activity, goal = goal,
        paceKgPerWeek = if (goal == Goal.MAINTAIN) 0f else pace, waterGoalMl = waterN!!,
    ) else null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Field("Your name", name, { name = it }, err("name"), KeyboardType.Text)

        UnitsCard(u, ::switch)

        Eyebrow("Sex (for the energy formula)")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, s ->
                SegmentedButton(sex == s, { sex = s }, SegmentedButtonDefaults.itemShape(i, Sex.entries.size)) { Text(s.label) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Age", age, { age = it }, err("age"), KeyboardType.Number, Modifier.weight(1f))
            if (u.height == Units.Height.CM) {
                Field("Height (cm)", height, { height = it }, err("height"), KeyboardType.Decimal, Modifier.weight(1f))
            } else {
                Field("Feet", feet, { feet = it }, err("height"), KeyboardType.Number, Modifier.weight(0.5f))
                Field("Inches", inches, { inches = it }, null, KeyboardType.Decimal, Modifier.weight(0.5f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Weight (${u.weight.label})", weight, { weight = it }, err("weight"), KeyboardType.Decimal, Modifier.weight(1f))
            Field("Goal weight (${u.weight.label})", goalWeight, { goalWeight = it }, err("goal"), KeyboardType.Decimal, Modifier.weight(1f))
        }

        Eyebrow("Goal")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Goal.entries.forEachIndexed { i, g ->
                SegmentedButton(goal == g, { goal = g }, SegmentedButtonDefaults.itemShape(i, Goal.entries.size)) { Text(g.label) }
            }
        }
        if (goal != Goal.MAINTAIN) {
            Eyebrow("Pace per week")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Round steps in the user's unit: ¼ to 1 kg, or ½ to 2 lb, a week.
                val steps = u.paceSteps().map(u::kgFrom)
                // A pace set in the other unit (0.5 kg = 1.1 lb) stays as its own chip, chosen.
                val all = if (steps.none { kotlin.math.abs(it - pace) < 0.01f }) (steps + pace).sorted() else steps
                all.forEach { kg ->
                    val v = ((u.weightRaw(kg) * 100f).roundToInt() / 100f).clean()
                    FilterChip(kotlin.math.abs(pace - kg) < 0.01f, { pace = kg }, label = { Text("$v ${u.weight.label}") })
                }
            }
        }

        Eyebrow("Activity")
        Column {
            Activity.entries.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().selectable(activity == a, role = Role.RadioButton) { activity = a }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(activity == a, null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(a.label, style = MaterialTheme.typography.titleSmall)
                        Text(a.detail, style = MaterialTheme.typography.bodySmall, color = Plate.colors.muted)
                    }
                }
            }
        }

        Field("Daily water goal (${u.volume.label})", water, { water = it }, err("water"), KeyboardType.Number)

        if (preview != null) {
            val t = Nutrition.targets(preview)
            PlateCard {
                Eyebrow("Your daily target")
                Text(
                    "%,d kcal".format(t.kcal),
                    style = MaterialTheme.typography.displayMedium.merge(TextStyle(fontFeatureSettings = TABULAR)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Protein ${t.proteinG} g · Carbs ${t.carbsG} g · Fat ${t.fatG} g",
                    style = MaterialTheme.typography.bodyMedium, color = Plate.colors.muted,
                )
                Text(
                    "Resting ${"%,d".format(t.bmr)} kcal × activity = ${"%,d".format(t.tdee)} kcal to maintain",
                    style = MaterialTheme.typography.bodySmall, color = Plate.colors.muted,
                )
            }
        }

        Button(
            onClick = { tried = true; preview?.let(onSave) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(saveLabel) }
        if (tried && errors.isNotEmpty()) {
            Text("Check the highlighted fields.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    type: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier,
    )
}

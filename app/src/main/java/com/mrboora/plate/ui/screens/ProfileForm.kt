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
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Plate

fun String.toNumber(): Float? = trim().replace(',', '.').toFloatOrNull()

/** Up to two decimals, trailing zeros dropped: 80 → "80", 0.25 → "0.25", 79.9 → "79.9". */
fun Float.clean(): String = "%.2f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')

/** Shared by onboarding and "Edit profile". Calls [onSave] only with valid values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileForm(initial: Profile?, saveLabel: String, onSave: (Profile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var sex by rememberSaveable { mutableStateOf(initial?.sex ?: Sex.FEMALE) }
    var age by rememberSaveable { mutableStateOf(initial?.age?.toString() ?: "") }
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.clean() ?: "") }
    var weight by rememberSaveable { mutableStateOf(initial?.weightKg?.clean() ?: "") }
    var goalWeight by rememberSaveable { mutableStateOf(initial?.goalWeightKg?.clean() ?: "") }
    var activity by rememberSaveable { mutableStateOf(initial?.activity ?: Activity.LIGHT) }
    var goal by rememberSaveable { mutableStateOf(initial?.goal ?: Goal.LOSE) }
    var pace by rememberSaveable { mutableStateOf(initial?.paceKgPerWeek ?: 0.5f) }
    var water by rememberSaveable { mutableStateOf((initial?.waterGoalMl ?: 2000).toString()) }

    val ageN = age.trim().toIntOrNull()
    val heightN = height.toNumber()
    val weightN = weight.toNumber()
    val goalN = if (goal == Goal.MAINTAIN && goalWeight.isBlank()) weightN else goalWeight.toNumber()
    val waterN = water.trim().toIntOrNull()

    val errors = buildMap {
        if (name.isBlank()) put("name", "Tell us what to call you")
        if (ageN == null || ageN !in 13..100) put("age", "Age 13–100")
        if (heightN == null || heightN !in 100f..250f) put("height", "100–250 cm")
        if (weightN == null || weightN !in 30f..350f) put("weight", "30–350 kg")
        if (goalN == null || goalN !in 30f..350f) put("goal", "30–350 kg")
        else if (weightN != null && goal == Goal.LOSE && goalN >= weightN) put("goal", "Should be below your current weight")
        else if (weightN != null && goal == Goal.GAIN && goalN <= weightN) put("goal", "Should be above your current weight")
        if (waterN == null || waterN !in 500..6000) put("water", "500–6000 ml")
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

        Eyebrow("Sex (for the energy formula)")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, s ->
                SegmentedButton(sex == s, { sex = s }, SegmentedButtonDefaults.itemShape(i, Sex.entries.size)) { Text(s.label) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Age", age, { age = it }, err("age"), KeyboardType.Number, Modifier.weight(1f))
            Field("Height (cm)", height, { height = it }, err("height"), KeyboardType.Decimal, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Weight (kg)", weight, { weight = it }, err("weight"), KeyboardType.Decimal, Modifier.weight(1f))
            Field("Goal weight (kg)", goalWeight, { goalWeight = it }, err("goal"), KeyboardType.Decimal, Modifier.weight(1f))
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
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { v ->
                    FilterChip(pace == v, { pace = v }, label = { Text("${v.clean()} kg") })
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

        Field("Daily water goal (ml)", water, { water = it }, err("water"), KeyboardType.Number)

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

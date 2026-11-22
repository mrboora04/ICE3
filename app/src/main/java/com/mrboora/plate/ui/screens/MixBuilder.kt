package com.mrboora.plate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrboora.plate.data.FoodItem
import com.mrboora.plate.data.Mix
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.MenuLine
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PosterType
import kotlin.math.roundToInt

private fun g(v: Float) = if (v < 10f) "%.1f".format(v) else "%,d".format(v.roundToInt())

/** One editable ingredient row's state. */
private class MixRow(val label: String, val food: FoodItem, grams: String) {
    var grams by mutableStateOf(grams)
    val value get() = grams.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f && it <= 3000f }
}

/**
 * Shake builder: fruit(s) in grams + a base in ml, shown as plain arithmetic, saved as one glass.
 * Everything comes from per-100 g Health Canada numbers; nothing is stored per shake.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShakeBuilder(
    ingredients: suspend () -> Map<String, FoodItem>,
    searchIngredients: suspend (String) -> List<FoodItem>,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit,
    preview: Map<String, FoodItem>? = null,
) {
    val c = Plate.colors
    val foods = preview ?: produceState<Map<String, FoodItem>?>(null) { value = ingredients() }.value
    val rows = remember { mutableStateListOf<MixRow>() }
    var base by remember { mutableStateOf("Milk 3.25%") }
    var baseMl by remember { mutableStateOf("250") }
    var glasses by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // A brand-new builder starts the way most shakes start: one banana.
    LaunchedEffect(foods) {
        if (rows.isEmpty() && preview == null) foods?.get("Banana, raw")?.let { rows += MixRow("Banana", it, g(it.servingG)) }
    }
    if (preview != null && rows.isEmpty()) {
        preview["Banana, raw"]?.let { rows += MixRow("Banana", it, "354") }
        preview[Mix.MIXED_BERRIES]?.let { rows += MixRow("Mixed berries", it, "30") }
    }

    val baseFood = foods?.get(Mix.BASES.first { it.first == base }.second)
    val ml = baseMl.toFloatOrNull()?.takeIf { it in 0f..2000f }
    val parts = buildList {
        rows.forEach { r -> r.value?.let { add(Mix.Part(r.food, it)) } }
        if (baseFood != null && ml != null && ml > 0f) add(Mix.Part(baseFood, Mix.mlToGrams(baseFood, ml)))
    }
    val auto = Mix.shakeName(rows.map { it.label })
    val shownName = if (nameEdited) name else auto
    val t = Mix.totals(parts)
    val n = glasses.coerceAtLeast(1)

    val content = @Composable {
        Column(
            Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SHAKE BUILDER", style = PosterType.title)
                    Text("Fruit + base, worked out from Health Canada numbers", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                IconButton(onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            }
            Spacer(Modifier.height(12.dp))
            if (foods == null) {
                Text("Loading ingredients…", color = c.muted)
                return@Column
            }

            // 1. Fruit and extras.
            PlateCard(padding = 14.dp) {
                Eyebrow("1 · Fruit and extras")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (Mix.FRUITS + Mix.EXTRAS).forEach { (label, key) ->
                        val food = foods[key] ?: return@forEach
                        val on = rows.any { it.label == label }
                        FilterChip(on, {
                            if (on) rows.removeAll { it.label == label }
                            else rows += MixRow(label, food, g(Mix.quickAmounts(food).first().second))
                        }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    query, { query = it }, singleLine = true, label = { Text("Something else? Search any food") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                val results = produceState(emptyList<FoodItem>(), query) {
                    value = if (query.trim().length < 2) emptyList() else searchIngredients(query)
                }.value
                results.take(5).forEach { f ->
                    Text(
                        "+ ${f.name}", style = MaterialTheme.typography.bodyMedium, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable {
                            rows += MixRow(f.name.substringBefore(',').trim(), f, g(Mix.quickAmounts(f).first().second)); query = ""
                        }.padding(vertical = 8.dp),
                    )
                }
                rows.forEach { r ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            r.grams, { r.grams = it }, singleLine = true, suffix = { Text("g") }, isError = r.value == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(110.dp),
                        )
                        IconButton({ rows.remove(r) }) { Icon(Icons.Rounded.Close, "Remove ${r.label}", tint = c.muted) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Mix.quickAmounts(r.food).forEach { (label, grams) ->
                            FilterChip(r.value == grams.roundToInt().toFloat() || r.value == grams, { r.grams = g(grams) }, label = { Text(if (label.endsWith(" g")) label else "$label · ${g(grams)} g") })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 2. Base.
            PlateCard(padding = 14.dp) {
                Eyebrow("2 · Base")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mix.BASES.forEach { (label, key) -> if (foods[key] != null) FilterChip(base == label, { base = label }, label = { Text(label) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        baseMl, { baseMl = it }, singleLine = true, suffix = { Text("ml") }, isError = ml == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp),
                    )
                    listOf(125, 250, 350).forEach { v -> FilterChip(ml == v.toFloat(), { baseMl = "$v" }, label = { Text("$v") }) }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 3. The math, shown.
            PlateCard(padding = 14.dp) {
                Eyebrow("3 · The math")
                Spacer(Modifier.height(6.dp))
                val num = PosterType.number.copy(fontSize = 20.sp, lineHeight = 20.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { p ->
                        MenuLine(
                            "${p.food.name.substringBefore(',')} ${g(p.grams)} g × ${p.food.kcal100.roundToInt()}/100 g",
                            "%,d".format(p.kcal.roundToInt()), unit = "KCAL", nameStyle = MaterialTheme.typography.bodyMedium, numberStyle = num,
                        )
                    }
                    MenuLine("Whole batch, ${g(t.grams)} g", "%,d".format(t.kcal.roundToInt()), unit = "KCAL", numberStyle = num)
                }
                if (rows.any { it.food.name == Mix.MIXED_BERRIES }) {
                    Text("Mixed berries = equal parts blueberry, raspberry and strawberry.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Makes", style = MaterialTheme.typography.titleSmall)
                    (1..3).forEach { k -> FilterChip(glasses == k, { glasses = k }, label = { Text("$k glass${if (k > 1) "es" else ""}") }) }
                }
                Spacer(Modifier.height(8.dp))
                MenuLine("≈ One glass", "%,d".format((t.kcal / n).roundToInt()), unit = "KCAL", numberStyle = PosterType.number, numberColor = c.accent)
                Text(
                    "P ${(t.protein / n).roundToInt()} g · C ${(t.carbs / n).roundToInt()} g · F ${(t.fat / n).roundToInt()} g · ≈ homemade, no official label",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                shownName, { name = it; nameEdited = true }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                { onSave(Mix.build(shownName.ifBlank { auto }, parts, n)) },
                enabled = parts.isNotEmpty() && rows.isNotEmpty() && rows.all { it.value != null } && ml != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save and add 1 glass") }
            Text(
                "It's saved in your foods, so next time it's one tap. The grams are only for building it; you log it by the glass.",
                style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (preview != null) content()
    else Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { content() }
}

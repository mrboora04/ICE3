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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
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

/** Grams for the text fields: no thousands separator, because the field reads "1,5" as 1.5. */
private fun g(v: Float) = if (v < 10f) "%.1f".format(java.util.Locale.ROOT, v) else "${v.roundToInt()}"

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
    /** The saved food (one glass or one slice) and how many of them to log now. */
    onSave: (FoodItem, Float) -> Unit,
    preview: Map<String, FoodItem>? = null,
    /** What the user typed, already read into ingredients and amounts. */
    draft: com.mrboora.plate.data.MixParser.Draft? = null,
) {
    val c = Plate.colors
    val foods = preview ?: produceState<Map<String, FoodItem>?>(null) { value = ingredients() }.value
    val rows = remember { mutableStateListOf<MixRow>() }
    var base by remember { mutableStateOf("Milk 3.25%") }
    var baseMl by remember { mutableStateOf("250") }
    var glasses by remember { mutableStateOf(1) }
    // Dish mode (pizza): its size, how many slices it's cut into, and how many were eaten.
    val dishMode = draft?.dish != null
    var size by remember { mutableStateOf(draft?.dish?.size ?: Mix.PIZZA_DEFAULT) }
    var slices by remember { mutableStateOf(draft?.dish?.size?.slices ?: 8) }
    var ate by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Start from what was typed; otherwise the way most shakes start, with one banana.
    var started by remember { mutableStateOf(false) }
    if (!started && draft != null) {
        started = true
        draft.parts.forEach { rows += MixRow(it.label, it.food, g(it.grams)) }
        draft.base?.let { b ->
            if (Mix.BASES.any { it.first == b.label }) { base = b.label; baseMl = "${(b.ml ?: 250f).roundToInt()}" }
            else rows += MixRow(b.label, b.food, g(b.grams))
        } ?: run { base = "Water"; baseMl = "0" }
    }
    LaunchedEffect(foods) {
        if (rows.isEmpty() && preview == null && draft == null) foods?.get("Banana, raw")?.let { rows += MixRow("Banana", it, g(it.servingG)) }
    }
    if (preview != null && rows.isEmpty() && draft == null) {
        preview["Banana, raw"]?.let { rows += MixRow("Banana", it, "354") }
        preview[Mix.MIXED_BERRIES]?.let { rows += MixRow("Mixed berries", it, "30") }
    }

    val baseFood = foods?.get(Mix.BASES.first { it.first == base }.second)
    val ml = baseMl.toFloatOrNull()?.takeIf { it in 0f..2000f }
    val parts = buildList {
        rows.forEach { r -> r.value?.let { add(Mix.Part(r.food, it)) } }
        if (baseFood != null && ml != null && ml > 0f) add(Mix.Part(baseFood, Mix.mlToGrams(baseFood, ml)))
    }
    val auto = if (dishMode) {
        val tops = rows.filter { it.food.name != Mix.PIZZA }.map { it.label.lowercase() }
        "${size.label} cheese pizza" + if (tops.isEmpty()) "" else " with " + tops.joinToString(" & ")
    } else Mix.shakeName(rows.map { it.label })
    val shownName = if (nameEdited) name else auto
    val t = Mix.totals(parts)
    val n = (if (dishMode) slices else glasses).coerceAtLeast(1)
    val one = if (dishMode) "slice" else "glass"

    val content = @Composable {
        Column(
            Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (dishMode) "PIZZA BUILDER" else "SHAKE BUILDER", style = PosterType.title)
                    Text(
                        if (dishMode) "Pizza + toppings, worked out from Health Canada numbers" else "Fruit + base, worked out from Health Canada numbers",
                        style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
                IconButton(onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            }
            Spacer(Modifier.height(12.dp))
            // How the typed text was read, step by step, so it can be checked.
            if (draft != null) {
                PlateCard(padding = 14.dp) {
                    Eyebrow("How I read it")
                    Spacer(Modifier.height(6.dp))
                    draft.items.forEach { i ->
                        Text("${i.label}: ${i.how}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    draft.unknown.forEach { u ->
                        Text("“$u”: not found, add it with the search below", style = MaterialTheme.typography.bodyMedium, color = c.over, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Text("Change any amount below; the math updates.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (foods == null) {
                Text("Loading ingredients…", color = c.muted)
                return@Column
            }

            // 1. Fruit and extras.
            PlateCard(padding = 14.dp) {
                if (dishMode) {
                    // Size: changing it rescales the pizza and its toppings by area.
                    Eyebrow("Size")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Mix.PIZZA_SIZES.forEach { s ->
                            FilterChip(size == s, {
                                val ratio = s.grams / size.grams
                                rows.forEach { r -> r.value?.let { r.grams = g(it * ratio) } }
                                size = s; slices = s.slices; if (ate > s.slices) ate = s.slices
                            }, label = { Text(s.label) })
                        }
                    }
                    Text(size.note, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Spacer(Modifier.height(14.dp))
                }
                Eyebrow(if (dishMode) "1 · Pizza and toppings" else "1 · Fruit and extras")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (dishMode) Mix.TOPPINGS else Mix.FRUITS + Mix.EXTRAS).forEach { (label, key) ->
                        val food = foods[key] ?: return@forEach
                        val on = rows.any { it.label == label }
                        FilterChip(on, {
                            if (on) rows.removeAll { it.label == label }
                            else rows += MixRow(label, food, g(if (dishMode) Mix.toppingGrams(label, size) else Mix.quickAmounts(food).first().second))
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
                    // The pizza row always follows the size picked above, and says what its grams mean.
                    val isPizza = dishMode && r.food.name == Mix.PIZZA
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isPizza) "Whole ${size.label.lowercase()} pizza" else r.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            r.grams, { r.grams = it }, singleLine = true, suffix = { Text("g") }, isError = r.value == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(110.dp),
                        )
                        IconButton({ rows.remove(r) }) { Icon(Icons.Rounded.Close, "Remove ${r.label}", tint = c.muted) }
                    }
                    if (isPizza) {
                        Text(
                            "The whole pizza before it's cut. How much you ate is set with slices below.",
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (isPizza) listOf("1 pizza" to size.grams, "2 pizzas" to size.grams * 2) else Mix.quickAmounts(r.food)).forEach { (label, grams) ->
                            FilterChip(r.value == grams.roundToInt().toFloat() || r.value == grams, { r.grams = g(grams) }, label = { Text(if (label.endsWith(" g")) label else "$label · ${g(grams)} g") })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 2. Base (shakes only; a pizza has no liquid base).
            if (!dishMode) PlateCard(padding = 14.dp) {
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
                    if (dishMode) {
                        Text("Cut into", style = MaterialTheme.typography.titleSmall)
                        Stepper(slices, 1..48) { slices = it; if (ate > it) ate = it }
                        Text("slices", style = MaterialTheme.typography.titleSmall)
                    } else {
                        Text("Makes", style = MaterialTheme.typography.titleSmall)
                        (1..3).forEach { k -> FilterChip(glasses == k, { glasses = k }, label = { Text("$k glass${if (k > 1) "es" else ""}") }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                MenuLine(if (dishMode) "≈ One slice" else "≈ One glass", "%,d".format((t.kcal / n).roundToInt()), unit = "KCAL", numberStyle = PosterType.number, numberColor = c.accent)
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
            if (dishMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("I ate", style = MaterialTheme.typography.titleSmall)
                    Stepper(ate.coerceAtMost(slices), 1..slices) { ate = it }
                    Text("slice${if (ate > 1) "s" else ""} of $slices", style = MaterialTheme.typography.titleSmall)
                }
            }
            Button(
                { onSave(Mix.build(shownName.ifBlank { auto }, parts, n, unit = one), (if (dishMode) ate else 1).toFloat()) },
                enabled = parts.isNotEmpty() && rows.isNotEmpty() && rows.all { it.value != null } && ml != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (dishMode) "Save and add $ate slice${if (ate > 1) "s" else ""} · ≈ %,d kcal".format((t.kcal / n * ate).roundToInt())
                    else "Save and add 1 glass",
                )
            }
            Text(
                "It's saved in your foods, so next time it's one tap. The grams are only for building it; you log it by the $one.",
                style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (preview != null) content()
    else Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { content() }
}

/** − n + for small whole numbers. */
@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.FilledTonalIconButton({ onChange((value - 1).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(36.dp)) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Remove, "Fewer", Modifier.size(18.dp))
        }
        Text("$value", style = PosterType.number.copy(fontSize = 22.sp, lineHeight = 22.sp), modifier = Modifier.padding(horizontal = 10.dp))
        androidx.compose.material3.FilledTonalIconButton({ onChange((value + 1).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(36.dp)) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Add, "More", Modifier.size(18.dp))
        }
    }
}

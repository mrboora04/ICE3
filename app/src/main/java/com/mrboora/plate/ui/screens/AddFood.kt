package com.mrboora.plate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.FoodItem
import com.mrboora.plate.data.Meal
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.components.TABULAR
import com.mrboora.plate.ui.theme.Plate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val tab = TextStyle(fontFeatureSettings = TABULAR)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    meal: Meal,
    dayText: String,
    recent: List<FoodItem>,
    searchLocal: suspend (String) -> List<FoodItem>,
    searchOnline: suspend (String) -> Result<List<FoodItem>>,
    lookupBarcode: suspend (String) -> Result<FoodItem?>,
    onCreate: (FoodItem) -> Unit,
    onForget: (FoodItem) -> Unit,
    onAdd: (FoodItem, Float) -> Unit,
    onBack: () -> Unit,
) {
    val c = Plate.colors
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var local by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var online by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var onlineState by remember { mutableStateOf<String?>(null) } // null idle, "loading", or a message
    var picked by remember { mutableStateOf<FoodItem?>(null) }
    var showBarcode by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(150)
        local = searchLocal(query)
        online = emptyList()
        onlineState = null
    }

    fun runOnline() {
        if (query.isBlank()) return
        onlineState = "loading"
        scope.launch {
            searchOnline(query).fold(
                onSuccess = { online = it; onlineState = if (it.isEmpty()) "No online results for \"$query\"." else null },
                onFailure = { onlineState = "Couldn't reach Open Food Facts. Check your connection." },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add to ${meal.label}", style = MaterialTheme.typography.titleLarge)
                        Text(dayText, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.ground),
            )
        },
        containerColor = c.ground,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search foods, e.g. dal, banana") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runOnline() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip({ runOnline() }, { Text("Search online") }, leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(18.dp)) }, enabled = query.isNotBlank())
                    AssistChip({ showBarcode = true }, { Text("Barcode") }, leadingIcon = { Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp)) })
                    AssistChip({ showCreate = true }, { Text("Create") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
                }
            }

            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item { Eyebrow("Recent and your foods", Modifier.padding(top = 8.dp)) }
                    items(recent, key = { "r" + it.key }) { FoodRow(it, { picked = it }, onForget) }
                } else {
                    item {
                        Text(
                            "Search the built-in list, or tap Search online for packaged foods from Open Food Facts. For restaurant meals like an A&W burger, tap Create and enter the calories from the restaurant's menu once. Foods you add show up here next time.",
                            style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                if (local.isNotEmpty()) {
                    item { Eyebrow("On this phone", Modifier.padding(top = 8.dp)) }
                    items(local, key = { "l" + it.key }) { FoodRow(it, { picked = it }, onForget) }
                }
                if (online.isNotEmpty()) {
                    item { Eyebrow("Open Food Facts", Modifier.padding(top = 8.dp)) }
                    items(online, key = { "o" + it.key }) { FoodRow(it, { picked = it }, null) }
                }
                item {
                    when (val s = onlineState) {
                        "loading" -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        null -> if (online.isEmpty()) TextButton({ runOnline() }) { Text("Not here? Search Open Food Facts for \"$query\"") }
                        else -> Text(s, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
                item {
                    TextButton({ showCreate = true }) {
                        Text("Eating out? Add \"$query\" from the restaurant's menu numbers")
                    }
                }
            }
        }
    }

    picked?.let { food ->
        AmountSheet(food, onDismiss = { picked = null }) { grams ->
            onAdd(food, grams)
            picked = null
            onBack()
        }
    }
    if (showBarcode) {
        BarcodeDialog(lookupBarcode, onDismiss = { showBarcode = false }) { found ->
            showBarcode = false
            picked = found
        }
    }
    if (showCreate) {
        CreateFoodDialog(initialName = query, onDismiss = { showCreate = false }) { food ->
            showCreate = false
            onCreate(food)
            picked = food
        }
    }
}

@Composable
private fun FoodRow(food: FoodItem, onClick: () -> Unit, onForget: ((FoodItem) -> Unit)?) {
    val c = Plate.colors
    PlateCard(Modifier.clickable(onClick = onClick), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    if (food.brand.isNotBlank()) add(food.brand)
                    add(if (food.perItem) "${food.kcal100.roundToInt()} kcal each" else "${food.kcal100.roundToInt()} kcal / 100 g")
                    if (food.source == FoodItem.Source.CUSTOM) add("your food")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall.merge(tab), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onForget != null && (food.source == FoodItem.Source.SAVED || food.source == FoodItem.Source.CUSTOM)) {
                IconButton({ onForget(food) }) { Icon(Icons.Rounded.Close, "Forget ${food.name}", tint = c.muted) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AmountSheet(food: FoodItem, onDismiss: () -> Unit, onConfirm: (Float) -> Unit) {
    val c = Plate.colors
    var text by remember(food) { mutableStateOf(if (food.perItem) "1" else food.servingG.clean()) }
    val g = text.toNumber()?.takeIf { it > 0f && it <= (if (food.perItem) 50f else 5000f) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(food.name, style = MaterialTheme.typography.headlineSmall)
            if (food.brand.isNotBlank()) Text(food.brand, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(if (food.perItem) "How many" else "Amount (g or ml)") }, singleLine = true,
                isError = g == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (food.perItem) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 1f, 2f, 3f).forEach { v ->
                    FilterChip(g == v, { text = v.clean() }, label = { Text("${v.clean()} ×") })
                }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val serving = food.servingG
                val label = food.servingLabel.ifBlank { "serving" }
                FilterChip(g == serving, { text = serving.clean() }, label = { Text("1 $label (${serving.clean()} g)") })
                if (serving != 100f) FilterChip(g == serving * 2, { text = (serving * 2).clean() }, label = { Text("× 2") })
                listOf(50f, 100f, 200f).filter { it != serving }.forEach { v ->
                    FilterChip(g == v, { text = v.clean() }, label = { Text("${v.clean()} g") })
                }
            }
            Spacer(Modifier.height(16.dp))
            val gg = g ?: 0f
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Num("kcal", food.kcalFor(gg))
                Num("Protein", food.proteinFor(gg), "g")
                Num("Carbs", food.carbsFor(gg), "g")
                Num("Fat", food.fatFor(gg), "g")
            }
            Spacer(Modifier.height(20.dp))
            Button({ g?.let(onConfirm) }, enabled = g != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Add") }
        }
    }
}

@Composable
private fun Num(label: String, v: Float, unit: String = "") {
    Column {
        Eyebrow(label)
        Text("${v.roundToInt()}$unit", style = MaterialTheme.typography.titleLarge.merge(tab))
    }
}

@Composable
private fun BarcodeDialog(lookup: suspend (String) -> Result<FoodItem?>, onDismiss: () -> Unit, onFound: (FoodItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Look up a barcode") },
        text = {
            Column {
                Text("Type the number under the barcode on the pack.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    code, { code = it.filter(Char::isDigit) }, label = { Text("Barcode number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length >= 6 && !busy,
                onClick = {
                    busy = true; msg = null
                    scope.launch {
                        lookup(code).fold(
                            onSuccess = { if (it != null) onFound(it) else msg = "Not found in Open Food Facts. Try Create instead." },
                            onFailure = { msg = "Couldn't reach Open Food Facts." },
                        )
                        busy = false
                    }
                },
            ) { Text(if (busy) "Looking…" else "Look up") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateFoodDialog(initialName: String, onDismiss: () -> Unit, onCreate: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var cb by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var serving by remember { mutableStateOf("100") }
    var brand by remember { mutableStateOf("") }
    var perItem by remember { mutableStateOf(true) }
    val kcalN = kcal.toNumber()
    val servingN = if (perItem) 1f else serving.toNumber()
    val maxKcal = if (perItem) 5000f else 900f
    val ok = name.isNotBlank() && kcalN != null && kcalN in 0f..maxKcal && servingN != null && servingN > 0f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add your own food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(perItem, { perItem = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Per item") }
                    SegmentedButton(!perItem, { perItem = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Per 100 g") }
                }
                Text(
                    if (perItem) "For restaurant food: copy the numbers for one item from the restaurant's menu, app or nutrition guide."
                    else "Values per 100 g, as printed on most packaged-food labels.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(name, { name = it }, label = { Text(if (perItem) "Item, e.g. Papa Burger" else "Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brand, { brand = it }, label = { Text("Restaurant or brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small(if (perItem) "kcal per item" else "kcal", kcal, { kcal = it }, Modifier.weight(1f))
                    if (!perItem) Small("Serving g", serving, { serving = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small("Protein", p, { p = it }, Modifier.weight(1f))
                    Small("Carbs", cb, { cb = it }, Modifier.weight(1f))
                    Small("Fat", f, { f = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onCreate(
                    FoodItem(
                        name.trim(), brand.trim(), kcalN!!, p.toNumber() ?: 0f, cb.toNumber() ?: 0f, f.toNumber() ?: 0f,
                        servingN!!, source = FoodItem.Source.CUSTOM, perItem = perItem,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Small(label: String, v: String, on: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(v, on, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

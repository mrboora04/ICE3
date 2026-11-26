package com.mrboora.plate.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PosterType

/** Design preview for #10: the home-cooked dish as three questions. Not wired into the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickDishPreview(name: String, mains: List<Pair<String, Int>>, quietKcal: Int, kcalOne: Int, size: Int, count: Int) {
    val c = Plate.colors
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name.uppercase(), style = PosterType.title)
                Text("Home-cooked · worked out from Health Canada numbers", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            IconButton({}) { Icon(Icons.Rounded.Close, "Close") }
        }
        Spacer(Modifier.height(14.dp))
        PlateCard {
            Eyebrow("1 · What's in it")
            Spacer(Modifier.height(8.dp))
            // Main ingredients: the real numbers, one line each.
            mains.forEach { (n, kcal) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("$kcal kcal", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    Icon(Icons.Rounded.Edit, "Change $n", Modifier.padding(start = 10.dp).size(18.dp), tint = c.muted)
                }
            }
            TextButton({}, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add an ingredient") }
            HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 6.dp))
            // The things people don't measure: one quiet line, still with its calories.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Onion-tomato base, oil and spices", style = MaterialTheme.typography.bodyMedium)
                    Text("The usual amount", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Text("$quietKcal kcal", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Icon(Icons.Rounded.Edit, "Change base and oil", Modifier.padding(start = 10.dp).size(18.dp), tint = c.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        PlateCard {
            Eyebrow("2 · How big a bowl?")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small" to "¾ cup", "Regular" to "1 cup", "Large" to "1½ cups").forEachIndexed { i, (l, h) ->
                    FilterChip(size == i, {}, modifier = Modifier.weight(1f), label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(l, style = MaterialTheme.typography.labelLarge)
                            Text(h, style = MaterialTheme.typography.labelSmall, color = c.muted)
                        }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("3 · How many bowls?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                FilledTonalIconButton({}, enabled = count > 1) { Icon(Icons.Rounded.Remove, "One less") }
                Text("$count", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                FilledTonalIconButton({}) { Icon(Icons.Rounded.Add, "One more") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("≈ ${"%,d".format(kcalOne * count)}", style = PosterType.hero.copy(fontSize = 56.sp, lineHeight = 52.sp), color = c.accent)
            Text(" KCAL", style = PosterType.section, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("P 4 g · C 27 g · F 10 g · homemade estimate", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(12.dp))
        Button({}, Modifier.fillMaxWidth().height(56.dp)) { Text("Add $count ${if (count > 1) "bowls" else "bowl"} · ≈ ${kcalOne * count} kcal") }
        TextButton({}, Modifier.align(Alignment.CenterHorizontally)) { Text("See the details (grams and the math)") }
    }
}

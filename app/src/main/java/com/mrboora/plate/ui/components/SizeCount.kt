package com.mrboora.plate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.Portions
import com.mrboora.plate.ui.theme.Plate

/**
 * One simple pattern for foods you count or spoon, used in Add food and in the builders:
 * Type (only when it matters) · Size: Small / Regular / Large · How many: − n +.
 */
@Composable
fun SizeCountPicker(
    picks: List<Portions.Pick>,
    size: Int,
    count: Int,
    onChange: (size: Int, count: Int) -> Unit,
    modifier: Modifier = Modifier,
    types: List<String>? = null,
    type: Int = 0,
    onType: (Int) -> Unit = {},
    /** What's counted, plural: "eggs", "slices", "spoons". */
    things: String? = null,
) {
    val c = Plate.colors
    Column(modifier) {
        if (types != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                types.forEachIndexed { i, t -> FilterChip(type == i, { onType(i) }, label = { Text(t) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            picks.forEachIndexed { i, p ->
                FilterChip(
                    size == i, { onChange(i, count) }, modifier = Modifier.weight(1f),
                    label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(p.label, style = MaterialTheme.typography.labelLarge)
                            Text(p.hint, style = MaterialTheme.typography.labelSmall, color = c.muted)
                        }
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(if (things != null) "How many $things?" else "How many?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilledTonalIconButton({ onChange(size, count - 1) }, enabled = count > 1) { Icon(Icons.Rounded.Remove, "One less") }
            Text("$count", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
            FilledTonalIconButton({ onChange(size, (count + 1).coerceAtMost(20)) }) { Icon(Icons.Rounded.Add, "One more") }
        }
    }
}

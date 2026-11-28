package com.mrboora.plate.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mrboora.plate.data.Shelves
import com.mrboora.plate.ui.components.Eyebrow
import com.mrboora.plate.ui.components.MenuLine
import com.mrboora.plate.ui.components.PlateCard
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PosterType

/** Design preview: the top of Add food, sorted into what each thing is. Not wired into the app. */
@Composable
fun AddFoodTopPreview() {
    val c = Plate.colors
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            Spacer(Modifier.width(16.dp))
            Column { Text("ADD FOOD", style = PosterType.section); Text("Today", style = MaterialTheme.typography.bodySmall, color = c.muted) }
        }
        Spacer(Modifier.height(14.dp))
        // Scan lives where people look for it: in the search box.
        OutlinedTextField(
            "", {}, placeholder = { Text("Search foods, drinks, restaurants") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { IconButton({}) { Icon(Icons.Rounded.QrCodeScanner, "Scan a barcode", tint = c.accent) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Eyebrow("Make it yourself")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Icons.Rounded.SoupKitchen to "Home-cooked", Icons.Rounded.LocalDrink to "Shake", Icons.Rounded.LocalCafe to "Chai",
                Icons.Rounded.BakeryDining to "Roti meal", Icons.Rounded.LocalPizza to "Pizza", Icons.Rounded.Egg to "Eggs",
                Icons.Rounded.LunchDining to "Sandwich", Icons.Rounded.RiceBowl to "Rice bowl",
            ).forEach { (icon, label) -> Builder(icon, label) }
        }
        Spacer(Modifier.height(18.dp))
        // One clear way into restaurants.
        PlateCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storefront, null, tint = c.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Restaurants", style = MaterialTheme.typography.titleMedium)
                    Text("Tim Hortons, McDonald's, A&W, Osmow's and more", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
            }
        }
        Spacer(Modifier.height(18.dp))
        Eyebrow("Food groups")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shelves.all.filter { !it.restaurants }.forEach { s -> AssistChip({}, { Text(s.title) }) }
        }
        Spacer(Modifier.height(20.dp))
        Eyebrow("Recent and your foods")
        Spacer(Modifier.height(8.dp))
        listOf("Banana" to "89", "Dal (lentil curry)" to "116").forEach { (n, k) ->
            PlateCard(Modifier.padding(bottom = 10.dp), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { MenuLine(n, k, unit = "/100 G") }
                    FilledTonalIconButton({}) { Icon(Icons.Rounded.Add, "Add $n") }
                }
            }
        }
        TextButton({}, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Not here? Create it") }
    }
}

@Composable
private fun Builder(icon: ImageVector, label: String) {
    val c = Plate.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.card), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

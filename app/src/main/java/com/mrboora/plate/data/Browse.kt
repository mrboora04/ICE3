package com.mrboora.plate.data

import android.content.Context

/** A browsable shelf: restaurant menus, or a group of foods from the Canadian Nutrient File. */
data class Shelf(val title: String, val cnfGroups: Set<String> = emptySet(), val restaurants: Boolean = false)

object Shelves {
    val all = listOf(
        Shelf("Restaurants", restaurants = true),
        Shelf("Drinks", setOf("14")),
        Shelf("Fast food", setOf("21")),
        Shelf("Snacks", setOf("25")),
        Shelf("Meals & dishes", setOf("22")),
        Shelf("Baked goods", setOf("18")),
        Shelf("Sweets", setOf("19")),
        Shelf("Fruit", setOf("9")),
        Shelf("Vegetables", setOf("11")),
        Shelf("Dairy & eggs", setOf("1")),
        Shelf("Meat & poultry", setOf("5", "7", "10", "13", "17")),
        Shelf("Fish & seafood", setOf("15")),
        Shelf("Beans & lentils", setOf("16")),
        Shelf("Nuts & seeds", setOf("12")),
        Shelf("Cereals", setOf("8")),
        Shelf("Grains & pasta", setOf("20")),
        Shelf("Soups & sauces", setOf("6")),
    )

    fun byTitle(title: String) = all.firstOrNull { it.title == title }
}

/**
 * Chain menu items from USDA FoodData Central (SR Legacy, public domain).
 * US recipes; Canadian menus can differ. Values per 100 g with the chain's serving.
 */
class RestaurantFoods(private val context: Context) {
    @Volatile private var foods: List<FoodItem>? = null

    private fun load(): List<FoodItem> = foods ?: synchronized(this) {
        foods ?: context.assets.open("restaurants.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull(::parseLine).toList()
        }.also { foods = it }
    }

    /** Chains with how many items each has, most items first. */
    fun chains(): List<Pair<String, Int>> =
        load().groupingBy { it.brand }.eachCount().toList().sortedWith(compareBy({ -it.second }, { it.first }))

    fun menu(chain: String): List<FoodItem> = load().filter { it.brand == chain }.sortedBy { it.name }

    fun search(query: String, limit: Int = 30): List<FoodItem> {
        val terms = query.lowercase().replace("'", "").split(' ', ',').filter { it.length > 1 }
        if (terms.isEmpty()) return emptyList()
        return load().filter { f ->
            val hay = "${f.brand} ${f.name}".lowercase().replace("'", "")
            terms.all { hay.contains(it) }
        }.take(limit)
    }

    companion object {
        fun parseLine(line: String): FoodItem? {
            val cols = splitCsv(line)
            if (cols.size < 8) return null
            return FoodItem(
                name = cols[1],
                brand = cols[0],
                kcal100 = cols[2].toFloatOrNull() ?: return null,
                protein100 = cols[3].toFloatOrNull() ?: 0f,
                carbs100 = cols[4].toFloatOrNull() ?: 0f,
                fat100 = cols[5].toFloatOrNull() ?: 0f,
                servingG = cols[6].toFloatOrNull() ?: 100f,
                servingLabel = cols[7],
                source = FoodItem.Source.BUILT_IN,
            )
        }
    }
}

/** Splits one CSV line, honouring double-quoted fields. */
fun splitCsv(line: String): List<String> {
    val cols = ArrayList<String>(8)
    val sb = StringBuilder()
    var quoted = false
    for (ch in line) {
        when {
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { cols += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
    }
    cols += sb.toString()
    return cols
}

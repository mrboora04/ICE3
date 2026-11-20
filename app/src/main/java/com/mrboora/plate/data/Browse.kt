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
 * One restaurant chain in Browse. [canadian] is true for Canadian menus; [estimated] when its
 * numbers are estimates (the restaurant publishes none), not official nutrition info.
 */
data class Chain(val name: String, val items: Int, val canadian: Boolean, val estimated: Boolean = false)

/**
 * Restaurant menus from two sources:
 * - canada_restaurants.csv: official Canadian nutrition info from each chain, values per item.
 * - restaurants.csv: USDA FoodData Central SR Legacy (public domain), US recipes, values per 100 g.
 * A chain with a Canadian menu uses only that; USDA items fill in chains without one.
 */
class RestaurantFoods(private val context: Context) {
    @Volatile private var foods: List<Pair<FoodItem, Boolean>>? = null

    private fun read(name: String, parse: (String) -> FoodItem?): List<FoodItem> =
        context.assets.open(name).bufferedReader().useLines { lines -> lines.mapNotNull(parse).toList() }

    private fun load(): List<Pair<FoodItem, Boolean>> = foods ?: synchronized(this) {
        foods ?: run {
            val downloaded = MenuUpdates.localFile(context)
            val canada = (if (downloaded.exists()) runCatching {
                downloaded.bufferedReader().useLines { l -> l.mapNotNull(::parseCanadianLine).toList() }
            }.getOrNull()?.takeIf { it.size >= 1000 } else null) ?: read("canada_restaurants.csv", ::parseCanadianLine)
            val canadianChains = canada.map { it.brand }.toSet()
            val us = read("restaurants.csv", ::parseLine).filter { it.brand !in canadianChains }
            canada.map { it to true } + us.map { it to false }
        }.also { foods = it }
    }

    /** Chains with Canadian menus first, then by number of items. */
    fun chains(): List<Chain> = load().groupBy { it.first.brand }
        .map { (name, items) -> Chain(name, items.size, items.first().second, items.all { it.first.isEstimate }) }
        .sortedWith(compareBy({ !it.canadian }, { it.estimated }, { -it.items }, { it.name }))

    fun names(): List<String> = load().map { "${it.first.brand} ${it.first.name}" }

    @Volatile private var byKey: Map<String, FoodItem>? = null

    /** The current menu entry for a saved food's key, so recents pick up corrected numbers. */
    fun find(key: String): FoodItem? =
        (byKey ?: load().associate { it.first.key to it.first }.also { byKey = it })[key]

    fun menu(chain: String): List<FoodItem> = load().map { it.first }.filter { it.brand == chain }

    fun search(query: String, limit: Int = 30): List<FoodItem> {
        val terms = query.lowercase().replace("'", "").split(' ', ',').filter { it.length > 1 }
        if (terms.isEmpty()) return emptyList()
        return load().asSequence().map { it.first }.filter { f ->
            val hay = "${f.brand} ${f.name}".lowercase().replace("'", "")
            terms.all { hay.contains(it) }
        }.take(limit).toList()
    }

    companion object {
        /** USDA row: chain,item,kcal100,p100,c100,f100,serving_g,serving_label */
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

        /**
         * Canadian row: chain,item,kcal,protein_g,carbs_g,fat_g,serving_g,serving_label,source_url[,basis]
         * (per item). basis "estimate" marks numbers estimated from the dish and serving, not published.
         */
        fun parseCanadianLine(line: String): FoodItem? {
            val cols = splitCsv(line)
            if (cols.size < 8) return null
            val grams = cols[6].toFloatOrNull()
            val label = cols[7].ifBlank { "1 item" }
            val kcal = cols[2].toFloatOrNull() ?: return null
            val p = cols[3].toFloatOrNull()
            val f = cols[5].toFloatOrNull()
            // A&W publishes calories, protein and fat but not carbs. Energy is about 4 kcal/g protein,
            // 4 kcal/g carbs and 9 kcal/g fat, so the carbs can be worked out from the rest.
            val carbs = cols[4].toFloatOrNull()
                ?: if (p != null && f != null) maxOf(0f, (kcal - 4f * p - 9f * f) / 4f) else null
            return FoodItem(
                name = cols[1],
                brand = cols[0],
                kcal100 = kcal,
                protein100 = p ?: 0f,
                carbs100 = carbs ?: 0f,
                fat100 = f ?: 0f,
                servingG = 1f,
                servingLabel = if (grams != null) "$label, ${grams.toInt()} g" else label,
                source = FoodItem.Source.BUILT_IN,
                perItem = true,
                unknown = buildString {
                    if (p == null) append('p')
                    if (carbs == null) append('c')
                    if (f == null) append('f')
                },
                estimated = when {
                    cols.getOrNull(9)?.trim()?.equals("estimate", ignoreCase = true) == true -> "kpcf"
                    cols[4].isBlank() && carbs != null -> "c"
                    else -> ""
                },
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

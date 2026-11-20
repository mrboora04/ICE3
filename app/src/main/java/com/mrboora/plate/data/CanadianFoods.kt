package com.mrboora.plate.data

import android.content.Context

/**
 * Health Canada's Canadian Nutrient File (2026), bundled as a compact CSV in assets.
 * Contains information licensed under the Open Government Licence – Canada.
 * Values are per 100 g; the serving is the food's first household measure.
 */
class CanadianFoods(private val context: Context) {
    @Volatile private var entries: List<Pair<FoodItem, String>>? = null

    private fun load(): List<Pair<FoodItem, String>> = entries ?: synchronized(this) {
        entries ?: context.assets.open("cnf_foods.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull(::parseEntry).toList()
        }.also { entries = it }
    }

    fun names(): List<String> = load().map { it.first.name }

    fun search(query: String, limit: Int = 40): List<FoodItem> = rank(load().map { it.first }, query, limit)

    /** All foods in the given food groups, alphabetically. */
    fun inGroups(groups: Set<String>): List<FoodItem> =
        load().filter { it.second in groups }.map { it.first }.sortedBy { it.name }

    companion object {
        fun parseEntry(line: String): Pair<FoodItem, String>? {
            val cols = splitCsv(line)
            if (cols.size < 7) return null
            val item = FoodItem(
                name = cols[0],
                kcal100 = cols[1].toFloatOrNull() ?: return null,
                protein100 = cols[2].toFloatOrNull() ?: 0f,
                carbs100 = cols[3].toFloatOrNull() ?: 0f,
                fat100 = cols[4].toFloatOrNull() ?: 0f,
                servingG = cols[5].toFloatOrNull() ?: 100f,
                servingLabel = cols[6],
                source = FoodItem.Source.BUILT_IN,
            )
            return item to cols.getOrElse(7) { "" }
        }

        fun parseLine(line: String): FoodItem? = parseEntry(line)?.first

        /**
         * Every query word must appear. Foods whose name starts with the first word rank first,
         * then raw/plain foods, then shorter (more general) names.
         */
        fun rank(foods: List<FoodItem>, query: String, limit: Int): List<FoodItem> {
            val terms = query.lowercase().split(' ', ',').filter { it.length > 1 }
            if (terms.isEmpty()) return emptyList()
            return foods.asSequence()
                .map { it to it.name.lowercase() }
                .filter { (_, n) -> terms.all { n.contains(it) } }
                .sortedWith(
                    compareBy<Pair<FoodItem, String>> { (_, n) -> if (n.startsWith(terms[0])) 0 else 1 }
                        .thenBy { (_, n) -> if ("raw" in n || "plain" in n) 0 else 1 }
                        .thenBy { (_, n) -> n.length }
                )
                .take(limit)
                .map { it.first }
                .toList()
        }
    }
}

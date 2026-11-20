package com.mrboora.plate.data

import android.content.Context

/**
 * Health Canada's Canadian Nutrient File (2026), bundled as a compact CSV in assets.
 * Contains information licensed under the Open Government Licence – Canada.
 * Values are per 100 g; the serving is the food's first household measure.
 */
class CanadianFoods(private val context: Context) {
    @Volatile private var foods: List<FoodItem>? = null

    private fun load(): List<FoodItem> = foods ?: synchronized(this) {
        foods ?: context.assets.open("cnf_foods.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull(::parseLine).toList()
        }.also { foods = it }
    }

    fun search(query: String, limit: Int = 40): List<FoodItem> = rank(load(), query, limit)

    companion object {
        /** Splits one CSV line, honouring double-quoted fields. */
        fun parseLine(line: String): FoodItem? {
            val cols = ArrayList<String>(7)
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
            if (cols.size < 7) return null
            return FoodItem(
                name = cols[0],
                kcal100 = cols[1].toFloatOrNull() ?: return null,
                protein100 = cols[2].toFloatOrNull() ?: 0f,
                carbs100 = cols[3].toFloatOrNull() ?: 0f,
                fat100 = cols[4].toFloatOrNull() ?: 0f,
                servingG = cols[5].toFloatOrNull() ?: 100f,
                servingLabel = cols[6],
                source = FoodItem.Source.BUILT_IN,
            )
        }

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

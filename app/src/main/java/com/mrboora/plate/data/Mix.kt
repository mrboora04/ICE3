package com.mrboora.plate.data

import kotlin.math.roundToInt

/**
 * Make-it-yourself: a homemade drink or dish is just ingredients and amounts. Nothing is stored
 * per recipe; every number is arithmetic on per-100 g values Plate already has (Health Canada's
 * Canadian Nutrient File):
 *
 *     ingredient grams ÷ 100 × per-100 g value = that ingredient's share
 *     sum of shares = the whole batch;  batch ÷ glasses = one glass
 *
 * The result is saved as one of "your foods", counted per glass and marked ≈ (homemade,
 * no official label). The shake builder is the first template; the same engine works for any
 * homemade mix.
 */
object Mix {

    data class Part(val food: FoodItem, val grams: Float) {
        val kcal get() = food.kcalFor(grams)
        val protein get() = food.proteinFor(grams)
        val carbs get() = food.carbsFor(grams)
        val fat get() = food.fatFor(grams)
    }

    data class Totals(val grams: Float, val kcal: Float, val protein: Float, val carbs: Float, val fat: Float)

    fun totals(parts: List<Part>): Totals = Totals(
        parts.sumOf { it.grams.toDouble() }.toFloat(),
        parts.sumOf { it.kcal.toDouble() }.toFloat(),
        parts.sumOf { it.protein.toDouble() }.toFloat(),
        parts.sumOf { it.carbs.toDouble() }.toFloat(),
        parts.sumOf { it.fat.toDouble() }.toFloat(),
    )

    /** One glass (or serving) of the batch, as a food you can log again later. */
    fun build(name: String, parts: List<Part>, servings: Int, unit: String = "glass"): FoodItem {
        val t = totals(parts)
        val n = servings.coerceAtLeast(1)
        return FoodItem(
            name = name.trim(),
            brand = "Homemade",
            kcal100 = t.kcal / n, protein100 = t.protein / n, carbs100 = t.carbs / n, fat100 = t.fat / n,
            servingG = (t.grams / n).roundToInt().toFloat(),
            servingLabel = "1 $unit",
            source = FoodItem.Source.CUSTOM,
            perItem = true,
            estimated = "k",
        )
    }

    /** "Banana & mixed berry shake". */
    fun shakeName(fruits: List<String>): String {
        fun singular(w: String) = when {
            w.endsWith("ies") -> w.dropLast(3) + "y"     // berries → berry
            w.endsWith("oes") -> w.dropLast(2)           // mangoes → mango
            w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }
        val short = fruits.map { singular(it.lowercase()) }.distinct()
        if (short.isEmpty()) return "Homemade shake"
        val joined = if (short.size <= 2) short.joinToString(" & ") else short.dropLast(1).joinToString(", ") + " & " + short.last()
        return joined.replaceFirstChar { it.uppercase() } + " shake"
    }

    /** Grams in [ml] of a liquid base, from that food's own Health Canada density (milk: 257.8 g per 250 ml). */
    fun mlToGrams(food: FoodItem, ml: Float): Float = ml * Portions.density(food).first

    // ---- Shake template ---------------------------------------------------------------------

    /** Fruit chips: label → exact Health Canada name. */
    val FRUITS = listOf(
        "Banana" to "Banana, raw",
        "Mixed berries" to MIXED_BERRIES,
        "Strawberries" to "Strawberry, raw",
        "Blueberries" to "Blueberry, raw",
        "Raspberries" to "Raspberry, raw",
        "Mango" to "Mango, raw",
        "Pineapple" to "Pineapple, raw",
        "Peach" to "Peach, raw",
        "Apple" to "Apple, raw, with skin",
        "Kiwi" to "Kiwi fruit, raw",
        "Papaya" to "Papaya, raw",
        "Avocado" to "Avocado, raw, all commercial varieties",
        "Dates" to "Date, medjool",
        "Spinach" to "Spinach, raw",
    )

    /** Extras people often blend in. */
    val EXTRAS = listOf(
        "Peanut butter" to "Peanut butter, smooth type, fat and sugar added",
        "Oats" to "Cereal, hot, oats (oatmeal), instant: regular, dry",
        "Chia seeds" to "Seeds, chia seeds, dried",
    )

    /** Base chips: label → exact Health Canada name (water is built in: zero everything). */
    val BASES = listOf(
        "Milk 3.25%" to "Milk, fluid, whole, pasteurized, homogenized, 3.25% M.F.",
        "Milk 2%" to "Milk, fluid, partly skimmed, 2% M.F.",
        "Yogurt" to "Yogourt (yogurt), 2-3.9% M.F., plain",
        "Greek yogurt" to "Yogourt (yogurt), Greek style, fat free, 0-0.5% M.F., plain",
        "Oat drink" to "Plant-based beverage, oat and quinoa beverage, unsweetened, fortified, shelf-stable",
        "Soy drink" to "Plant-based beverage, soy beverage, plain, unsweetened, fortified, refrigerated and/or shelf-stable",
        "Almond drink" to "Plant-based beverage, almond beverage, plain, unsweetened, calcium-fortified, shelf-stable",
        "Water" to WATER,
    )

    const val MIXED_BERRIES = "Mixed berries"
    const val WATER = "Water"
    val BERRY_PARTS = listOf("Blueberry, raw", "Raspberry, raw", "Strawberry, raw")

    val water = FoodItem(WATER, kcal100 = 0f, protein100 = 0f, carbs100 = 0f, fat100 = 0f, servingG = 250f, servingLabel = "250 ml")

    /** Mixed berries: equal parts blueberry, raspberry and strawberry, so the average of the three. */
    fun mixedBerries(berries: List<FoodItem>): FoodItem? {
        if (berries.isEmpty()) return null
        fun avg(f: (FoodItem) -> Float) = berries.map(f).average().toFloat()
        return FoodItem(
            MIXED_BERRIES, kcal100 = avg { it.kcal100 }, protein100 = avg { it.protein100 },
            carbs100 = avg { it.carbs100 }, fat100 = avg { it.fat100 }, servingG = 150f, servingLabel = "250 ml",
        )
    }

    /** Quick amounts for a fruit: its own natural unit (1 medium banana = 118 g) or cups. */
    fun quickAmounts(food: FoodItem): List<Pair<String, Float>> {
        val label = food.servingLabel
        val byMl = Regex("""\bml\b""", RegexOption.IGNORE_CASE).containsMatchIn(label)
        // Rich foods (peanut butter, oats, seeds, honey) go in by the spoon, not the cup.
        if (food.kcal100 >= 300f && !food.perItem) {
            val d = Portions.density(food).first
            return listOf("1 tsp" to 5f * d, "1 tbsp" to 15f * d, "2 tbsp" to 30f * d)
        }
        return if (!byMl && label.isNotBlank() && food.servingG > 0f) {
            val unit = label.substringBefore('(').trim().removePrefix("1 ").ifBlank { "piece" }
            (1..3).map { n -> "$n $unit" to food.servingG * n }
        } else {
            val cup = Portions.kitchen(food).firstOrNull { it.name == "1 cup" }?.grams ?: 150f
            listOf("30 g" to 30f, "½ cup" to cup / 2f, "1 cup" to cup)
        }
    }

    /** True when a request is for a homemade shake or smoothie, which the builder makes. */
    fun isShake(text: String): Boolean = Regex("""\b(shake|smoothie|smoothy|milkshake|lassi)s?\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

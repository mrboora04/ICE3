package com.mrboora.plate.data

import kotlin.math.roundToInt

/**
 * Turns household measures (spoons, cups, glasses) and the hand guide (fist, palm, cupped hand,
 * thumb) into grams for a particular food.
 *
 * Volume needs the food's density. Health Canada lists about 2,500 foods by millilitres
 * ("250 ml", "125 ml chopped"), so for those the density is exact: serving grams ÷ serving ml.
 * Everything else uses a typical density for its kind of food and is shown as an estimate.
 * Cups are Canada's metric cup, 250 ml.
 */
object Portions {

    data class Portion(
        /** Short name, e.g. "1 tbsp" or "Fist". */
        val name: String,
        /** What it stands for, e.g. "1 cup", shown under hand measures; blank otherwise. */
        val means: String,
        val grams: Float,
        /** False when the grams come from a typical density, not this food's own listing. */
        val exact: Boolean,
        /** "g", or "ml" for drinks (Plate counts drinks 1 ml = 1 g, like the amount box). */
        val unit: String = "g",
    )

    private val mlRegex = Regex("""(\d+(?:\.\d+)?)\s*ml\b""", RegexOption.IGNORE_CASE)
    private val prepWords = listOf("chopped", "diced", "slice", "cube", "piece", "shredded", "halves", "grated", "mashed", "sections")

    /** Grams per millilitre, and whether it came from the food's own listing. */
    fun density(food: FoodItem): Pair<Float, Boolean> {
        if (!food.perItem && food.servingG > 0f) {
            mlRegex.find(food.servingLabel)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it > 0f }?.let { ml ->
                val d = food.servingG / ml
                if (d in 0.03f..2.5f) return d to true
            }
            // Kitchen labels count too: "2 tbsp" = 30 ml, "1 tsp" = 5 ml, "1 cup" = 250 ml.
            kitchenRegex.find(food.servingLabel.lowercase())?.let { m ->
                val n = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return@let
                val per = when {
                    m.groupValues[2].startsWith("tbsp") || m.groupValues[2].startsWith("tablespoon") -> 15f
                    m.groupValues[2].startsWith("tsp") || m.groupValues[2].startsWith("teaspoon") -> 5f
                    else -> 250f
                }
                val d = food.servingG / (n * per)
                if (d in 0.03f..2.5f) return d to true
            }
        }
        return typical(food.name.lowercase()) to false
    }

    private val kitchenRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(tbsp|tablespoons?|tsp|teaspoons?|cups?)\b""")

    private fun has(name: String, vararg words: String) =
        words.any { Regex("""\b${Regex.escape(it)}(e?s)?\b""").containsMatchIn(name) }

    private fun typical(n: String): Float = when {
        has(n, "oil", "ghee") -> 0.92f
        // Nut butters are dense pastes, not loose nuts: about 1.08 g/ml.
        has(n, "peanut butter", "almond butter", "cashew butter", "nut butter", "tahini") -> 1.08f
        has(n, "butter", "margarine", "shortening") && !has(n, "peanut butter", "nut butter") -> 0.95f
        has(n, "honey", "syrup", "molasses", "jam", "jelly", "marmalade") -> 1.4f
        has(n, "sugar") -> 0.85f
        has(n, "flour", "besan", "atta", "cornstarch") -> 0.55f
        has(n, "salt") -> 1.2f
        has(n, "almond", "cashew", "peanut", "pistachio", "walnut", "pecan", "nut", "seed") -> 0.6f
        has(n, "lettuce", "spinach", "greens", "kale", "arugula", "salad") -> 0.25f
        has(n, "cereal", "flakes", "puffed", "granola", "oats") -> 0.35f
        has(n, "rice", "quinoa", "pasta", "noodle", "spaghetti", "macaroni", "couscous", "poha", "upma") -> 0.75f
        isDrinkName(n) -> 1.03f
        has(n, "dal", "lentil", "curry", "soup", "stew", "chili", "yogurt", "yoghurt", "sauce", "gravy", "raita", "dahi") -> 1.0f
        else -> 0.8f
    }

    private fun isDrinkName(n: String) = has(
        n, "beverage", "drink", "juice", "tea", "coffee", "latte", "cappuccino", "espresso", "water", "soda", "cola", "pop",
        "lemonade", "smoothie", "kefir", "lassi", "shake", "beer", "wine", "whisky", "whiskey", "vodka", "rum", "gin",
        "tequila", "brandy", "liqueur", "spirits", "cider", "chai",
    ) || (has(n, "milk") && !has(n, "milk chocolate", "powder", "condensed"))

    /** True for things you pour into a glass. */
    fun isDrink(food: FoodItem): Boolean {
        if (food.perItem) return false
        val n = food.name.lowercase()
        if (!isDrinkName(n)) return false
        return prepWords.none { it in food.servingLabel.lowercase() }
    }

    private fun spirit(n: String) = has(n, "whisky", "whiskey", "vodka", "rum", "gin", "tequila", "brandy", "liqueur", "spirits")

    private fun grams(ml: Float, d: Float): Float {
        val g = ml * d
        return if (g < 10f) (g * 10f).roundToInt() / 10f else g.roundToInt().toFloat()
    }

    /** Spoons and cups, or glasses and cans for drinks. Empty for foods counted by the item. */
    fun kitchen(food: FoodItem): List<Portion> {
        if (food.perItem) return emptyList()
        val (d, exact) = density(food)
        val n = food.name.lowercase()
        val list = if (isDrink(food)) buildList {
            if (spirit(n)) add("1 shot" to 43f)
            if (has(n, "wine")) add("1 wine glass" to 150f)
            if (has(n, "beer", "cider")) add("1 bottle" to 341f)
            add("1 glass" to 250f)
            add("1 can" to 355f)
            if (!spirit(n)) add("½ L bottle" to 500f)
        } else listOf("1 tsp" to 5f, "1 tbsp" to 15f, "¼ cup" to 62.5f, "½ cup" to 125f, "1 cup" to 250f)
        if (isDrink(food)) return list.map { (name, ml) -> Portion(name, "", ml, true, "ml") }
        return list.map { (name, ml) -> Portion(name, "", grams(ml, d), exact) }
    }

    private val MEASURES = setOf(
        "ml", "l", "cup", "cups", "tbsp", "tsp", "tablespoon", "teaspoon", "g", "kg", "oz", "bowl", "katori",
        "glass", "serving", "portion", "handful", "scoop", "can", "bottle", "container", "small container",
        "packet", "package", "pouch", "food guide serving", "order", "plate",
    )

    /**
     * The unit a food is counted in, from its own serving ("1 large" → "large", "1 roti" → "roti"),
     * or null when it's measured (cups, ml, grams, a bowl). Counted foods get "How many?" instead of
     * cups and the hand guide: 4 eggs are 4 eggs.
     */
    fun countUnit(food: FoodItem): String? {
        if (food.perItem || food.servingG <= 0f) return null
        val l = food.servingLabel.trim().lowercase()
        val m = Regex("""^(1|one|a)\s+(.+)$""").find(l) ?: return null
        val unit = m.groupValues[2].substringBefore('(').substringBefore('=').substringBefore(',').trim()
        if (unit.isBlank() || unit in MEASURES || MEASURES.any { unit.startsWith("$it ") } ||
            Regex("""\b(ml|cups?|tbsp|tsp|g|oz|bowl|serving)\b""").containsMatchIn(unit)
        ) return null
        return unit
    }

    /** Hand guide: no scales or cups needed. Empty for drinks and per-item foods. */
    fun hand(food: FoodItem): List<Portion> {
        if (food.perItem || isDrink(food)) return emptyList()
        val (d, exact) = density(food)
        return listOf(
            Portion("Thumb", "1 tbsp", grams(15f, d), exact),
            Portion("Cupped hand", "½ cup", grams(125f, d), exact),
            Portion("Fist", "1 cup", grams(250f, d), exact),
            // A palm-sized piece (size and thickness of your palm) of meat, fish, paneer or tofu.
            Portion("Palm", "a piece", 100f, false),
        )
    }
}

package com.mrboora.plate.data

import kotlin.math.roundToInt

/**
 * Reads a homemade mix written in plain English, offline, with simple rules:
 *
 *   "1 banana with around 30g 4 berries-blend with a regular spoon of peanut butter
 *    over with 3.25% milk around 400ml"
 *
 * → banana 1 medium (118 g) · mixed berries 30 g · peanut butter 1 tbsp (16 g) · milk 3.25% 400 ml.
 *
 * Rules:
 * - The text is split into pieces at "with", "and", "over", "plus", commas and "+".
 * - In each piece, a number with a unit is the amount. **g / kg** is weight; **ml / l / cup /
 *   glass / spoon / scoop / handful** is volume, turned into grams with that food's own density.
 *   A bare number ("1 banana", "2 dates") counts the food's natural unit (1 medium banana = 118 g).
 * - Spoons: teaspoon / small spoon 5 ml, spoon / regular spoon / tablespoon 15 ml.
 *   Scoops differ, so: protein powder scoop 30 g, other scoops 2 tbsp (30 ml); "small" × 0.75,
 *   "big / heaped" × 1.5. Every conversion is written out so it can be checked and changed.
 * - A number followed by % is a fat level ("3.25% milk"), not an amount.
 * - The rest of the piece names the food, matched to the builder's ingredients, else searched.
 */
object MixParser {

    data class Item(
        /** Builder label, e.g. "Banana" or "Milk 3.25%". */
        val label: String,
        val food: FoodItem,
        val grams: Float,
        /** For a base: the ml it was given in. */
        val ml: Float?,
        val isBase: Boolean,
        /** The reasoning, in English: "1 regular spoon = 15 ml × 1.08 g/ml = 16 g". */
        val how: String,
    )

    data class Draft(val items: List<Item>, val unknown: List<String>) {
        /** The base is the liquid there's most of; any other milk, yogurt or drink is an ingredient. */
        val base: Item? get() = items.filter { it.isBase }.maxByOrNull { it.ml ?: 0f }
        val parts: List<Item> get() = items.filter { it !== base }
    }

    // ---- Vocabulary ------------------------------------------------------------------------

    /** Phrase → builder label. Longer phrases are tried first ("oat milk" before "oat"). */
    private val ALIASES: List<Pair<String, String>> = listOf(
        "banana" to "Banana", "bananas" to "Banana",
        "mixed berries" to "Mixed berries", "berry blend" to "Mixed berries", "berries blend" to "Mixed berries",
        "berry mix" to "Mixed berries", "berries" to "Mixed berries", "berry" to "Mixed berries",
        "strawberries" to "Strawberries", "strawberry" to "Strawberries",
        "blueberries" to "Blueberries", "blueberry" to "Blueberries",
        "raspberries" to "Raspberries", "raspberry" to "Raspberries",
        "mangoes" to "Mango", "mango" to "Mango", "pineapple" to "Pineapple", "peaches" to "Peach", "peach" to "Peach",
        "apples" to "Apple", "apple" to "Apple", "kiwis" to "Kiwi", "kiwi" to "Kiwi", "papaya" to "Papaya",
        "avocado" to "Avocado", "dates" to "Dates", "date" to "Dates", "spinach" to "Spinach",
        "peanut butter" to "Peanut butter", "pb" to "Peanut butter",
        "chia seeds" to "Chia seeds", "chia" to "Chia seeds",
        "oat milk" to "Oat drink", "oat drink" to "Oat drink", "oat beverage" to "Oat drink",
        "soy milk" to "Soy drink", "soya milk" to "Soy drink", "soy drink" to "Soy drink",
        "almond milk" to "Almond drink", "almond drink" to "Almond drink",
        "oatmeal" to "Oats", "oats" to "Oats", "oat" to "Oats",
        "greek yogurt" to "Greek yogurt", "greek yoghurt" to "Greek yogurt",
        "yogurt" to "Yogurt", "yoghurt" to "Yogurt", "yogourt" to "Yogurt", "curd" to "Yogurt", "dahi" to "Yogurt",
        "whole milk" to "Milk 3.25%", "homo milk" to "Milk 3.25%", "milk" to "Milk",
        "water" to "Water",
    ).sortedByDescending { it.first.length }

    /** Common slips, fixed before matching. */
    private val TYPOS = mapOf(
        "barries" to "berries", "berrys" to "berries", "beries" to "berries", "berris" to "berries",
        "bananna" to "banana", "banan" to "banana", "bannana" to "banana", "bananaa" to "banana",
        "penut" to "peanut", "peanutbutter" to "peanut butter", "pnut" to "peanut",
        "yougurt" to "yogurt", "yogart" to "yogurt", "mlk" to "milk", "millk" to "milk",
        "strawbery" to "strawberry", "bluebery" to "blueberry", "mangos" to "mangoes",
    )

    private val NUMBER_WORDS = mapOf(
        "a" to 1f, "an" to 1f, "one" to 1f, "two" to 2f, "three" to 3f, "four" to 4f, "five" to 5f,
        "six" to 6f, "seven" to 7f, "eight" to 8f, "nine" to 9f, "ten" to 10f, "half" to 0.5f,
        "½" to 0.5f, "¼" to 0.25f, "¾" to 0.75f,
    )

    private val SPLIT = Regex("""\s*(?:,|\+|&|;|\bwith\b|\band\b|\bover\b|\bplus\b|\bthen\b|\btopped\b)\s*""")
    private const val NUM = """(\d+(?:[.,]\d+)?|½|¼|¾|half|an|a|one|two|three|four|five|six|seven|eight|nine|ten)"""
    private const val SIZE = """(?:(small|little|regular|normal|medium|big|large|heaped|heaping|level|full)\s+)?"""
    private const val UNIT =
        """(kg|kgs|g|gm|gms|grams?|ml|mls|millilit(?:re|er)s?|l|lit(?:re|er)s?|cups?|glass(?:es)?|tbsp|tablespoons?|tsp|teaspoons?|spoons?|scoops?|handfuls?)"""
    private val WITH_UNIT = Regex("""(?<![\w.])$NUM\s*$SIZE$UNIT\b""")
    private val BARE = Regex("""(?<![\w.%])$NUM\b(?!\s*%)""")
    private val FAT = Regex("""(\d+(?:[.,]\d+)?)\s*%|\b3\s25\b""")
    private val STOP = setOf(
        "around", "about", "approx", "approximately", "roughly", "some", "of", "the", "blend", "blended", "mix",
        "fresh", "frozen", "cold", "in", "on", "it", "my", "regular", "normal", "little", "bit", "plain", "fat",
    )

    /** True when the text reads like something made from ingredients, not one food's name. */
    fun looksLikeMix(text: String): Boolean {
        val t = clean(text)
        if (t.split(' ').size < 4) return false
        val pieces = t.split(SPLIT).filter { it.isNotBlank() }
        return pieces.size >= 2 && (WITH_UNIT.containsMatchIn(t) || BARE.containsMatchIn(t))
    }

    private fun clean(text: String): String {
        var t = text.lowercase().replace('-', ' ').replace(Regex("""\s+"""), " ").trim()
        TYPOS.forEach { (k, v) -> t = t.replace(Regex("""\b$k\b"""), v) }
        // "30g" → "30 g"
        return t.replace(Regex("""(\d)([a-z])""")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
    }

    private fun number(s: String): Float? = NUMBER_WORDS[s] ?: s.replace(',', '.').toFloatOrNull()

    private fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) "${v.roundToInt()}" else "%.1f".format(v)

    /**
     * Reads [text]. [foods] maps builder ingredient keys (as in [Mix]) to foods; [search] looks up
     * anything else in Health Canada's file.
     */
    fun parse(text: String, foods: Map<String, FoodItem>, search: (String) -> FoodItem?): Draft {
        val labels = (Mix.FRUITS + Mix.EXTRAS + Mix.BASES).toMap()
        val baseLabels = Mix.BASES.map { it.first }.toSet()
        val items = mutableListOf<Item>()
        val unknown = mutableListOf<String>()

        for (raw in clean(text).split(SPLIT)) {
            var piece = raw.trim()
            if (piece.isBlank()) continue

            // Fat level: "3.25% milk", "2% milk", "3 25 milk".
            val fat = FAT.find(piece)?.let { m -> if (m.value.startsWith("3 25")) 3.25f else number(m.groupValues[1]) }
            piece = FAT.replace(piece, " ")

            // Amount: a number with a unit wins; else a bare count.
            val unitMatch = WITH_UNIT.find(piece)
            val qty = unitMatch?.groupValues?.get(1)?.let(::number)
            val size = unitMatch?.groupValues?.get(2).orEmpty()
            val unit = unitMatch?.groupValues?.get(3).orEmpty()
            if (unitMatch != null) piece = piece.removeRange(unitMatch.range)
            var count: Float? = null
            if (unitMatch == null) {
                BARE.find(piece)?.let { m -> count = number(m.groupValues[1]); piece = piece.removeRange(m.range) }
            }

            val words = piece.split(' ').filter { it.isNotBlank() && it !in STOP && it.toFloatOrNull() == null }
            val name = words.joinToString(" ")
            if (name.isBlank()) continue

            // Which food.
            var label = ALIASES.firstOrNull { (alias, _) -> Regex("""\b$alias\b""").containsMatchIn(name) }?.second
            if (label == "Milk") label = when {
                fat == null -> "Milk 2%"
                fat >= 3f -> "Milk 3.25%"
                else -> "Milk 2%"
            }
            val food = label?.let { labels[it] }?.let { foods[it] } ?: search(name)?.also { label = it.name.substringBefore(',').trim() }
            if (food == null || label == null) {
                unknown += raw.trim()
                continue
            }
            val isBase = label in baseLabels

            // Amount → grams, with the reasoning written out.
            val density = if (label == "Water") 1f else Mix.mlToGrams(food, 1f)
            fun fromMl(ml: Float, said: String): Pair<Float, String> {
                val gr = ml * density
                return gr to (if (said.isEmpty()) "" else "$said = ") + "${fmt(ml)} ml × ${"%.2f".format(density)} g/ml = ${fmt(gr.roundToInt().toFloat())} g"
            }
            val sizeFactor = when (size) {
                "small", "little" -> 0.75f
                "big", "large", "heaped", "heaping", "full" -> 1.5f
                else -> 1f
            }
            val (grams, how) = when {
                qty != null && unit.startsWith("k") -> (qty * 1000f) to "${fmt(qty)} kg = ${fmt(qty * 1000f)} g"
                qty != null && (unit == "g" || unit.startsWith("gm") || unit.startsWith("gram")) -> qty to "${fmt(qty)} g, weighed"
                qty != null && (unit.startsWith("ml") || unit.startsWith("milli")) -> fromMl(qty, "")
                qty != null && (unit == "l" || unit.startsWith("lit")) -> fromMl(qty * 1000f, "${fmt(qty)} L")
                qty != null && unit.startsWith("cup") -> fromMl(qty * 250f, "${fmt(qty)} cup (250 ml each)")
                qty != null && unit.startsWith("glass") -> fromMl(qty * 250f, "${fmt(qty)} glass (250 ml each)")
                qty != null && (unit == "tsp" || unit.startsWith("teaspoon") || (unit.startsWith("spoon") && size in setOf("small", "little"))) ->
                    fromMl(qty * 5f, "${fmt(qty)} teaspoon (5 ml)")
                qty != null && (unit == "tbsp" || unit.startsWith("tablespoon") || unit.startsWith("spoon")) ->
                    fromMl(qty * 15f * (if (size in setOf("big", "large", "heaped", "heaping", "full")) 1.5f else 1f),
                        "${fmt(qty)} ${size.ifEmpty { "regular" }} spoon (tablespoon, 15 ml${if (sizeFactor > 1f) " × 1.5 heaped" else ""})")
                qty != null && unit.startsWith("scoop") && Regex("""protein|whey""").containsMatchIn(name) ->
                    (qty * 30f * sizeFactor) to "${fmt(qty)} ${size.ifEmpty { "regular" }} scoop of protein ≈ ${fmt(qty * 30f * sizeFactor)} g (most scoops are 30 g; check yours)"
                qty != null && unit.startsWith("scoop") ->
                    fromMl(qty * 30f * sizeFactor, "${fmt(qty)} ${size.ifEmpty { "regular" }} scoop (≈ 2 tbsp${if (sizeFactor != 1f) " × ${fmt(sizeFactor)}" else ""}; scoops vary)")
                qty != null && unit.startsWith("handful") -> fromMl(qty * 125f, "${fmt(qty)} handful (cupped hand ≈ ½ cup)")
                count != null && isBase -> fromMl(count!! * 250f, "${fmt(count!!)} glass (250 ml each)")
                count != null -> {
                    val each = Mix.quickAmounts(food).first()
                    val unitName = each.first.removePrefix("1 ").removePrefix("30 g").ifBlank { "serving" }
                    (count!! * each.second) to "${fmt(count!!)} × $unitName (${fmt(each.second.roundToInt().toFloat())} g each) = ${fmt((count!! * each.second).roundToInt().toFloat())} g"
                }
                isBase -> fromMl(250f, "no amount given, so 1 glass")
                else -> {
                    val each = Mix.quickAmounts(food).first()
                    each.second to "no amount given, so ${each.first} = ${fmt(each.second.roundToInt().toFloat())} g"
                }
            }
            val ml = if (isBase) grams / density else null
            items += Item(label!!, food, grams, ml, isBase, how)
        }
        return Draft(items, unknown)
    }
}

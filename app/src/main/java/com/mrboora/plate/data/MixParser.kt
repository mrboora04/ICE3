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
        /** The word it was read from ("milk"), so a correction can be remembered for it. */
        val said: String = "",
        /** True when the user's own remembered reading was used. */
        val usual: Boolean = false,
    )

    /** A dish such as a pizza: its size, and how many slices it's cut into. */
    data class Dish(val size: Mix.Size, val crust: Mix.Crust = Mix.CRUSTS.first())

    data class Draft(
        val items: List<Item>,
        val unknown: List<String>,
        val dish: Dish? = null,
        /** A chai, roti meal, sandwich, eggs or rice bowl template, when the text asked for one. */
        val template: Templates.Template? = null,
        /** The template size that was typed ("Big" for "large coffee"), if any. */
        val sizeLabel: String? = null,
        /** The dish word as typed ("sabzi", "stew"), for its name. */
        val word: String? = null,
    ) {
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
        // Toppings.
        "paneer" to "Paneer", "cottage cheese" to "Paneer",
        "onions" to "Onion", "onion" to "Onion",
        "green peppers" to "Green pepper", "green pepper" to "Green pepper", "bell pepper" to "Green pepper",
        "capsicum" to "Green pepper", "peppers" to "Green pepper",
        "mushrooms" to "Mushrooms", "mushroom" to "Mushrooms",
        "tomatoes" to "Tomato", "tomato" to "Tomato", "corn" to "Corn",
        "chicken" to "Chicken", "extra cheese" to "Extra cheese", "mozzarella" to "Extra cheese",
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
    private val PIZZA = Regex("""\bpizzas?\b""")
    private val INCH = Regex("""(\d{1,2})\s*(?:"|inch(?:es)?\b|in\b)""")

    /** Pizza in the text, and its size: a size word, inches, or medium if none is given. */
    fun pizzaSize(text: String): Mix.Size? {
        val t = clean(text)
        if (!PIZZA.containsMatchIn(t)) return null
        INCH.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { inches ->
            return Mix.PIZZA_SIZES.filter { it.label != "Party" }.minBy { s ->
                kotlin.math.abs((s.words.firstNotNullOfOrNull { w -> Regex("""(\d+) inch""").find(w)?.groupValues?.get(1)?.toInt() } ?: 0) - inches)
            }
        }
        // Longest words first, so "extra large" isn't read as "large".
        val byWord = Mix.PIZZA_SIZES.flatMap { s -> s.words.map { it to s } }.sortedByDescending { it.first.length }
        return byWord.firstOrNull { (w, _) -> Regex("""\b$w\b""").containsMatchIn(t) }?.second ?: Mix.PIZZA_DEFAULT
    }

    /** Thin, thick or regular crust; regular if not said. */
    fun pizzaCrust(text: String): Mix.Crust {
        val t = clean(text)
        val byWord = Mix.CRUSTS.flatMap { c -> c.words.map { it to c } }.sortedByDescending { it.first.length }
        return byWord.firstOrNull { (w, _) -> Regex("""\b${Regex.escape(w)}\b""").containsMatchIn(t) }?.second ?: Mix.CRUSTS.first()
    }

    private val STOP = setOf(
        "topping", "toppings", "size", "slices", "slice", "topped",
        "around", "about", "approx", "approximately", "roughly", "some", "of", "the", "blend", "blended", "mix",
        "fresh", "frozen", "cold", "in", "on", "it", "my", "regular", "normal", "little", "bit", "plain", "fat",
    )

    /** The food words of a search, without the amount: "peanut butter two spoon" → "peanut butter". */
    fun foodWords(text: String): String {
        var t = clean(text)
        t = WITH_UNIT.replace(t, " ")
        t = BARE.replace(t, " ")
        return t.split(' ').filter { it.isNotBlank() && it !in STOP && it.toFloatOrNull() == null && it !in setOf("of", "x", "×") }
            .joinToString(" ")
    }

    /** True when a search also says how much ("two spoon", "4 eggs", "100 g"). */
    fun hasAmount(text: String): Boolean {
        val t = clean(text)
        return WITH_UNIT.containsMatchIn(t) || Regex("""(?<![\w.%])(\d+(?:[.,]\d+)?|half|one|two|three|four|five|six|seven|eight|nine|ten)\b(?!\s*%)""").containsMatchIn(t)
    }

    /**
     * How much of [food] a search asks for, in grams (or a count for per-item foods), with the
     * reasoning: "2 spoons = 30 ml × 1.09 g/ml = 33 g", "4 × large (50 g each) = 200 g".
     */
    fun amountFor(text: String, food: FoodItem): Pair<Float, String>? {
        val t = clean(text)
        val m = WITH_UNIT.find(t)
        if (m != null) {
            val qty = number(m.groupValues[1]) ?: return null
            val size = m.groupValues[2]
            val unit = m.groupValues[3]
            if (food.perItem) return null
            val d = Mix.mlToGrams(food, 1f)
            fun ml(v: Float, said: String) = (v * d) to "$said = ${fmt(v)} ml × ${"%.2f".format(d)} g/ml = ${fmt((v * d).roundToInt().toFloat())} g"
            val big = size in setOf("big", "large", "heaped", "heaping", "full")
            val small = size in setOf("small", "little")
            val n = fmt(qty)
            return when {
                unit.startsWith("k") -> (qty * 1000f) to "$n kg = ${fmt(qty * 1000f)} g"
                unit == "g" || unit.startsWith("gm") || unit.startsWith("gram") -> qty to "$n g"
                unit.startsWith("ml") || unit.startsWith("milli") -> ml(qty, "$n ml")
                unit == "l" || unit.startsWith("lit") -> ml(qty * 1000f, "$n L")
                unit.startsWith("cup") -> ml(qty * 250f, "$n cup (250 ml)")
                unit.startsWith("glass") -> ml(qty * 250f, "$n glass (250 ml)")
                unit == "tsp" || unit.startsWith("teaspoon") || (unit.startsWith("spoon") && small) -> ml(qty * 5f, "$n teaspoon (5 ml)")
                unit == "tbsp" || unit.startsWith("tablespoon") || unit.startsWith("spoon") ->
                    ml(qty * 15f * (if (big) 1.5f else 1f), "$n ${if (big) "heaped " else ""}spoon${if (qty != 1f) "s" else ""} (tablespoon, 15 ml)")
                unit.startsWith("scoop") -> ml(qty * 30f * (if (big) 1.5f else if (small) .75f else 1f), "$n scoop (≈ 2 tbsp; scoops vary)")
                unit.startsWith("handful") -> ml(qty * 125f, "$n handful (≈ ½ cup)")
                else -> null
            }
        }
        val b = Regex("""(?<![\w.%])(\d+(?:[.,]\d+)?|½|¼|¾|half|one|two|three|four|five|six|seven|eight|nine|ten)\b(?!\s*%)""").find(t) ?: return null
        val count = number(b.groupValues[1]) ?: return null
        if (food.perItem) return count to "${fmt(count)} ×"
        if (food.servingG <= 0f) return null
        val unit = Portions.countUnit(food) ?: food.servingLabel.removePrefix("1 ").ifBlank { "serving" }
        val g = count * food.servingG
        return g to "${fmt(count)} × $unit (${fmt(food.servingG.roundToInt().toFloat())} g each) = ${fmt(g.roundToInt().toFloat())} g"
    }

    /** True when the text reads like something made from ingredients, not one food's name. */
    fun looksLikeMix(text: String): Boolean {
        val t = clean(text)
        if (t.split(' ').size < 4) return false
        val pieces = t.split(SPLIT).filter { it.isNotBlank() }
        if (PIZZA.containsMatchIn(t)) return pieces.size >= 2
        return pieces.size >= 2 && (WITH_UNIT.containsMatchIn(t) || BARE.containsMatchIn(t))
    }

    /**
     * One cup: the tadka (onion + tomato, a tenth) and oil go in first, unless named with an
     * amount; the vegetables named without one share what's left (100 · 50/50 · 40/30/30, paneer
     * about 30%). Nothing named but the dish word: potato, the everyday one.
     */
    private fun reasonVeg(items: MutableList<Item>, guessed: List<Item>, labels: Map<String, String>, foods: Map<String, FoodItem>) {
        fun food(l: String) = labels[l]?.let { foods[it] }
        fun isGuess(i: Item) = guessed.any { it === i }
        if (items.none { it.label !in VegDish.BASE }) food("Potato")?.let { items.add(0, Item("Potato", it, 0f, null, false, "")) }
        val tadkaEach = VegDish.CUP_G * VegDish.TADKA_SHARE / 2f
        for (l in listOf("Onion", "Tomato")) {
            val i = items.indexOfFirst { it.label == l }
            val how = "the base, a tenth of the cup shared with ${if (l == "Onion") "tomato" else "onion"} ≈ ${fmt(tadkaEach)} g"
            if (i < 0) food(l)?.let { items += Item(l, it, tadkaEach, null, false, how) }
            else if (isGuess(items[i])) items[i] = items[i].copy(grams = tadkaEach, how = how)
        }
        val fat = items.indexOfFirst { it.label == "Oil" || it.label == "Ghee" }
        val oilHow = "2 tsp a cup (1 tsp ≈ 4.5 g) = ${fmt(VegDish.OIL_G)} g; change it if you used more or less"
        if (fat < 0) food("Oil")?.let { items += Item("Oil", it, VegDish.OIL_G, null, false, oilHow) }
        else if (isGuess(items[fat])) items[fat] = items[fat].copy(grams = VegDish.OIL_G, how = oilHow)
        // What's left of the cup goes to the vegetables named without an amount, in the order named.
        val open = items.filter { it.label !in VegDish.BASE && (isGuess(it) || it.grams == 0f) }
        val fixed = items.filter { it !in open }.sumOf { it.grams.toDouble() }.toFloat()
        val room = (VegDish.CUP_G - fixed).coerceAtLeast(VegDish.CUP_G * 0.3f)
        val split = VegDish.split(open.map { it.label }, room)
        val n = open.count { it.label != "Paneer" }
        for (o in open) {
            val g = split[o.label] ?: continue
            val share = if (o.label == "Paneer") (if (n > 0) "about 30% of" else "all of") else
                VegDish.shares(n)[open.filter { it.label != "Paneer" }.indexOf(o)].let { "${(it * 100).roundToInt()}% of" }
            val idx = items.indexOf(o)
            items[idx] = o.copy(grams = g, how = "$share the vegetables' ${fmt(room.roundToInt().toFloat())} g in a cup ≈ ${fmt(g.roundToInt().toFloat())} g")
        }
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
    fun parse(
        text: String,
        foods: Map<String, FoodItem>,
        search: (String) -> FoodItem?,
        /** The user's own readings (UserAliases): word → ingredient. */
        usual: Map<String, String> = emptyMap(),
    ): Draft {
        val pizza = pizzaSize(text)
        // A template (chai, roti meal, sandwich, eggs, rice bowl) brings its own words and amounts.
        val tpl = if (pizza == null && !Mix.isShake(text)) Templates.forText(text) else null
        val labels = (Mix.FRUITS + Mix.EXTRAS + Mix.BASES + Mix.TOPPINGS).toMap() + (tpl?.chips?.toMap() ?: emptyMap())
        val tplAliases = tpl?.aliases.orEmpty().sortedByDescending { it.first.length }
        val crust = pizzaCrust(text)
        val toppingLabels = Mix.TOPPINGS.map { it.first }.toSet()
        val baseLabels = Mix.BASES.map { it.first }.toSet()
        val items = mutableListOf<Item>()
        val guessed = mutableListOf<Item>()
        val unknown = mutableListOf<String>()

        // "aloo gajar ki sabji", "carrot potato stew": the vegetables are separate items; the dish word is just the dish.
        val source = if (tpl?.reasoned == true) VegDish.separate(clean(text), tpl.aliases.map { it.first }) else clean(text)
        for (raw in source.split(SPLIT)) {
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

            // The pizza itself: its whole weight from its size.
            if (pizza != null && PIZZA.containsMatchIn(piece)) {
                val food = foods[crust.food] ?: foods[Mix.PIZZA]
                if (food == null) { unknown += raw.trim(); continue }
                val n = (count ?: qty)?.takeIf { it in 1f..10f } ?: 1f
                val grams = pizza.grams * crust.weight
                items += Item(
                    "Pizza", food, grams * n, null, false,
                    "${pizza.label.lowercase()} ${crust.label.lowercase()} crust, ${pizza.note}" +
                        (if (crust.weight != 1f) " × ${"%.2f".format(crust.weight)} thin" else "") +
                        " = ${fmt(grams.roundToInt().toFloat())} g, crust, sauce and cheese together" +
                        if (n != 1f) " × ${fmt(n)} pizzas" else "",
                )
                continue
            }

            val words = piece.split(' ').filter { it.isNotBlank() && it !in STOP && it.toFloatOrNull() == null }
            val name = words.joinToString(" ")
            if (name.isBlank()) continue
            // Just the dish's own word ("sabji", "chai" when it isn't an ingredient): nothing to add.
            if (tpl != null && words.all { w -> tpl.triggers.any { w == it || w == it + "s" } } && tplAliases.none { (a, _) -> a == name }) continue

            // Which food.
            val matched = tplAliases.firstOrNull { (alias, _) -> Regex("""\b${Regex.escape(alias)}\b""").containsMatchIn(name) }
                ?: ALIASES.firstOrNull { (alias, _) -> Regex("""\b$alias\b""").containsMatchIn(name) }
            var label = matched?.second
            val said = matched?.first ?: name
            if (label == "Milk") label = when {
                fat == null -> "Milk 2%"
                fat >= 3f -> "Milk 3.25%"
                else -> "Milk 2%"
            }
            // The user's usual wins, unless they typed something more specific ("2% milk").
            val mine = usual[said] ?: usual[name]
            val isUsual = mine != null && labels[mine] != null && !(label?.startsWith("Milk") == true && fat != null)
            if (isUsual) label = mine
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
                qty != null && (unit == "tsp" || unit.startsWith("teaspoon") ||
                    (unit.startsWith("spoon") && (size in setOf("small", "little") || (tpl?.spoonIsTeaspoon == true && size !in setOf("big", "large", "heaped", "heaping", "full"))))) ->
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
                isBase && tpl?.hasBase == true -> fromMl(tpl.baseMl.toFloat(), "no amount given: a usual ${tpl.unit} has ${tpl.baseMl} ml")
                isBase -> fromMl(250f, "no amount given, so 1 glass")
                tpl != null && tpl.usual[label] != null -> {
                    val gr = tpl.usual.getValue(label!!)
                    gr to "no amount given: a usual amount in a ${tpl.unit} ≈ ${fmt(gr)} g (change it if yours had more)"
                }
                pizza != null -> {
                    val gr = Mix.toppingGrams(if (label in toppingLabels) label!! else "", pizza)
                    gr to "no amount given: a usual topping on a ${pizza.label.lowercase()} pizza ≈ ${fmt(gr)} g (change it if yours had more)"
                }
                else -> {
                    val each = Mix.quickAmounts(food).first()
                    each.second to "no amount given, so ${each.first} = ${fmt(each.second.roundToInt().toFloat())} g"
                }
            }
            val ml = if (isBase) grams / density else null
            items += Item(label!!, food, grams, ml, isBase, if (isUsual) "your usual · $how" else how, said, isUsual)
                .also { if (how.startsWith("no amount given")) guessed += it }
        }
        // "omelette with 3 eggs": the dish word and "3 eggs" both mean eggs; keep the one with the amount.
        items.removeAll { a -> guessed.any { it === a } && items.any { b -> b !== a && b.label == a.label && guessed.none { it === b } } }
        // A vegetable dish: the app reasons the amounts for one cup, like a home cook would.
        if (tpl?.reasoned == true) reasonVeg(items, guessed, labels, foods)
        // A bare "chai" or "omelette": start from the template's usual recipe.
        if (tpl != null) {
            // Nothing but a base named: the whole usual recipe. Otherwise only the main thing if it's
            // missing (an "egg sandwich" still has bread), so nothing else is added that wasn't asked for.
            // Just the word ("omelette", "2 chai") counts as bare too.
            val onlyWord = tpl.triggers.any { Regex("""^(\S+\s+)?${Regex.escape(it)}s?$""").matches(clean(text)) }
            val bare = items.all { it.isBase } || onlyWord
            tpl.starts.forEachIndexed { i, (lbl, gr) ->
                if (items.any { it.label == lbl }) return@forEachIndexed
                // "Coffee" is already the main thing: don't add tea to it.
                if (lbl in tpl.main && items.any { it.label in tpl.main }) return@forEachIndexed
                val needed = bare || (i == 0 && items.none { it.label in tpl.main })
                val food = labels[lbl]?.let { foods[it] } ?: return@forEachIndexed
                if (needed) items.add(if (bare) items.size else 0, Item(lbl, food, gr, null, false, "${fmt(gr)} g, a usual ${tpl.unit}"))
            }
            if (tpl.hasBase && items.none { it.isBase }) {
                val key = labels[tpl.baseLabel]
                val baseFood = key?.let { foods[it] }
                if (baseFood != null) {
                    val gr = Mix.mlToGrams(baseFood, tpl.baseMl.toFloat())
                    items += Item(tpl.baseLabel, baseFood, gr, tpl.baseMl.toFloat(), true, "${tpl.baseMl} ml, a usual ${tpl.unit}")
                }
            }
        }
        // A cup size ("large coffee", "small chai") scales the whole cup, milk included.
        val sizeLabel = tpl?.let { t ->
            t.sizeWords.entries.sortedByDescending { it.key.length }
                .firstOrNull { (w, _) -> Regex("""\b${Regex.escape(w)}\b""").containsMatchIn(clean(text)) }?.value
        }
        val factor = tpl?.sizes?.firstOrNull { it.first == sizeLabel }?.second ?: 1f
        // Only the cup itself (tea or coffee, and the milk) grows, and only where no amount was typed:
        // "2 spoons sugar" stays 2 spoons in a big cup.
        fun typed(it: Item) = !(it.how.startsWith("no amount given") || it.how.contains("a usual"))
        var sized = if (factor == 1f) items else items.map {
            if (typed(it) || !(it.isBase || it.label in tpl!!.main)) it
            else it.copy(grams = it.grams * factor, ml = it.ml?.times(factor), how = it.how + " × ${fmt(factor)} for a ${sizeLabel!!.lowercase()} ${tpl.unit}")
        }
        // Sugar and cream follow the cup, like at Tim Hortons: 1, 2 or 3 for small, regular, big.
        if (tpl != null && tpl.perSize.isNotEmpty()) {
            val sz = sizeLabel ?: "Regular"
            sized = sized.map {
                val g = tpl.perSize[it.label]?.get(sz)
                if (g == null || typed(it)) it
                else it.copy(grams = g, how = "a usual ${sz.lowercase()} ${tpl.unit} takes ${tpl.quick[it.label]?.firstOrNull { q -> q.second == g }?.first ?: "${fmt(g)} g"}")
            }
            // Tim Hortons words: "double double" = 2 cream + 2 sugar, "triple triple", "black".
            val t = clean(text)
            val dd = Regex("""\b(double|triple|quad)[\s-]+(double|triple|quad)\b""").find(t)
            val n = mapOf("double" to 2, "triple" to 3, "quad" to 4)
            if (dd != null) {
                val creams = n.getValue(dd.groupValues[1])
                val sugars = n.getValue(dd.groupValues[2])
                val creamFood = labels["Cream"]?.let { foods[it] }
                val sugarFood = labels["Sugar"]?.let { foods[it] }
                // One "cream" or "sugar" is sized to the cup: a regular cup's unit is 1 creamer / 1 tsp.
                val unit = tpl.sizes.firstOrNull { it.first == sz }?.second ?: 1f
                sized = sized.filter { it.label != "Cream" && it.label != "Sugar" && !it.isBase }
                if (creamFood != null) sized = sized + Item("Cream", creamFood, creams * 11f * unit, null, false, "${dd.value}: $creams creams (1 creamer each) × ${fmt(unit)} for a ${sz.lowercase()} cup")
                if (sugarFood != null) sized = sized + Item("Sugar", sugarFood, sugars * 4.2f * unit, null, false, "${dd.value}: $sugars sugars (1 tsp each) × ${fmt(unit)} for a ${sz.lowercase()} cup")
            }
            if (Regex("""\bblack\b""").containsMatchIn(t)) {
                sized = sized.filter { !it.isBase && (it.label != "Sugar" || typed(it)) }
            }
        }
        return Draft(sized, unknown, pizza?.let { Dish(it, crust) }, tpl, sizeLabel, tpl?.let { Templates.wordIn(text, it) })
    }
}

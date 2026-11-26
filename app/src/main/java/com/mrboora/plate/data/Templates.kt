package com.mrboora.plate.data

/**
 * Make-it-yourself templates on the same engine as the shake and the pizza: the words that open
 * one, the ingredients offered as chips, what it starts with, and what it's counted in.
 * Adding a template is adding an entry here; the math is always grams × per-100 g numbers.
 */
object Templates {

    data class Template(
        val id: String,
        /** Builder title, poster style: "CHAI & COFFEE". */
        val title: String,
        val subtitle: String,
        /** Words that offer this builder. Longer phrases are checked first. */
        val triggers: List<String>,
        /** Chips: label → food name (Health Canada, or Plate's own list). */
        val chips: List<Pair<String, String>>,
        /** Extra words people type → chip label ("chapati" → Roti). */
        val aliases: List<Pair<String, String>>,
        /** What a bare trigger starts with: label → grams. */
        val starts: List<Pair<String, Float>>,
        /** Grams for an ingredient named without an amount ("chai with sugar"). */
        val usual: Map<String, Float>,
        /** Counted per: cup, plate, sandwich, bowl. */
        val unit: String,
        /** A milk / water base, like the shake. */
        val hasBase: Boolean = false,
        val baseLabel: String = "Milk 2%",
        val baseMl: Int = 0,
        val mlChips: List<Int> = listOf(125, 250, 350),
        /** "A spoon of sugar" in chai means a teaspoon. */
        val spoonIsTeaspoon: Boolean = false,
        /** Name for the saved food. */
        val dishName: String,
        /** The thing it can't be without (bread in a sandwich); added from [starts] when not mentioned. */
        val main: Set<String> = emptySet(),
        /** Plain-word quick amounts per chip ("Regular · 240 ml"), instead of grams and cups. */
        val quick: Map<String, List<Pair<String, Float>>> = emptyMap(),
        /** Whole-portion sizes, as a multiplier of the usual recipe: Small ×0.75, Regular ×1, Big ×1.5. */
        val sizes: List<Pair<String, Float>> = emptyList(),
        /** Words for those sizes when typed ("large coffee", "tall latte"). */
        val sizeWords: Map<String, String> = emptyMap(),
        /** Amounts that follow the cup size, like Tim Hortons: 1, 2 or 3 sugars for small, regular, big. */
        val perSize: Map<String, Map<String, Float>> = emptyMap(),
        /** Amounts are reasoned for the whole dish (home-cooked dishes, see [HomeDish]), not fixed per chip. */
        val reasoned: Boolean = false,
        /** Rows may show Size · How many for whole pieces (eggs, rotis). Off when everything is a share. */
        val wholePieces: Boolean = true,
        /** Quick amounts for any chip without its own ([quick]): e.g. ¼ · ½ · ¾ cup. */
        val parts: List<Pair<String, Float>> = emptyList(),
        /** Named from what's in it and the user's own word: "Aloo gajar sabji", "Carrot potato stew". */
        val nameFromParts: Boolean = false,
        /** A request for one is ready at once: the builder makes it, no menu row needed. */
        val homemade: Boolean = false,
        /**
         * The star ingredients of a reasoned dish and their share of it when there are vegetables
         * too (chicken 60%, paneer 30%); [Lead.gravy]: cooked in more onion-tomato gravy.
         */
        val leads: Map<String, Lead> = emptyMap(),
    )

    data class Lead(val share: Float, val gravy: Boolean = false)

    val CHAI = Template(
        id = "chai", title = "CHAI & COFFEE", subtitle = "Tea or coffee, milk and sugar, worked out from Health Canada numbers",
        triggers = listOf("chai", "tea", "coffee", "latte", "cappuccino", "double double", "triple triple"),
        chips = listOf(
            "Tea" to "Tea, brewed",
            "Coffee" to "Coffee, brewed, prepared with tap water",
            "Sugar" to "Sweets, sugars, granulated (white)",
            "Cream" to "Cream, table (coffee), 18% M.F.",
            "Ginger" to "Ginger root, raw",
        ),
        aliases = listOf(
            "chai" to "Tea", "tea" to "Tea", "coffee" to "Coffee", "latte" to "Coffee", "cappuccino" to "Coffee",
            "espresso" to "Coffee", "double double" to "Coffee", "triple triple" to "Coffee", "sugar" to "Sugar", "cream" to "Cream", "ginger" to "Ginger", "adrak" to "Ginger",
        ),
        starts = listOf("Tea" to 120f, "Sugar" to 8.5f),
        usual = mapOf("Tea" to 120f, "Coffee" to 240f, "Sugar" to 8.5f, "Cream" to 15f, "Ginger" to 3f),
        unit = "cup", hasBase = true, baseLabel = "Milk 2%", baseMl = 120, mlChips = listOf(30, 60, 120),
        spoonIsTeaspoon = true, dishName = "Chai", main = setOf("Tea", "Coffee"),
        quick = mapOf(
            "Tea" to listOf("Small" to 90f, "Regular" to 120f, "Big" to 180f),
            "Coffee" to listOf("Small" to 180f, "Regular" to 240f, "Big" to 360f),
            "Sugar" to listOf("1 tsp" to 4.2f, "2 tsp" to 8.5f, "3 tsp" to 12.7f),
            "Cream" to listOf("1 creamer" to 11f, "2 creamers" to 22f, "3 creamers" to 33f),
            "Ginger" to listOf("A little" to 2f, "Some" to 4f, "Lots" to 8f),
        ),
        sizes = listOf("Small" to 0.75f, "Regular" to 1f, "Big" to 1.5f),
        perSize = mapOf(
            "Sugar" to mapOf("Small" to 4.2f, "Regular" to 8.5f, "Big" to 12.7f),   // 1, 2, 3 tsp
            "Cream" to mapOf("Small" to 11f, "Regular" to 22f, "Big" to 33f),       // 1, 2, 3 creamers
        ),
        sizeWords = mapOf(
            "small" to "Small", "short" to "Small", "regular" to "Regular", "medium" to "Regular", "tall" to "Regular",
            "large" to "Big", "big" to "Big", "grande" to "Big", "extra large" to "Big", "xl" to "Big",
        ),
    )

    val ROTI = Template(
        id = "roti", title = "ROTI MEAL", subtitle = "Roti, dal, sabzi and sides, worked out from Health Canada and Plate's numbers",
        triggers = listOf("roti", "chapati", "chapatti", "phulka", "paratha", "thali"),
        chips = listOf(
            "Roti" to "Roti / chapati",
            "Paratha" to "Bread, Indian, paratha, whole wheat",
            "Naan" to "Naan",
            "Dal" to "Dal (lentil curry)",
            "Rajma" to "Rajma (kidney bean curry)",
            "Chana" to "Chana masala",
            "Chicken curry" to "Chicken curry",
            "Rice" to "Basmati rice, cooked",
            "Curd" to "Curd / dahi",
            "Ghee" to "Ghee",
            "Salad" to "Mixed salad",
        ),
        aliases = listOf(
            "roti" to "Roti", "rotis" to "Roti", "chapati" to "Roti", "chapatis" to "Roti", "chapatti" to "Roti", "phulka" to "Roti",
            "paratha" to "Paratha", "parathas" to "Paratha", "naan" to "Naan", "dal" to "Dal", "daal" to "Dal",
            "rajma" to "Rajma", "chana" to "Chana", "chole" to "Chana", "chicken curry" to "Chicken curry",
            "rice" to "Rice", "chawal" to "Rice", "ghee" to "Ghee", "salad" to "Salad",
        ),
        starts = listOf("Roti" to 80f, "Dal" to 200f),
        usual = mapOf("Dal" to 200f, "Rajma" to 200f, "Chana" to 200f, "Chicken curry" to 250f, "Rice" to 160f, "Curd" to 150f, "Ghee" to 5f, "Salad" to 100f),
        unit = "plate", dishName = "Roti meal", main = setOf("Roti", "Paratha", "Naan", "Rice"),
        spoonIsTeaspoon = true,
    )

    val SANDWICH = Template(
        id = "sandwich", title = "SANDWICH & TOAST", subtitle = "Bread and what's in it, worked out from Health Canada numbers",
        triggers = listOf("sandwich", "toast", "grilled cheese"),
        chips = listOf(
            "Brown bread" to "Bread, whole wheat",
            "White bread" to "Bread, white",
            "Butter" to "Butter",
            "Cheese" to "Cheddar cheese",
            "Ham" to "Deli-meat, ham, cooked, sliced",
            "Egg" to "Egg, boiled",
            "Chicken" to "Chicken breast, cooked",
            "Peanut butter" to "Peanut butter",
            "Mayo" to "Salad dressing, mayonnaise, regular",
            "Lettuce" to "Lettuce, cos or romaine",
            "Tomato" to "Tomato, red, ripe, raw, year round average",
        ),
        aliases = listOf(
            "brown bread" to "Brown bread", "whole wheat" to "Brown bread", "white bread" to "White bread", "bread" to "Brown bread",
            "butter" to "Butter", "cheese" to "Cheese", "ham" to "Ham", "egg" to "Egg", "eggs" to "Egg", "chicken" to "Chicken",
            "mayo" to "Mayo", "mayonnaise" to "Mayo", "lettuce" to "Lettuce", "tomato" to "Tomato", "tomatoes" to "Tomato",
        ),
        starts = listOf("Brown bread" to 64f, "Butter" to 5f),
        usual = mapOf("Butter" to 5f, "Cheese" to 28f, "Ham" to 30f, "Egg" to 50f, "Chicken" to 60f, "Peanut butter" to 16f, "Mayo" to 15f, "Lettuce" to 10f, "Tomato" to 30f),
        unit = "sandwich", dishName = "Sandwich", main = setOf("Brown bread", "White bread"),
    )

    val EGGS = Template(
        id = "eggs", title = "EGGS & OMELETTE", subtitle = "Eggs and what's cooked with them, worked out from Health Canada numbers",
        triggers = listOf("omelette", "omelet", "bhurji", "scrambled", "eggs", "egg"),
        chips = listOf(
            "Eggs" to "Egg, chicken, whole, fresh or frozen, raw",
            "Oil" to "Vegetable oil, canola",
            "Butter" to "Butter",
            "Onion" to "Onion, raw",
            "Tomato" to "Tomato, red, ripe, raw, year round average",
            "Cheese" to "Cheddar cheese",
            "Milk" to "Milk, fluid, partly skimmed, 2% M.F.",
            "Spinach" to "Spinach, raw",
            "Green pepper" to "Pepper, sweet, green, raw",
        ),
        aliases = listOf(
            "eggs" to "Eggs", "egg" to "Eggs", "omelette" to "Eggs", "omelet" to "Eggs", "bhurji" to "Eggs",
            "oil" to "Oil", "butter" to "Butter", "onion" to "Onion", "onions" to "Onion", "tomato" to "Tomato",
            "tomatoes" to "Tomato", "cheese" to "Cheese", "milk" to "Milk", "spinach" to "Spinach",
            "pepper" to "Green pepper", "capsicum" to "Green pepper",
        ),
        starts = listOf("Eggs" to 91f, "Oil" to 4.6f),
        usual = mapOf("Eggs" to 91f, "Oil" to 4.6f, "Butter" to 5f, "Onion" to 30f, "Tomato" to 30f, "Cheese" to 20f, "Milk" to 30f, "Spinach" to 20f, "Green pepper" to 30f),
        unit = "plate", dishName = "Omelette", main = setOf("Eggs"),
    )

    val BOWL = Template(
        id = "bowl", title = "RICE BOWL", subtitle = "Rice, protein and toppings, worked out from Health Canada and Plate's numbers",
        triggers = listOf("rice bowl", "burrito bowl", "grain bowl", "poke bowl", "buddha bowl"),
        chips = listOf(
            "Rice" to "Basmati rice, cooked",
            "Brown rice" to "Brown rice, cooked",
            "Chicken" to "Chicken breast, cooked",
            "Paneer" to "Paneer",
            "Chickpeas" to "Chickpeas (garbanzo beans, bengal gram), mature seeds, boiled",
            "Beans" to "Beans, kidney, all types, canned, solids and liquid",
            "Lentils" to "Lentils, mature seeds, boiled",
            "Corn" to "Corn, sweet, yellow, raw",
            "Onion" to "Onion, raw",
            "Green pepper" to "Pepper, sweet, green, raw",
            "Lettuce" to "Lettuce, cos or romaine",
            "Cheese" to "Cheddar cheese",
            "Curd" to "Curd / dahi",
        ),
        aliases = listOf(
            "brown rice" to "Brown rice", "rice" to "Rice", "chicken" to "Chicken", "paneer" to "Paneer", "chickpeas" to "Chickpeas",
            "chana" to "Chickpeas", "beans" to "Beans", "rajma" to "Beans", "lentils" to "Lentils", "corn" to "Corn",
            "onion" to "Onion", "pepper" to "Green pepper", "peppers" to "Green pepper", "lettuce" to "Lettuce",
            "cheese" to "Cheese", "curd" to "Curd", "yogurt" to "Curd",
        ),
        starts = listOf("Rice" to 160f, "Chicken" to 120f),
        usual = mapOf("Rice" to 160f, "Brown rice" to 160f, "Chicken" to 120f, "Paneer" to 80f, "Chickpeas" to 80f, "Beans" to 80f, "Lentils" to 80f, "Corn" to 40f, "Onion" to 30f, "Green pepper" to 30f, "Lettuce" to 20f, "Cheese" to 20f, "Curd" to 60f),
        unit = "bowl", dishName = "Rice bowl", main = setOf("Rice", "Brown rice"),
    )

    /**
     * A home-cooked dish, veg or non-veg, whatever it's called where you're from: sabji, curry,
     * keema, korma, poriyal, stir-fry, stew, guisado… All the same shape: the main ingredients, a base
     * of onion and tomato, a little oil. Names work in Hindi and English (aloo = potato, murgh =
     * chicken). Amounts are reasoned by [HomeDish]. A new word is one more entry in [triggers], a new
     * ingredient one more chip; the builder screen doesn't change.
     */
    val DISH = Template(
        id = "dish", title = "HOME-COOKED DISH", subtitle = "The main ingredients, an onion-tomato base and a little oil, worked out from Health Canada numbers",
        triggers = listOf(
            // Curries and the like, veg or non-veg
            "curry", "keema", "korma", "bhuna", "kadai", "kadhai", "karahi", "vindaloo", "do pyaza", "rogan josh",
            "jalfrezi", "salan", "sukka", "chettinad", "casserole", "goulash", "adobo",
            // South Asia
            "sabji", "sabzi", "subzi", "sabjee", "subji", "bhaji", "bhujia", "tarkari", "torkari", "shaak", "shak",
            "poriyal", "thoran", "palya", "kootu", "kura",
            // Elsewhere
            "stir fry", "stir fried", "stirfry", "stew", "saute", "sauté", "sauteed", "sautéed",
            "guisado", "salteado", "ratatouille", "veggie mix", "mixed veg",
        ),
        chips = listOf(
            "Potato" to "Potato, flesh and skin, raw",
            "Carrot" to "Carrot, raw",
            "Cauliflower" to "Cauliflower, raw",
            "Peas" to "Peas, green, frozen, unprepared",
            "Okra" to "Okra (gumbo), raw",
            "Eggplant" to "Eggplant (aubergine, brinjal), raw",
            "Spinach" to "Spinach, raw",
            "Paneer" to "Paneer",
            "Bottle gourd" to "Gourd, white flowered (calabash gourd), raw",
            "Ridge gourd" to "Gourd, dishcloth (towelgourd), raw",
            "Bitter gourd" to "Balsam-pear (bitter gourd, bitter melon), pods, raw",
            "Green beans" to "Beans, snap (Italian, green or yellow), raw",
            "Cabbage" to "Cabbage, raw",
            "Pumpkin" to "Pumpkin, raw",
            "Taro" to "Taro, raw",
            "Bell pepper" to "Pepper, sweet, green, raw",
            "Mushroom" to "Mushroom, white, raw",
            "Chicken" to "Chicken, broiler, meat only, raw",
            "Mutton" to "Lamb, Canadian, composite cuts, lean, raw",
            "Mince" to "Beef, ground, lean, raw",
            "Fish" to "Fish, tilapia, raw",
            "Prawns" to "Crustaceans, shrimp, mixed species, raw",
            "Egg" to "Egg, chicken, whole, fresh or frozen, raw",
            "Tofu" to "Tofu, regular, medium firm or firm (prepared with calcium sulphate)",
            "Mustard greens" to "Mustard greens, raw",
            "Onion" to "Onion, raw",
            "Tomato" to "Tomato, red, ripe, raw, year round average",
            "Oil" to "Vegetable oil, canola",
            "Ghee" to "Ghee",
        ),
        // Labels in English so everyone can read them; Hindi and other names point to them
        // (the dish's name keeps the user's own word: "Aloo gajar sabji").
        aliases = listOf(
            "aloo" to "Potato", "alu" to "Potato", "aaloo" to "Potato", "batata" to "Potato", "potato" to "Potato", "potatoes" to "Potato",
            "gajar" to "Carrot", "carrot" to "Carrot", "carrots" to "Carrot",
            "gobhi" to "Cauliflower", "gobi" to "Cauliflower", "phool gobhi" to "Cauliflower", "cauliflower" to "Cauliflower",
            "matar" to "Peas", "mattar" to "Peas", "mutter" to "Peas", "peas" to "Peas", "pea" to "Peas",
            "bhindi" to "Okra", "okra" to "Okra", "lady finger" to "Okra", "ladyfinger" to "Okra",
            "baingan" to "Eggplant", "bengan" to "Eggplant", "brinjal" to "Eggplant", "eggplant" to "Eggplant", "aubergine" to "Eggplant",
            "palak" to "Spinach", "spinach" to "Spinach",
            "paneer" to "Paneer", "cottage cheese" to "Paneer",
            "lauki" to "Bottle gourd", "ghiya" to "Bottle gourd", "doodhi" to "Bottle gourd", "bottle gourd" to "Bottle gourd",
            "turai" to "Ridge gourd", "tori" to "Ridge gourd", "tinda" to "Ridge gourd", "ridge gourd" to "Ridge gourd",
            "karela" to "Bitter gourd", "bitter gourd" to "Bitter gourd", "bitter melon" to "Bitter gourd",
            "beans" to "Green beans", "green beans" to "Green beans", "french beans" to "Green beans",
            "patta gobhi" to "Cabbage", "band gobhi" to "Cabbage", "cabbage" to "Cabbage",
            "kaddu" to "Pumpkin", "pumpkin" to "Pumpkin", "arbi" to "Taro", "taro" to "Taro",
            "shimla mirch" to "Bell pepper", "capsicum" to "Bell pepper", "bell pepper" to "Bell pepper",
            "mushroom" to "Mushroom", "mushrooms" to "Mushroom", "saag" to "Mustard greens", "sarson" to "Mustard greens", "mustard greens" to "Mustard greens",
            "onion" to "Onion", "pyaz" to "Onion", "pyaaz" to "Onion", "tomato" to "Tomato", "tamatar" to "Tomato",
            "oil" to "Oil", "tel" to "Oil", "ghee" to "Ghee",
            "chicken" to "Chicken", "murgh" to "Chicken", "murg" to "Chicken",
            "mutton" to "Mutton", "lamb" to "Mutton", "goat" to "Mutton", "gosht" to "Mutton", "bakra" to "Mutton",
            "keema" to "Mince", "kheema" to "Mince", "qeema" to "Mince", "mince" to "Mince", "ground beef" to "Mince", "ground meat" to "Mince", "beef" to "Mince",
            "fish" to "Fish", "machhi" to "Fish", "macchi" to "Fish", "machli" to "Fish", "meen" to "Fish",
            "prawns" to "Prawns", "prawn" to "Prawns", "shrimp" to "Prawns", "jhinga" to "Prawns", "chingri" to "Prawns",
            "egg" to "Egg", "eggs" to "Egg", "anda" to "Egg", "ande" to "Egg", "tofu" to "Tofu",
        ),
        starts = emptyList(),
        usual = emptyMap(),
        unit = "cup", dishName = "Home-cooked dish",
        spoonIsTeaspoon = true,
        reasoned = true, wholePieces = false, parts = HomeDish.PARTS, nameFromParts = true, homemade = true,
        // The star of the dish when there are vegetables too; on its own it's all of it.
        leads = mapOf(
            "Chicken" to Lead(0.6f, gravy = true), "Mutton" to Lead(0.6f, gravy = true), "Mince" to Lead(0.6f, gravy = true),
            "Fish" to Lead(0.6f, gravy = true), "Prawns" to Lead(0.6f, gravy = true), "Egg" to Lead(0.5f, gravy = true),
            "Paneer" to Lead(0.3f), "Tofu" to Lead(0.4f),
        ),
        quick = mapOf(
            "Onion" to HomeDish.BASE_PARTS, "Tomato" to HomeDish.BASE_PARTS,
            "Oil" to listOf("1 tsp" to 4.5f, "1½ tsp" to 6.75f, "2 tsp" to 9f),
            "Ghee" to listOf("1 tsp" to 5f, "1½ tsp" to 7.5f, "2 tsp" to 10f),
        ),
    )

    /**
     * In the order they're checked: a "rice bowl" is a bowl, not a roti meal with rice; a meal
     * ("chicken curry with roti") comes before the dish in it; "masala chai" is chai; an "egg
     * curry" is a dish, not an omelette.
     */
    val ALL = listOf(BOWL, ROTI, CHAI, SANDWICH, DISH, EGGS)

    /** Every food name any template uses, for loading. */
    val foodNames: Set<String> get() = ALL.flatMap { t -> t.chips.map { it.second } }.toSet()

    /** The template a text asks for, if any (pizza and shakes are checked before this). */
    fun forText(text: String): Template? {
        val t = text.lowercase()
        return ALL.firstOrNull { tpl ->
            tpl.triggers.sortedByDescending { it.length }.any { Regex("""\b${Regex.escape(it)}s?\b""").containsMatchIn(t) }
        }
    }

    fun byId(id: String): Template? = ALL.firstOrNull { it.id == id }

    /** The word that opened [tpl] in [text], as typed ("sabzi", "stew"), for naming the dish. */
    fun wordIn(text: String, tpl: Template): String? {
        val t = text.lowercase()
        return tpl.triggers.sortedByDescending { it.length }.firstOrNull { Regex("""\b${Regex.escape(it)}s?\b""").containsMatchIn(t) }
    }
}

/**
 * How a home-cooked dish is put together, reasoned the way a home cook does: one cup (250 ml, about 200 g
 * cooked: vegetable pieces sit loosely, about 0.8 g per ml) is
 * mostly the vegetables, a tadka of onion and tomato, and a little oil. Spices, salt, chilli and
 * ginger have almost no calories and are counted as 0. Every amount can be changed in the builder.
 */
object HomeDish {
    /** One cup of sabji, cooked: 250 ml at about 0.8 g/ml. Typing "katori" or "bowl" still works. */
    const val CUP_G = 200f
    /** The onion and tomato base (tadka, sofrito…): about a tenth of the cup, half each. */
    const val TADKA_SHARE = 0.10f
    /** Oil: 2 teaspoons a cup (a teaspoon of oil is about 4.5 g). */
    const val OIL_G = 9f
    /** The base when there's meat, fish or egg: more gravy, about a quarter of the cup. */
    const val GRAVY_SHARE = 0.25f

    /** Quick amounts for a vegetable in the builder: parts of a cup. */
    val PARTS: List<Pair<String, Float>> = listOf("¼ cup" to CUP_G / 4, "½ cup" to CUP_G / 2, "¾ cup" to CUP_G * 3 / 4)

    /** Onion or tomato in the base: half the usual, the usual, or twice as much. */
    val BASE_PARTS: List<Pair<String, Float>> get() = (CUP_G * TADKA_SHARE / 2).let { listOf("Less" to it / 2, "Usual" to it, "More" to it * 2) }

    /** Not vegetables: they're the tadka and the fat, sized on their own. */
    val BASE = setOf("Onion", "Tomato", "Oil", "Ghee")

    /** How the vegetables share their part, in the order named: 100 · 50/50 · 40/30/30, then equal. */
    fun shares(n: Int): List<Float> = when (n) {
        0 -> emptyList()
        1 -> listOf(1f)
        2 -> listOf(0.5f, 0.5f)
        3 -> listOf(0.4f, 0.3f, 0.3f)
        else -> List(n) { 1f / n }
    }

    /**
     * Grams for each main ingredient named without an amount, given what's left of the cup after
     * the base, the oil and anything with a typed amount. Stars ([leads]: chicken, paneer…) take
     * their share first, the vegetables share the rest 100 · 50/50 · 40/30/30. Only stars: theirs.
     */
    fun split(names: List<String>, room: Float, leads: Map<String, Templates.Lead>): Map<String, Float> {
        val stars = names.filter { it in leads }
        val veg = names.filter { it !in leads }
        val out = mutableMapOf<String, Float>()
        if (veg.isEmpty()) {
            val total = stars.sumOf { leads.getValue(it).share.toDouble() }.toFloat()
            stars.forEach { out[it] = room * leads.getValue(it).share / total }
            return out
        }
        // Stars together take at most 80%, so the vegetables are always there.
        val wanted = stars.sumOf { leads.getValue(it).share.toDouble() }.toFloat()
        val scale = if (wanted > 0.8f) 0.8f / wanted else 1f
        stars.forEach { out[it] = room * leads.getValue(it).share * scale }
        val forVeg = room - out.values.sum()
        veg.zip(shares(veg.size)).forEach { (v, s) -> out[v] = forVeg * s }
        return out
    }

    /**
     * The main ingredients a dish's text names, in order, whatever the language or small typos:
     * "potato and carrot sabji" and "aloo garaj ki sabji" both give Aloo, Gajar.
     */
    fun vegetables(text: String): List<String> {
        val t = text.lowercase()
        val aliases = Templates.DISH.aliases.sortedByDescending { it.first.length }
        val found = mutableListOf<String>()
        // Two-word names first ("bottle gourd"), then each word on its own, allowing typos.
        var rest = " $t "
        aliases.filter { ' ' in it.first }.forEach { (a, l) -> if (rest.contains(" $a ")) { found += l; rest = rest.replace(" $a ", " ") } }
        Spelling.words(rest).forEach { w ->
            aliases.firstOrNull { (a, _) -> ' ' !in a && (a == w || (w.length >= 4 && Spelling.close(w, a))) }?.let { found += it.second }
        }
        return found.distinct().filter { it !in BASE }
    }

    /** "aloo gajar ki sabji" → "aloo, gajar, sabji": vegetables side by side become separate items. */
    fun separate(text: String, aliases: List<String>): String {
        val words = aliases.sortedByDescending { it.length }
        // "1 katori", "2 bowls of", "a cup": how much sabji was eaten, not an amount of one vegetable.
        val serving = Regex("""\b(\d+(?:[.,]\d+)?|a|an|one|half|1/2)\s*(katoris?|bowls?|cups?|plates?)\s*(of\s+)?""")
        var t = " " + serving.replace(text.lowercase(), " ").replace(Regex("""\b(ki|ka|ke|wali|wala|vali)\b"""), " ").replace(Regex("""\s+"""), " ").trim() + " "
        // Put a comma after each vegetable word that's followed straight by another one.
        val alt = words.joinToString("|") { Regex.escape(it) }
        val pair = Regex("""\b($alt)\s+(?=($alt)\b)""")
        while (true) {
            val m = pair.find(t) ?: break
            t = t.replaceRange(m.range, m.groupValues[1] + ", ")
        }
        return t.trim()
    }
}


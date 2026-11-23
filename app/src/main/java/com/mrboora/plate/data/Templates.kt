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
    )

    val CHAI = Template(
        id = "chai", title = "CHAI & COFFEE", subtitle = "Tea or coffee, milk and sugar, worked out from Health Canada numbers",
        triggers = listOf("chai", "tea", "coffee", "latte", "cappuccino"),
        chips = listOf(
            "Tea" to "Tea, brewed",
            "Coffee" to "Coffee, brewed, prepared with tap water",
            "Sugar" to "Sweets, sugars, granulated (white)",
            "Cream" to "Cream, table (coffee), 18% M.F.",
            "Ginger" to "Ginger root, raw",
        ),
        aliases = listOf(
            "chai" to "Tea", "tea" to "Tea", "coffee" to "Coffee", "latte" to "Coffee", "cappuccino" to "Coffee",
            "espresso" to "Coffee", "sugar" to "Sugar", "cream" to "Cream", "ginger" to "Ginger", "adrak" to "Ginger",
        ),
        starts = listOf("Tea" to 120f, "Sugar" to 8.5f),
        usual = mapOf("Tea" to 120f, "Coffee" to 240f, "Sugar" to 8.5f, "Cream" to 15f, "Ginger" to 3f),
        unit = "cup", hasBase = true, baseLabel = "Milk 2%", baseMl = 120, mlChips = listOf(30, 60, 120),
        spoonIsTeaspoon = true, dishName = "Chai", main = setOf("Tea", "Coffee"),
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

    /** In the order they're checked: a "rice bowl" is a bowl, not a roti meal with rice. */
    val ALL = listOf(BOWL, CHAI, ROTI, SANDWICH, EGGS)

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
}

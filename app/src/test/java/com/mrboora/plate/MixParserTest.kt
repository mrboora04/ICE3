package com.mrboora.plate

import com.mrboora.plate.data.CanadianFoods
import com.mrboora.plate.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Needs the real Health Canada file from assets, so it runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
class MixParserTest {
    @Test
    fun readsAHomemadeMixWrittenInPlainEnglish() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = CanadianFoods(ctx)
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS).map { it.second } + com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.CRUST_FOODS + com.mrboora.plate.data.Templates.foodNames
        val foods = names.mapNotNull { n -> (cnf.byName(n) ?: com.mrboora.plate.data.FoodCatalog.foods.firstOrNull { it.name == n })?.let { n to it } }.toMap().toMutableMap()
        foods[com.mrboora.plate.data.Mix.MIXED_BERRIES] = com.mrboora.plate.data.Mix.mixedBerries(com.mrboora.plate.data.Mix.BERRY_PARTS.mapNotNull { foods[it] })!!
        foods[com.mrboora.plate.data.Mix.WATER] = com.mrboora.plate.data.Mix.water
        val search: (String) -> FoodItem? = { cnf.search(it, 1).firstOrNull() }

        // The user's own words, typo included.
        val text = "1 banana with around 30g 4 barries-blend  with a regular spoon of peanut butter over with 3.25% milk around 400ml "
        assertTrue(com.mrboora.plate.data.MixParser.looksLikeMix(text))
        val d = com.mrboora.plate.data.MixParser.parse(text, foods, search)
        assertEquals(listOf("Banana", "Mixed berries", "Peanut butter", "Milk 3.25%"), d.items.map { it.label })
        assertEquals(118f, d.items[0].grams, 0.5f)            // 1 medium banana
        assertEquals(30f, d.items[1].grams, 0.01f)            // weighed
        assertEquals(15f * 272.6f / 250f, d.items[2].grams, 0.1f) // 1 tbsp × peanut butter's own density
        assertEquals(400f, d.base!!.ml!!, 0.5f)
        assertEquals("Milk 3.25%", d.base!!.label)
        assertTrue(d.unknown.isEmpty())

        // Scoops and sizes, glasses, and milk without a fat level.
        val e = com.mrboora.plate.data.MixParser.parse("2 big scoops of oats, half cup yogurt and a glass of milk", foods, search)
        assertEquals(listOf("Oats", "Yogurt", "Milk 2%"), e.items.map { it.label })
        assertTrue(e.items[0].how.contains("scoop"))
        assertEquals(250f, e.base!!.ml!!, 0.5f)

        // One food's name is not a mix.
        assertFalse(com.mrboora.plate.data.MixParser.looksLikeMix("banana bread"))
        assertFalse(com.mrboora.plate.data.MixParser.looksLikeMix("chicken shawarma plate"))

        // A pizza: same math, with the size setting the whole weight and toppings scaling with it.
        val pz = "party size cheese pizza with paneer topping with onion"
        assertTrue(com.mrboora.plate.data.MixParser.looksLikeMix(pz))
        val p = com.mrboora.plate.data.MixParser.parse(pz, foods, search)
        assertEquals("Party", p.dish!!.size.label)
        assertEquals(24, p.dish!!.size.slices)
        assertEquals(listOf("Pizza", "Paneer", "Onion"), p.items.map { it.label })
        assertEquals("Regular", p.dish!!.crust.label)
        assertEquals(1745f, p.items[0].grams, 0.5f)
        assertEquals(195f, p.items[1].grams, 0.5f)   // 70 g on a medium × 1745/627, to the nearest 5 g
        assertEquals(110f, p.items[2].grams, 0.5f)   // 40 g × 1745/627
        assertTrue(p.unknown.isEmpty())
        assertEquals("X-Large", com.mrboora.plate.data.MixParser.pizzaSize("extra large pizza with corn")!!.label)
        assertEquals("Large", com.mrboora.plate.data.MixParser.pizzaSize("14 inch pizza with onion")!!.label)
        assertEquals("Medium", com.mrboora.plate.data.MixParser.pizzaSize("cheese pizza with mushrooms")!!.label)
        // Crust: thin is lighter (556 g for 14" instead of 853 g); "pizza" alone is a pizza.
        val thin = com.mrboora.plate.data.MixParser.parse("large thin crust pizza with corn", foods, search)
        assertEquals("Thin", thin.dish!!.crust.label)
        assertEquals(556f, thin.items[0].grams, 1f)
        assertEquals("Pizza, cheese, thin crust, frozen, cooked", thin.items[0].food.name)
        assertEquals(1, com.mrboora.plate.data.MixParser.parse("pizza", foods, search).items.size)
        assertTrue(com.mrboora.plate.data.Mix.isShake("orange juice with ginger"))

        // Templates: chai (a spoon is a teaspoon here), roti meal, sandwich, eggs, rice bowl.
        val chai = com.mrboora.plate.data.MixParser.parse("chai with 2% milk and 2 spoons sugar", foods, search)
        assertEquals("chai", chai.template!!.id)
        assertEquals(listOf("Tea", "Milk 2%", "Sugar"), chai.items.map { it.label })
        assertEquals(120f, chai.base!!.ml!!, 0.5f)
        assertEquals(2 * 5f * 211.3f / 250f, chai.items[2].grams, 0.1f)

        val roti = com.mrboora.plate.data.MixParser.parse("2 roti with dal and a spoon of ghee", foods, search)
        assertEquals("roti", roti.template!!.id)
        assertEquals(listOf("Roti", "Dal", "Ghee"), roti.items.map { it.label })
        assertEquals(80f, roti.items[0].grams, 0.5f)
        assertEquals(200f, roti.items[1].grams, 0.5f)

        val sandwich = com.mrboora.plate.data.MixParser.parse("egg sandwich with cheese", foods, search)
        assertEquals("sandwich", sandwich.template!!.id)
        assertEquals(listOf("Brown bread", "Egg", "Cheese"), sandwich.items.map { it.label })

        val omelette = com.mrboora.plate.data.MixParser.parse("omelette", foods, search)
        assertEquals(listOf("Eggs", "Oil"), omelette.items.map { it.label })

        val bowl = com.mrboora.plate.data.MixParser.parse("rice bowl with chicken and corn", foods, search)
        assertEquals("bowl", bowl.template!!.id)
        assertEquals(listOf("Rice", "Chicken", "Corn"), bowl.items.map { it.label })
        assertTrue(bowl.unknown.isEmpty())

        // "Coffee" is coffee: no tea added. Cup sizes in plain words scale the whole cup.
        val coffee = com.mrboora.plate.data.MixParser.parse("coffee", foods, search)
        assertEquals(listOf("Coffee", "Sugar", "Milk 2%"), coffee.items.map { it.label })
        val big = com.mrboora.plate.data.MixParser.parse("large coffee with milk and 2 spoons sugar", foods, search)
        assertEquals(2 * 5f * 211.3f / 250f, big.items.first { it.label == "Sugar" }.grams, 0.1f) // typed: not scaled
        assertEquals("Big", big.sizeLabel)
        assertEquals(240f * 1.5f, big.items.first { it.label == "Coffee" }.grams, 0.5f)
        assertEquals(120f * 1.5f, big.base!!.ml!!, 0.5f)
        assertEquals("Small", com.mrboora.plate.data.MixParser.parse("small chai", foods, search).sizeLabel)

        // Sugar follows the cup like at Tim Hortons; Tim words work.
        assertEquals(4.2f, com.mrboora.plate.data.MixParser.parse("small coffee with sugar", foods, search).items.first { it.label == "Sugar" }.grams, 0.01f)
        assertEquals(12.7f, com.mrboora.plate.data.MixParser.parse("large coffee with sugar", foods, search).items.first { it.label == "Sugar" }.grams, 0.01f)
        val dd = com.mrboora.plate.data.MixParser.parse("medium double double", foods, search)
        assertEquals(listOf("Coffee", "Cream", "Sugar"), dd.items.map { it.label })
        assertEquals(22f, dd.items[1].grams, 0.01f)
        assertEquals(8.4f, dd.items[2].grams, 0.01f)
        val ddLarge = com.mrboora.plate.data.MixParser.parse("large double double coffee", foods, search)
        assertEquals(33f, ddLarge.items.first { it.label == "Cream" }.grams, 0.01f)
        val black = com.mrboora.plate.data.MixParser.parse("black coffee", foods, search)
        assertEquals(listOf("Coffee"), black.items.map { it.label })

        // Remembered readings: "milk" is this user's 3.25%; a typed "2%" still wins.
        val mine = mapOf("milk" to "Milk 3.25%")
        val usualChai = com.mrboora.plate.data.MixParser.parse("chai with milk", foods, search, mine)
        assertEquals("Milk 3.25%", usualChai.base!!.label)
        assertTrue(usualChai.base!!.usual && usualChai.base!!.how.startsWith("your usual"))
        assertEquals("milk", usualChai.base!!.said)
        assertEquals("Milk 2%", com.mrboora.plate.data.MixParser.parse("chai with 2% milk", foods, search, mine).base!!.label)
        // Without a remembered reading, plain milk is still 2%.
        assertEquals("Milk 2%", com.mrboora.plate.data.MixParser.parse("chai with milk", foods, search).base!!.label)
        // Works for ingredients too: "berries" → Strawberries for this user.
        val berries = com.mrboora.plate.data.MixParser.parse("1 banana with 30 g berries and 250 ml milk", foods, search, mapOf("berries" to "Strawberries"))
        assertEquals("Strawberries", berries.items.first { it.said == "berries" }.label)

        // Shakes and pizza keep their own builders.
        assertEquals(null, com.mrboora.plate.data.MixParser.parse("mango lassi", foods, search).template)
        assertEquals(null, com.mrboora.plate.data.MixParser.parse("pizza with corn", foods, search).template)
    }
}

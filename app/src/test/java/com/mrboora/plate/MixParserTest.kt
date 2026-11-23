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
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS).map { it.second } + com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.PIZZA
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
        assertEquals(listOf("Cheese pizza, party", "Paneer", "Onion"), p.items.map { it.label })
        assertEquals(1745f, p.items[0].grams, 0.5f)
        assertEquals(195f, p.items[1].grams, 0.5f)   // 70 g on a medium × 1745/627, to the nearest 5 g
        assertEquals(110f, p.items[2].grams, 0.5f)   // 40 g × 1745/627
        assertTrue(p.unknown.isEmpty())
        assertEquals("X-Large", com.mrboora.plate.data.MixParser.pizzaSize("extra large pizza with corn")!!.label)
        assertEquals("Large", com.mrboora.plate.data.MixParser.pizzaSize("14 inch pizza with onion")!!.label)
        assertEquals("Medium", com.mrboora.plate.data.MixParser.pizzaSize("cheese pizza with mushrooms")!!.label)
    }
}

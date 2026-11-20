package com.mrboora.plate

import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.AiLookup
import com.mrboora.plate.data.AiLookupException
import com.mrboora.plate.data.FoodCatalog
import com.mrboora.plate.data.FoodItem
import com.mrboora.plate.data.OpenFoodFacts
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Sex
import com.mrboora.plate.ui.screens.clean
import com.mrboora.plate.ui.screens.goalProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {
    private val base = Profile(
        name = "Test", sex = Sex.MALE, age = 30, heightCm = 180f, weightKg = 80f, goalWeightKg = 75f,
        activity = Activity.SEDENTARY, goal = Goal.MAINTAIN, paceKgPerWeek = 0f,
    )

    @Test fun bmrMatchesMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780f, Nutrition.bmr(Sex.MALE, 80f, 180f, 30), 0.01f)
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        assertEquals(1345.25f, Nutrition.bmr(Sex.FEMALE, 60f, 165f, 25), 0.01f)
    }

    @Test fun maintainUsesActivityFactor() {
        val t = Nutrition.targets(base)
        assertEquals(1780, t.bmr)
        assertEquals(2136, t.tdee) // 1780 * 1.2
        assertEquals(2140, t.kcal) // rounded to 10
    }

    @Test fun losingHalfAKiloRemoves550() {
        val t = Nutrition.targets(base.copy(goal = Goal.LOSE, paceKgPerWeek = 0.5f))
        assertEquals(1590, t.kcal) // 2136 - 550 = 1586 -> 1590
    }

    @Test fun targetNeverBelowFloor() {
        val t = Nutrition.targets(base.copy(sex = Sex.FEMALE, weightKg = 45f, heightCm = 150f, age = 70, goal = Goal.LOSE, paceKgPerWeek = 1f))
        assertEquals(1200, t.kcal)
    }

    @Test fun macrosAddUpToTarget() {
        val t = Nutrition.targets(base.copy(goal = Goal.GAIN, paceKgPerWeek = 0.25f))
        val kcal = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9
        assertTrue("macros $kcal vs ${t.kcal}", kotlin.math.abs(kcal - t.kcal) <= 10)
    }

    @Test fun bmi() {
        assertEquals(24.69f, Nutrition.bmi(80f, 180f), 0.01f)
        assertEquals("Healthy range", Nutrition.bmiLabel(24.69f))
    }

    @Test fun goalProgressIsClamped() {
        assertEquals(0.5f, goalProgress(Goal.LOSE, 80f, 77.5f, 75f), 0.001f)
        assertEquals(0f, goalProgress(Goal.LOSE, 80f, 82f, 75f), 0.001f)
        assertEquals(1f, goalProgress(Goal.GAIN, 60f, 70f, 65f), 0.001f)
    }

    @Test fun catalogSearchMatchesAllWords() {
        assertTrue(FoodCatalog.search("basmati rice").any { it.name.startsWith("Basmati") })
        assertTrue(FoodCatalog.search("zzz").isEmpty())
    }

    @Test fun cleanNumbers() {
        assertEquals("80", 80f.clean())
        assertEquals("0.25", 0.25f.clean())
        assertEquals("79.9", 79.9f.clean())
    }

    @Test fun perItemFoodsScaleByCount() {
        val burger = FoodItem("Test burger", "Test", kcal100 = 700f, protein100 = 35f, carbs100 = 50f, fat100 = 40f, servingG = 1f, perItem = true)
        assertEquals(1400f, burger.kcalFor(2f), 0.01f)
        assertEquals(17.5f, burger.proteinFor(0.5f), 0.01f)
        val rice = FoodCatalog.foods.first { it.name.startsWith("Basmati") }
        assertEquals(rice.kcal100 * 2, rice.kcalFor(200f), 0.01f)
    }

    @Test fun parsesBothOpenFoodFactsFormats() {
        val modern = org.json.JSONObject("""{"product_name":"Big Mac","brands":["McDonald's"],"nutriments":{"energy-kcal_100g":228,"proteins_100g":11.6}}""")
        val classic = org.json.JSONObject("""{"product_name":"Cookie","brands":"Brand A, Brand B","nutriments":{"energy_100g":2092}}""")
        assertEquals("McDonald's", OpenFoodFacts.parse(modern)!!.brand)
        assertEquals("Brand A", OpenFoodFacts.parse(classic)!!.brand)
        assertEquals(500f, OpenFoodFacts.parse(classic)!!.kcal100, 0.5f)
    }

    @Test fun parsesAiAnswerWithStrayText() {
        val reply = """Here you go:
            ```json
            {"found": true, "name": "Test Burger", "restaurant": "Test Diner", "serving": "1 burger",
             "kcal": 610, "protein_g": 31, "carbs_g": 45, "fat_g": 33, "confidence": "official",
             "note": "From the diner's guide.", "sources": [{"title": "Guide", "url": "https://example.com/n"}, {"title": "bad", "url": "ftp://x"}]}
            ```"""
        val food = AiLookup.parse(reply)!!
        assertEquals(610f, food.kcal, 0.01f)
        assertEquals("official", food.confidence)
        assertEquals(1, food.sources.size)
        val item = food.toFoodItem()
        assertTrue(item.perItem)
        assertEquals(1220f, item.kcalFor(2f), 0.01f)
    }

    @Test(expected = AiLookupException::class) fun aiNotFoundIsAnError() {
        AiLookup.parse("""{"found": false, "note": "No such item."}""")
    }

    @Test fun aiGarbageReturnsNull() {
        assertEquals(null, AiLookup.parse("sorry, no idea"))
    }
}

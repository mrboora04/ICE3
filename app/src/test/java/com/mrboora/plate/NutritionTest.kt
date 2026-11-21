package com.mrboora.plate

import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.AppUpdates
import com.mrboora.plate.data.CanadianFoods
import com.mrboora.plate.data.FoodRequest
import com.mrboora.plate.data.MenuUpdates
import com.mrboora.plate.data.Spelling
import com.mrboora.plate.data.RestaurantFoods
import com.mrboora.plate.data.Shelves
import com.mrboora.plate.data.FoodCatalog
import com.mrboora.plate.data.FoodEntry
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

    @Test fun canadianFoodsFileParsesAndRanks() {
        val file = java.io.File("src/main/assets/cnf_foods.csv")
        val foods = file.readLines().mapNotNull { CanadianFoods.parseLine(it) }
        assertTrue("expected thousands of foods, got ${foods.size}", foods.size > 5000)
        val apple = CanadianFoods.rank(foods, "apple raw", 5).first()
        assertTrue(apple.name, apple.name.startsWith("Apple, raw"))
        assertTrue(apple.servingG in 100f..250f)
        val quoted = CanadianFoods.parseLine("\"Bread, white\",265,9.0,49.0,3.2,33.0,1 slice")!!
        assertEquals("Bread, white", quoted.name)
        assertEquals(33f, quoted.servingG, 0.01f)
        assertTrue(CanadianFoods.rank(foods, "poutine", 5).isNotEmpty())
    }

    @Test fun onlineResultsMustMatchEveryWord() {
        fun f(name: String, brand: String) = FoodItem(name, brand, 100f, 0f, 0f, 0f)
        // The results from the "papa burger" screenshot: none is a Papa Burger.
        val junk = listOf(f("Rootbeer", "AnW"), f("Papa", "Agro roff"), f("Papas", "McCain"), f("Uovo Cioccolato Fondente", "Papa"))
        assertTrue(OpenFoodFacts.relevant(junk, "papa burger").isEmpty())
        assertTrue(OpenFoodFacts.relevant(junk, "A&W papa burger").isEmpty())
        val good = listOf(f("Root beer", "A&W"), f("Big Mac", "McDonald's"), f("Cheddar slices", "Kraft"))
        assertEquals(listOf("Root beer"), OpenFoodFacts.relevant(good, "a&w root beer").map { it.name })
        assertEquals(listOf("Big Mac"), OpenFoodFacts.relevant(good, "big mac").map { it.name })
        assertEquals(listOf("Big Mac"), OpenFoodFacts.relevant(good, "mcdonalds big mac").map { it.name })
        assertEquals(listOf("Cheddar slices"), OpenFoodFacts.relevant(good, "cheddar slice").map { it.name })
    }

    @Test fun restaurantFileHasChainsWithServings() {
        val foods = java.io.File("src/main/assets/restaurants.csv").readLines().mapNotNull { RestaurantFoods.parseLine(it) }
        assertTrue(foods.size > 200)
        val bigMac = foods.first { it.brand == "McDonald's" && it.name == "Big Mac" }
        assertEquals(219f, bigMac.servingG, 0.5f)
        val kcal = bigMac.kcalFor(bigMac.servingG)
        assertTrue("Big Mac kcal $kcal", kcal in 500f..600f)
        assertTrue(foods.none { it.name.startsWith("McDONALD", ignoreCase = true) })
    }

    @Test fun everyShelfHasFoods() {
        val entries = java.io.File("src/main/assets/cnf_foods.csv").readLines().mapNotNull { CanadianFoods.parseEntry(it) }
        for (s in Shelves.all.filter { !it.restaurants }) {
            val n = entries.count { it.second in s.cnfGroups }
            assertTrue("${s.title} has $n foods", n >= 20)
        }
    }

    @Test fun canadianRestaurantMenusParse() {
        val foods = java.io.File("src/main/assets/canada_restaurants.csv").readLines().mapNotNull { RestaurantFoods.parseCanadianLine(it) }
        assertTrue(foods.size > 1000)
        assertTrue(foods.all { it.perItem })
        val chains = foods.map { it.brand }.toSet()
        for (c in listOf("Tim Hortons", "Popeyes", "McDonald's")) assertTrue("missing $c", c in chains)
        val coffee = foods.first { it.brand == "Tim Hortons" && it.name.startsWith("Original Blend Coffee (Medium") }
        assertEquals(coffee.kcal100 * 2, coffee.kcalFor(2f), 0.01f) // per item: two coffees, double calories
    }

    @Test fun missingMacrosAreMarkedUnknown() {
        val aw = RestaurantFoods.parseCanadianLine("A&W,Chubby Chicken Cruncher,630,26,,29,,1 sandwich,https://web.aw.ca/x")!!
        assertEquals("", aw.unknown)
        assertEquals("c", aw.estimated)
        assertEquals(66.25f, aw.carbsFor(1f), 0.01f) // (630 - 26*4 - 29*9) / 4
        val harveys = RestaurantFoods.parseCanadianLine("Harvey's,Test,410,,,,,1 burger,https://www.harveys.ca/x")!!
        assertEquals("pcf", harveys.unknown)
        assertEquals(410f, harveys.kcalFor(1f), 0.01f)
    }

    @Test fun didYouMeanFixesTypos() {
        val vocab = listOf("Osmow's Chicken on the Rock", "Harvey's Angus Burger", "Tim Hortons Double Double", "Poutine")
            .flatMap(Spelling::words).toSet()
        assertEquals("osmows", Spelling.suggest("osmaos", vocab))
        assertEquals("tim hortons", Spelling.suggest("tim hortnos", vocab))
        assertEquals("harveys burger", Spelling.suggest("harvys burgr", vocab))
        assertEquals(null, Spelling.suggest("poutine", vocab))      // already right
        assertEquals(null, Spelling.suggest("zzzzqqq", vocab))      // nothing close: no wild guesses
    }

    @Test fun requestIsFulfilledWhenItemArrives() {
        val req = FoodRequest(1, "Osmow's", "Chicken on the Rock", 0, false)
        val before = listOf(FoodItem("Big Mac", "McDonald's", 580f, 27f, 46f, 32f))
        val after = before + FoodItem("Chicken on the Rock", "Osmow's", 900f, 50f, 80f, 40f)
        assertTrue(MenuUpdates.fulfilled(listOf(req), before).isEmpty())
        assertEquals(listOf(1L), MenuUpdates.fulfilled(listOf(req), after).map { it.id })
        assertTrue(MenuUpdates.fulfilled(listOf(req.copy(done = true)), after).isEmpty())
    }

    @Test fun estimatedMenusAreFlagged() {
        val est = RestaurantFoods.parseCanadianLine("Amaya,Butter Chicken,600,35,20,40,350,1 bowl,https://example.com/menu,estimate")!!
        assertTrue(est.isEstimate)
        val official = RestaurantFoods.parseCanadianLine("Tim Hortons,Coffee (Medium),4,0,1,0,,Medium,https://example.com/pdf")!!
        assertTrue(!official.isEstimate)
        val rows = java.io.File("src/main/assets/canada_restaurants.csv").readLines().mapNotNull { RestaurantFoods.parseCanadianLine(it) }
        assertTrue(rows.filter { it.brand == "Amaya" }.let { it.isNotEmpty() && it.all { f -> f.isEstimate } })
        assertTrue(rows.filter { it.brand == "Tim Hortons" }.none { it.isEstimate })
    }

    @Test fun latestJsonParsesAndMatchesTheBuild() {
        val info = AppUpdates.parse(java.io.File("../release/latest.json").readText())!!
        val gradle = java.io.File("build.gradle.kts").readText()
        val code = Regex("versionCode = (\\d+)").find(gradle)!!.groupValues[1].toLong()
        val name = Regex("versionName = \"([^\"]+)\"").find(gradle)!!.groupValues[1]
        assertEquals(code, info.versionCode)
        assertEquals(name, info.versionName)
        assertTrue(info.notes.isNotEmpty())
        // Downloads only from this repository.
        assertEquals(null, AppUpdates.parse("""{"versionCode":99,"versionName":"9","apk":"https://evil.example/x.apk"}"""))
    }

    @Test fun addingAPortionScalesTheEntry() {
        val whisky = FoodEntry(day = 0, meal = com.mrboora.plate.data.Meal.SNACK, name = "Whisky", brand = "", grams = 94f,
            kcal = 217f, protein = 0f, carbs = 0f, fat = 0f, portion = 94f)
        val two = whisky.withAmount(whisky.grams + whisky.step)
        assertEquals(188f, two.grams, 0.01f)
        assertEquals(434f, two.kcal, 0.01f)
        assertEquals("2 × 94 g", two.amountText)
        val burger = FoodEntry(day = 0, meal = com.mrboora.plate.data.Meal.LUNCH, name = "Teen Burger", brand = "A&W", grams = 1f,
            kcal = 500f, protein = 27f, carbs = 36f, fat = 30f, unit = "item")
        assertEquals(1f, burger.step, 0f)   // old entries without a portion still step by one item
        assertEquals(54f, burger.withAmount(2f).protein, 0.01f)
    }
}

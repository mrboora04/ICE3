package com.mrboora.plate

import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.WeightEntry
import com.mrboora.plate.data.DaySummary
import com.mrboora.plate.data.weightValue
import com.mrboora.plate.data.kgFrom
import com.mrboora.plate.data.weight
import com.mrboora.plate.data.height
import com.mrboora.plate.data.volume
import com.mrboora.plate.data.mlFrom
import com.mrboora.plate.data.kcalPerWeightUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import com.mrboora.plate.data.AppUpdates
import com.mrboora.plate.data.CanadianFoods
import com.mrboora.plate.data.DayStatus
import com.mrboora.plate.data.History
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
import com.mrboora.plate.data.toItem
import com.mrboora.plate.data.Portions
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
        // The installer's fingerprint travels with it, and the download is checked against it.
        assertEquals(64, info.sha256.length)
        val f = java.io.File.createTempFile("plate", ".apk").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdates.sha256(f))
        f.delete()
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

    @Test fun dayStatusAndStreak() {
        assertEquals(DayStatus.ON_TARGET, History.status(1900f, 2000))
        assertEquals(DayStatus.ON_TARGET, History.status(2100f, 2000))   // up to 5% over
        assertEquals(DayStatus.OVER, History.status(2200f, 2000))
        assertEquals(DayStatus.UNDER, History.status(1300f, 2000))      // under 70%
        assertEquals(DayStatus.NOT_LOGGED, History.status(0f, 2000))
        fun d(day: Long, s: DayStatus) = com.mrboora.plate.data.DaySummary(day, 0f, 0f, 0f, 0f, 0, null, emptyList(), s)
        // Newest first: on, (gap), on, over, on  -> streak 2 (the gap doesn't break it, the over day does)
        assertEquals(2, History.streak(listOf(d(10, DayStatus.ON_TARGET), d(9, DayStatus.NOT_LOGGED), d(8, DayStatus.ON_TARGET), d(7, DayStatus.OVER), d(6, DayStatus.ON_TARGET))))
        assertEquals(0, History.streak(listOf(d(10, DayStatus.OVER), d(9, DayStatus.ON_TARGET))))
    }

    @Test
    fun portionHelperUsesTheFoodsOwnMeasureOrATypicalOne() {
        // Health Canada lists cooked rice as 250 ml = 195 g, so a cup is exactly 195 g.
        val rice = FoodItem("Rice, white, cooked", kcal100 = 130f, protein100 = 2.7f, carbs100 = 28f, fat100 = 0.3f, servingG = 195f, servingLabel = "250 ml")
        val cup = Portions.kitchen(rice).first { it.name == "1 cup" }
        assertEquals(195f, cup.grams, 0.01f)
        assertTrue(cup.exact)
        assertEquals(195f, Portions.hand(rice).first { it.name == "Fist" }.grams, 0.01f)

        // No ml listing: a tablespoon of oil uses oil's typical density.
        val oil = FoodItem("Olive oil", kcal100 = 884f, protein100 = 0f, carbs100 = 0f, fat100 = 100f)
        val tbsp = Portions.kitchen(oil).first { it.name == "1 tbsp" }
        assertEquals(14f, tbsp.grams, 0.01f) // 15 ml × 0.92, rounded
        assertFalse(tbsp.exact)

        // Drinks get glasses and shots, not spoons or hands.
        val whisky = FoodItem("Whisky", kcal100 = 250f, protein100 = 0f, carbs100 = 0f, fat100 = 0f)
        assertTrue(Portions.isDrink(whisky))
        assertEquals("1 shot", Portions.kitchen(whisky).first().name)
        assertEquals(43f, Portions.kitchen(whisky).first().grams, 0.01f)
        assertEquals("ml", Portions.kitchen(whisky).first().unit)
        assertTrue(Portions.hand(whisky).isEmpty())
        assertFalse(Portions.isDrink(FoodItem("Popcorn, air-popped", kcal100 = 387f, protein100 = 13f, carbs100 = 78f, fat100 = 4.5f)))
        assertFalse(Portions.isDrink(FoodItem("Watermelon, raw", kcal100 = 30f, protein100 = 0.6f, carbs100 = 7.6f, fat100 = 0.2f, servingG = 160f, servingLabel = "250 ml diced")))

        // Counted foods have no portion helper.
        assertTrue(Portions.kitchen(FoodItem("Samosa", kcal100 = 250f, protein100 = 5f, carbs100 = 30f, fat100 = 12f, perItem = true)).isEmpty())
    }

    @Test
    fun selfiesStayOffBackups() {
        val dir = java.io.File("src/main/res/xml")
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val xml = java.io.File(dir, name).readText()
            assertTrue("$name must exclude selfies", xml.contains("""path="selfies/""""))
        }
        // Both cloud backup and phone-to-phone transfer.
        val rules = java.io.File(dir, "data_extraction_rules.xml").readText()
        assertEquals(2, Regex("""path="selfies/"""").findAll(rules).count())
    }

    @Test
    fun forecastReasonsFromIntakeAndBurn() {
        val p = Profile(name = "A", sex = Sex.MALE, age = 30, heightCm = 178f, weightKg = 85f, goalWeightKg = 78f,
            activity = Activity.LIGHT, goal = Goal.LOSE, paceKgPerWeek = 0.5f)
        val today = 20_000L
        val burn = com.mrboora.plate.data.Forecast.burn(p, 85f)
        // (10·85 + 6.25·178 − 5·30 + 5) × 1.375
        assertEquals((850f + 1112.5f - 150f + 5f) * 1.375f, burn, 0.5f)

        // Eating 550 below burn ≈ 0.5 kg a week at first.
        val f = com.mrboora.plate.data.Forecast.run(p, 85f, burn - 550f, today)
        assertEquals(-0.5f, f.kgPerWeek, 0.01f)
        assertTrue(f.in4Weeks < 85f && f.in12Weeks < f.in4Weeks)
        // Slows down as burn drops: 12 weeks is less than 12 × the first week's pace.
        assertTrue(85f - f.in12Weeks < 12 * 0.5f)
        assertTrue(f.goalDay != null && f.goalDay!! > today)
        // Settling point: where burn at that weight equals intake.
        assertEquals(burn - 550f, com.mrboora.plate.data.Forecast.burn(p, f.settlesKg), 0.5f)

        // Eating above burn never reaches a lower goal.
        assertEquals(null, com.mrboora.plate.data.Forecast.run(p, 85f, burn + 200f, today).goalDay)

        // Average skips today and unlogged days, needs 3 days.
        fun d(day: Long, kcal: Float) = DaySummary(day, kcal, 0f, 0f, 0f, 0, null, emptyList(), DayStatus.ON_TARGET)
        assertEquals(null, com.mrboora.plate.data.Forecast.averageIntake(listOf(d(today - 1, 2000f), d(today - 2, 1800f)), today))
        val avg = com.mrboora.plate.data.Forecast.averageIntake(
            listOf(d(today, 500f), d(today - 1, 2000f), d(today - 2, 1800f), d(today - 3, 0f), d(today - 4, 2200f)), today,
        )!!
        assertEquals(2000f, avg.first, 0.01f)
        assertEquals(3, avg.second)

        // Scale trend: −1 kg over 14 days = −0.5 kg/week.
        val trend = com.mrboora.plate.data.Forecast.scaleTrend(
            listOf(WeightEntry(today - 14, 86f), WeightEntry(today - 7, 85.5f), WeightEntry(today, 85f)), today,
        )!!
        assertEquals(-0.5f, trend, 0.01f)
    }

    @Test
    fun todayForecastUsesRealIntakeAndLoggedWorkouts() {
        val p = Profile(name = "A", sex = Sex.MALE, age = 30, heightCm = 178f, weightKg = 85f, goalWeightKg = 78f,
            activity = Activity.VERY, goal = Goal.LOSE, paceKgPerWeek = 0.5f)
        val life = com.mrboora.plate.data.Forecast.dailyLife(p, 85f)
        // Activity level is ignored: resting × 1.2 only, workouts come from the log.
        assertEquals((850f + 1112.5f - 150f + 5f) * 1.2f, life, 0.5f)
        val run = com.mrboora.plate.data.Workouts.kinds.first { it.name == "Run" }
        // (9.8 − 1) × 85 kg × 0.5 h
        assertEquals(374, com.mrboora.plate.data.Workouts.kcal(run, 30, 85f))
        val f = com.mrboora.plate.data.Forecast.today(p, 85f, eaten = 1800f, workoutKcal = 374f, today = 20_000L)
        assertEquals(1800f - (life + 374f), f.balance, 0.5f)
    }

    @Test
    fun shakeBuilderIsPlainArithmetic() {
        val banana = FoodItem("Banana, raw", kcal100 = 89f, protein100 = 1.1f, carbs100 = 22.8f, fat100 = 0.3f, servingG = 118f, servingLabel = "1 medium (18cm to 20cm long)")
        val berries = com.mrboora.plate.data.Mix.mixedBerries(listOf(
            FoodItem("Blueberry, raw", kcal100 = 57f, protein100 = 0.7f, carbs100 = 14.5f, fat100 = 0.3f),
            FoodItem("Raspberry, raw", kcal100 = 53f, protein100 = 1.2f, carbs100 = 11.9f, fat100 = 0.7f),
            FoodItem("Strawberry, raw", kcal100 = 32f, protein100 = 0.7f, carbs100 = 7.7f, fat100 = 0.3f),
        ))!!
        assertEquals((57f + 53f + 32f) / 3f, berries.kcal100, 0.01f)
        val milk = FoodItem("Milk, fluid, whole, pasteurized, homogenized, 3.25% M.F.", kcal100 = 59f, protein100 = 3.3f, carbs100 = 4.5f, fat100 = 3.1f, servingG = 257.8f, servingLabel = "250 ml")
        // Request #4: 3 bananas, 30 g mixed berries, a glass of milk.
        val threeBananas = com.mrboora.plate.data.Mix.quickAmounts(banana)[2]
        assertEquals("3 medium", threeBananas.first)
        assertEquals(354f, threeBananas.second, 0.01f)
        val milkG = com.mrboora.plate.data.Mix.mlToGrams(milk, 250f)
        assertEquals(257.8f, milkG, 0.1f)
        val parts = listOf(
            com.mrboora.plate.data.Mix.Part(banana, 354f), com.mrboora.plate.data.Mix.Part(berries, 30f),
            com.mrboora.plate.data.Mix.Part(milk, milkG),
        )
        val expected = 354f * 89f / 100f + 30f * berries.kcal100 / 100f + milkG * 59f / 100f
        val glass = com.mrboora.plate.data.Mix.build("Banana & mixed berry shake", parts, 1)
        assertEquals(expected, glass.kcal100, 0.01f)
        assertTrue(glass.perItem && glass.isEstimate && glass.brand == "Homemade")
        assertEquals(expected / 2f, com.mrboora.plate.data.Mix.build("x", parts, 2).kcal100, 0.01f)
        assertEquals("Banana & mixed berry shake", com.mrboora.plate.data.Mix.shakeName(listOf("Banana", "Mixed berries")))

        // Requests: a homemade shake is done as soon as the builder exists; a restaurant shake isn't.
        val shake = FoodRequest(1, "", "3 banana 30g 4 blend berry shake", 0, false)
        val chain = FoodRequest(2, "Osmow's", "mango shake", 0, false)
        assertEquals(listOf(shake), MenuUpdates.fulfilled(listOf(shake, chain), emptyList()))
    }

    @Test
    fun onlyPlateInstallersAreDeleted() {
        assertTrue(AppUpdates.isPlateInstaller("Plate-2.6.0.apk"))
        assertTrue(AppUpdates.isPlateInstaller("Plate.apk"))
        assertTrue(AppUpdates.isPlateInstaller("Plate-2.7.0-1.apk"))
        assertFalse(AppUpdates.isPlateInstaller("WhatsApp.apk"))
        assertFalse(AppUpdates.isPlateInstaller("Plate-2.6.0.apk.pdf"))
        assertFalse(AppUpdates.isPlateInstaller("my plate photo.jpg"))
    }

    @Test
    fun waterContainersRoundTrip() {
        val W = com.mrboora.plate.data.WaterContainers
        val list = listOf(com.mrboora.plate.data.WaterContainers.Container("Bottle", 500), com.mrboora.plate.data.WaterContainers.Container("Steel | mug:", 350))
        val back = W.decode(W.encode(list))
        assertEquals(listOf(500, 350), back.map { it.ml })
        assertEquals("Steel   mug", back[1].name)
        assertEquals(listOf("½ Bottle" to 250), W.halves(list).map { it.name to it.ml })
        assertTrue(W.decode("junk|:12|x:99999").isEmpty())
        assertEquals(listOf(250, 500), W.defaults.map { it.ml })
        // One list of buttons for the app and the widgets: whole containers, then halves.
        assertEquals(listOf("+ Glass 250 ml", "+ Bottle 500 ml", "½ Bottle"), W.buttons(W.defaults).map { it.label })
        // Drops: one per 250 ml of the goal, shared by the app and the widgets.
        assertEquals(12, W.drops(3000)); assertEquals(5, W.filledDrops(1250)); assertEquals(16, W.drops(9000)); assertEquals(1, W.drops(100))
    }

    @Test
    fun countedFoodsAreCountedNotMeasured() {
        fun f(label: String, g: Float = 50f) = FoodItem("x", kcal100 = 100f, protein100 = 0f, carbs100 = 0f, fat100 = 0f, servingG = g, servingLabel = label)
        assertEquals("large", Portions.countUnit(f("1 large")))                          // a boiled egg
        assertEquals("roti", Portions.countUnit(f("1 roti", 40f)))
        assertEquals("medium", Portions.countUnit(f("1 medium (18cm to 20cm long)", 118f)))  // a banana
        assertEquals("slice", Portions.countUnit(f("1 slice", 28f)))
        assertEquals(null, Portions.countUnit(f("1 cup", 160f)))                          // rice is measured
        assertEquals(null, Portions.countUnit(f("1 bowl", 200f)))                         // dal is measured
        assertEquals(null, Portions.countUnit(f("250 ml", 250f)))
        assertEquals(null, Portions.countUnit(f("")))
        // A saved egg without a serving name gets "1 large" back from Plate's list.
        val saved = com.mrboora.plate.data.SavedFood("egg, boiled|", "Egg, boiled", "", 155f, 13f, 1f, 11f, 50f, false, 0L)
        assertEquals("large", Portions.countUnit(saved.toItem()))
    }

    @Test
    fun densityReadsKitchenLabelsAndNutButters() {
        // Plate's own peanut butter: "2 tbsp" = 32 g, so 32 g / 30 ml.
        val pb = FoodItem("Peanut butter", kcal100 = 588f, protein100 = 25f, carbs100 = 20f, fat100 = 50f, servingG = 32f, servingLabel = "2 tbsp")
        val (d, exact) = Portions.density(pb)
        assertEquals(32f / 30f, d, 0.001f); assertTrue(exact)
        // No label: a nut butter is a paste (1.08), not loose nuts (0.6).
        assertEquals(1.08f, Portions.density(FoodItem("Almond butter", kcal100 = 600f, protein100 = 20f, carbs100 = 20f, fat100 = 55f)).first, 0.001f)
        assertEquals(0.6f, Portions.density(FoodItem("Almonds", kcal100 = 579f, protein100 = 21f, carbs100 = 22f, fat100 = 50f)).first, 0.001f)
        // A cup label: rice "1 cup" = 160 g → 0.64 g/ml.
        assertEquals(0.64f, Portions.density(FoodItem("Basmati rice, cooked", kcal100 = 121f, protein100 = 3f, carbs100 = 25f, fat100 = 0f, servingG = 160f, servingLabel = "1 cup")).first, 0.001f)
    }

    @Test
    fun forecastUsesTheAverageOfFinishedDays() {
        val today = 20_000L
        fun d(day: Long, kcal: Float) = DaySummary(day, kcal, 0f, 0f, 0f, 0, null, emptyList(), DayStatus.ON_TARGET)
        val hist = listOf(d(today, 233f), d(today - 1, 2000f), d(today - 2, 1600f), d(today - 3, 0f))
        val b = com.mrboora.plate.data.Forecast.basis(hist, mapOf(today - 1 to 300), today, todayEaten = 233f, todayWorkout = 0f)
        // Today (unfinished) and the unlogged day aren't counted: (2000 + 1600) / 2.
        assertEquals(1800f, b.intake, 0.01f)
        assertEquals(150f, b.workout, 0.01f)
        assertEquals(2, b.days)
        // First day ever: today so far.
        val first = com.mrboora.plate.data.Forecast.basis(listOf(d(today, 233f)), emptyMap(), today, 233f, 0f)
        assertTrue(first.todayOnly); assertEquals(233f, first.intake, 0.01f)
    }

    @Test
    fun spoonFoodsGetASpoonPicker() {
        val pb = FoodItem("Peanut butter", kcal100 = 588f, protein100 = 25f, carbs100 = 20f, fat100 = 50f, servingG = 32f, servingLabel = "2 tbsp")
        assertTrue(Portions.spoonFood(pb))
        assertTrue(Portions.spoonFood(FoodItem("Ghee", kcal100 = 900f, protein100 = 0f, carbs100 = 0f, fat100 = 100f, servingG = 5f, servingLabel = "1 tsp")))
        assertTrue(Portions.spoonFood(FoodItem("Honey", kcal100 = 304f, protein100 = 0f, carbs100 = 82f, fat100 = 0f)))
        assertFalse(Portions.spoonFood(FoodItem("Basmati rice, cooked", kcal100 = 121f, protein100 = 3f, carbs100 = 25f, fat100 = 0f, servingG = 160f, servingLabel = "1 cup")))
        assertFalse(Portions.spoonFood(FoodItem("Egg, boiled", kcal100 = 155f, protein100 = 13f, carbs100 = 1f, fat100 = 11f, servingG = 50f, servingLabel = "1 large")))
        assertFalse(Portions.spoonFood(FoodItem("Milk, 2%", kcal100 = 50f, protein100 = 3f, carbs100 = 5f, fat100 = 2f, servingG = 250f, servingLabel = "1 cup")))
        // 2 tablespoons of peanut butter: 2 × 15 ml × (32 g / 30 ml) = 32 g.
        assertEquals(32f, Portions.spoonGrams(pb, 15f, 2), 0.01f)
    }

    @Test
    fun sizesAndCountsComeFromTheFood() {
        // A medium egg (Health Canada: 45.6 g) is Regular; Small and Large are a step either side.
        val egg = FoodItem("Egg, chicken, whole, fresh or frozen, raw", kcal100 = 152f, protein100 = 12.3f, carbs100 = 0.8f, fat100 = 10.6f, servingG = 45.6f, servingLabel = "1 medium egg")
        val p = Portions.picks(egg)!!
        assertEquals(listOf("Small", "Regular", "Large"), p.map { it.label })
        assertEquals(45.6f, p[1].each, 0.01f)
        assertTrue(p[0].each < p[1].each && p[1].each < p[2].each)
        assertEquals("egg", Portions.noun(egg))
        // 4 regular eggs back from their grams.
        assertEquals(1 to 4, Portions.nearest(p, 45.6f * 4))
        // A "1 large" listing is the Large one.
        val large = FoodItem("Egg, boiled", kcal100 = 155f, protein100 = 13f, carbs100 = 1f, fat100 = 11f, servingG = 50f, servingLabel = "1 large")
        assertEquals(50f, Portions.picks(large)!![2].each, 0.01f)
        // Spoon foods: teaspoon · tablespoon · heaped, from the food's own density (32 g per 2 tbsp).
        val pb = FoodItem("Peanut butter", kcal100 = 588f, protein100 = 25f, carbs100 = 20f, fat100 = 50f, servingG = 32f, servingLabel = "2 tbsp")
        Portions.picks(pb)!!.map { it.each }.zip(listOf(16f / 3f, 16f, 24f)).forEach { (a, b) -> assertEquals(b, a, 0.01f) }
        // Measured foods keep grams.
        assertNull(Portions.picks(FoodItem("Basmati rice, cooked", kcal100 = 121f, protein100 = 3f, carbs100 = 25f, fat100 = 0f, servingG = 160f, servingLabel = "250 ml")))
    }

    @Test
    fun eggTypesComeFromTheFoodFile() {
        val whole = FoodItem("Egg, chicken, whole, fresh or frozen, raw", kcal100 = 152f, protein100 = 12.3f, carbs100 = 0.8f, fat100 = 10.6f, servingG = 45.6f, servingLabel = "1 medium egg")
        val white = FoodItem("Egg, chicken, white, fresh or frozen, raw", kcal100 = 46f, protein100 = 10f, carbs100 = 0.6f, fat100 = 0f, servingG = 31.5f, servingLabel = "1 medium egg")
        val dried = FoodItem("Egg, chicken, white, dried", kcal100 = 382f, protein100 = 81f, carbs100 = 7.8f, fat100 = 0f, servingG = 100f, servingLabel = "")
        val cooked = FoodItem("Egg, chicken, white, fresh or frozen, cooked", kcal100 = 48f, protein100 = 10f, carbs100 = 1.2f, fat100 = 0f, servingG = 33f, servingLabel = "1 large egg white")
        val file = listOf(whole, white, dried, cooked)
        val t = Portions.types(whole) { pre -> file.filter { it.name.startsWith(pre) } }!!
        assertEquals(listOf("Whole", "White"), t.map { it.first })
        assertSame(white, t[1].second)
        assertNull(Portions.types(FoodItem("Banana", kcal100 = 89f, protein100 = 1f, carbs100 = 23f, fat100 = 0f)) { file })
    }

    @Test
    fun unitsConvertBothWaysWithoutDrift() {
        val lb = com.mrboora.plate.data.Units.Prefs(com.mrboora.plate.data.Units.Weight.LB, com.mrboora.plate.data.Units.Height.FT_IN, com.mrboora.plate.data.Units.Volume.FL_OZ)
        val metric = com.mrboora.plate.data.Units.Prefs()
        // 110 kg = 242.5 lb; typing 242.5 back gives 110 kg (to 0.1).
        assertEquals(242.5f, lb.weightValue(110f), 0.001f)
        assertEquals(110f, lb.kgFrom(lb.weightValue(110f)), 0.05f)
        assertEquals("242.5 lb", lb.weight(110f))
        assertEquals("110 kg", metric.weight(110f))
        // 180 cm = 5′ 11″, and back within an inch.
        assertEquals("5′ 11″", lb.height(180f))
        assertEquals(180.3f, com.mrboora.plate.data.cmFrom(5f, 11f), 0.1f)
        // 500 ml bottle = 16.9 fl oz; typing 16.9 back = 500 ml.
        assertEquals("16.9 fl oz", lb.volume(500))
        assertEquals(500, lb.mlFrom(16.9f))
        assertEquals("1,250 ml", metric.volume(1250))
        // 7,700 kcal per kg is about 3,500 per lb, worked out, not typed in.
        assertEquals(3500f, lb.kcalPerWeightUnit(), 0.1f)
        // The widget and water card buttons say fl oz too.
        assertEquals("+ Bottle 16.9 fl oz", com.mrboora.plate.data.WaterContainers.buttons(listOf(com.mrboora.plate.data.WaterContainers.Container("Bottle", 500)), lb).first().label)
        // The US uses lb, Canada and India metric (from the phone's region).
        assertEquals(com.mrboora.plate.data.Units.Weight.LB, com.mrboora.plate.data.Units.phoneDefault(java.util.Locale.US).weight)
        assertEquals(com.mrboora.plate.data.Units.Weight.KG, com.mrboora.plate.data.Units.phoneDefault(java.util.Locale.CANADA).weight)
        assertEquals(com.mrboora.plate.data.Units.Weight.KG, com.mrboora.plate.data.Units.phoneDefault(java.util.Locale("en", "IN")).weight)
    }

    @Test
    fun requestRules() {
        val R = com.mrboora.plate.data.Requests
        // Anything specific is enough on its own.
        assertNull(R.problem("", "", "Dragon fruit smoothie bowl"))
        // A plain food on its own is enough (most foods aren't from a restaurant); so is a menu item.
        assertNull(R.problem("", "banana skin", ""))
        assertNull(R.problem("Tim Hortons", "Iced capp", ""))
        // A plain food request is tracked, and found in the Ingredients rows once added.
        val skin = FoodRequest(9, "", "banana skin", 0, false)
        assertTrue(skin.trackable)
        val row = FoodItem("Banana Skin", brand = "Ingredients", kcal100 = 70f, protein100 = 1f, carbs100 = 19f, fat100 = 0.3f)
        assertEquals(listOf(skin), com.mrboora.plate.data.MenuUpdates.fulfilled(listOf(skin), listOf(row)))
        // A restaurant alone asks for its menu; an empty form can't be sent.
        assertNull(R.problem("Osmow's", "", ""))
        assertTrue(R.problem("", "", "") != null)
        // The phone can recognise named requests by itself; one with only details is read by a person.
        assertTrue(FoodRequest(1, "Osmow's", "", 0, false).trackable)
        assertFalse(FoodRequest(2, "", "", 0, false, "a homemade dosa").trackable)
        assertEquals("a homemade dosa", FoodRequest(2, "", "", 0, false, "a homemade dosa").label)
    }

    @Test
    fun requestsForgiveTyposAndSabjisAreReadyAtOnce() {
        assertTrue(com.mrboora.plate.data.Spelling.close("garaj", "gajar"))
        assertTrue(com.mrboora.plate.data.Spelling.close("gajer", "gajar"))
        assertFalse(com.mrboora.plate.data.Spelling.close("aloo", "gajar"))
        // A typo in a restaurant request still finds the row once it's added.
        val row = FoodItem("Iced Capp", brand = "Tim Hortons", kcal100 = 100f, protein100 = 1f, carbs100 = 20f, fat100 = 2f)
        val req = FoodRequest(1, "Tim Hortons", "iced cpap", 0, false)
        assertEquals(listOf(req), com.mrboora.plate.data.MenuUpdates.fulfilled(listOf(req), listOf(row)))
        // A sabji is made in the builder: ready at once, even when only described.
        val sabji = FoodRequest(2, "", "", 0, false, "aloo garaj sabji")
        assertTrue(sabji.homemade && sabji.trackable)
        assertEquals(listOf(sabji), com.mrboora.plate.data.MenuUpdates.fulfilled(listOf(sabji), emptyList()))
    }
}

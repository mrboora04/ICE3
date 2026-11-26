package com.mrboora.plate

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.layout.padding
import com.mrboora.plate.data.FoodItem
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.DayTotal
import com.mrboora.plate.data.FoodCatalog
import com.mrboora.plate.data.FoodEntry
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Meal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Sex
import com.mrboora.plate.data.WeightEntry
import com.mrboora.plate.ui.screens.AddFoodScreen
import com.mrboora.plate.ui.screens.OnboardingScreen
import com.mrboora.plate.ui.screens.ProfileScreen
import com.mrboora.plate.ui.screens.ProgressScreen
import com.mrboora.plate.ui.screens.TodayScreen
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PlateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders each screen with sample data to docs/screenshots so the design can be checked without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val profile = Profile(
        name = "Vishal", sex = Sex.MALE, age = 27, heightCm = 175f, weightKg = 82f, goalWeightKg = 74f,
        activity = Activity.LIGHT, goal = Goal.LOSE, paceKgPerWeek = 0.5f, waterGoalMl = 2500,
    )
    private val today = LocalDate.now().toEpochDay()
    private val weights = listOf(82f, 81.6f, 81.7f, 81.1f, 80.6f, 80.8f, 80.2f, 79.9f)
        .mapIndexed { i, kg -> WeightEntry(today - (7 - i) * 4L, kg) }
    private val current = weights.last().kg
    private val targets = Nutrition.targets(profile.copy(weightKg = current))

    private fun entry(meal: Meal, name: String, g: Float): FoodEntry {
        val f = FoodCatalog.foods.first { it.name == name }
        return FoodEntry(0, today, meal, f.name, "", g, f.kcalFor(g), f.proteinFor(g), f.carbsFor(g), f.fatFor(g))
    }

    private val entries = listOf(
        entry(Meal.BREAKFAST, "Poha", 200f), entry(Meal.BREAKFAST, "Chai with milk & sugar", 150f),
        entry(Meal.LUNCH, "Roti / chapati", 80f), entry(Meal.LUNCH, "Dal (lentil curry)", 220f),
        entry(Meal.LUNCH, "Curd / dahi", 120f), entry(Meal.SNACK, "Banana", 120f),
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PlateTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Plate.colors.ground)) { content() } }
        }
        rule.mainClock.advanceTimeBy(4000)
        // Let pictures loaded off the main thread arrive, then draw one more frame.
        Thread.sleep(800)
        rule.mainClock.advanceTimeBy(100)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val pad = PaddingValues(top = 32.dp_, bottom = 16.dp_)

    @Test fun profile() = shot("profile") {
        ProfileScreen(profile, targets, current, weights.first().kg, pad, {}, {})
    }

    @Test fun profileDark() = shot("profile_dark", dark = true) {
        ProfileScreen(profile, targets, current, weights.first().kg, pad, {}, {})
    }

    @Test fun today() = shot("today") {
        TodayScreen(profile, targets, today, entries, 1250, pad, {}, {}, {}, {}, {})
    }

    @Test fun progress() = shot("progress") {
        // A week of days: kcal per day (0 = nothing logged), to show every kind of card.
        val kcals = listOf(1650f, 2310f, 1820f, 0f, 1240f, 1900f, 1760f)
        val sample = kcals.mapIndexed { i, k -> entries.map { it.copy(day = today - i) }.let { list ->
            val f = if (k == 0f) 0f else k / list.sumOf { it.kcal.toDouble() }.toFloat()
            list.map { it.copy(kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f, fat = it.fat * f) }.filter { k > 0f }
        } }.flatten()
        val history = com.mrboora.plate.data.History.summarise(
            sample, listOf(com.mrboora.plate.data.Water(today, 1750)), weights, targets.kcal,
        )
        ProgressScreen(profile, targets, weights, history, pad, {}, {})
    }
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun progressDays() = shot("progress_days") {
        // A week of days: kcal per day (0 = nothing logged), to show every kind of card.
        val kcals = listOf(1650f, 2310f, 1820f, 0f, 1240f, 1900f, 1760f)
        val sample = kcals.mapIndexed { i, k -> entries.map { it.copy(day = today - i) }.let { list ->
            val f = if (k == 0f) 0f else k / list.sumOf { it.kcal.toDouble() }.toFloat()
            list.map { it.copy(kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f, fat = it.fat * f) }.filter { k > 0f }
        } }.flatten()
        val history = com.mrboora.plate.data.History.summarise(
            sample, listOf(com.mrboora.plate.data.Water(today, 1750)), weights, targets.kcal,
        )
        ProgressScreen(profile, targets, weights, history, pad, {}, {})
    }

    /** Day cards with that day's selfie on the left: blurred, and one tapped open. */
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun progressSelfies() = shot("progress_selfies") {
        listOf(today, today - 1, today - 4).forEach(::fakeSelfie)
        // A week of days: kcal per day (0 = nothing logged), to show every kind of card.
        val kcals = listOf(1650f, 2310f, 1820f, 0f, 1240f, 1900f, 1760f)
        val sample = kcals.mapIndexed { i, k -> entries.map { it.copy(day = today - i) }.let { list ->
            val f = if (k == 0f) 0f else k / list.sumOf { it.kcal.toDouble() }.toFloat()
            list.map { it.copy(kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f, fat = it.fat * f) }.filter { k > 0f }
        } }.flatten()
        val history = com.mrboora.plate.data.History.summarise(
            sample, listOf(com.mrboora.plate.data.Water(today, 1750)), weights, targets.kcal,
        )
        ProgressScreen(profile, targets, weights, history, pad, {}, {}, selfieDays = setOf(today, today - 1, today - 4), selfiesUnlocked = true, selfiesOpen = setOf(today - 1))
    }
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun progressDaysDark() = shot("progress_days_dark", dark = true) {
        // A week of days: kcal per day (0 = nothing logged), to show every kind of card.
        val kcals = listOf(1650f, 2310f, 1820f, 0f, 1240f, 1900f, 1760f)
        val sample = kcals.mapIndexed { i, k -> entries.map { it.copy(day = today - i) }.let { list ->
            val f = if (k == 0f) 0f else k / list.sumOf { it.kcal.toDouble() }.toFloat()
            list.map { it.copy(kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f, fat = it.fat * f) }.filter { k > 0f }
        } }.flatten()
        val history = com.mrboora.plate.data.History.summarise(
            sample, listOf(com.mrboora.plate.data.Water(today, 1750)), weights, targets.kcal,
        )
        ProgressScreen(profile, targets, weights, history, pad, {}, {})
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun progressTall() = shot("progress_tall") {
        // A week of days: kcal per day (0 = nothing logged), to show every kind of card.
        val kcals = listOf(1650f, 2310f, 1820f, 0f, 1240f, 1900f, 1760f)
        val sample = kcals.mapIndexed { i, k -> entries.map { it.copy(day = today - i) }.let { list ->
            val f = if (k == 0f) 0f else k / list.sumOf { it.kcal.toDouble() }.toFloat()
            list.map { it.copy(kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f, fat = it.fat * f) }.filter { k > 0f }
        } }.flatten()
        val history = com.mrboora.plate.data.History.summarise(
            sample, listOf(com.mrboora.plate.data.Water(today, 1750)), weights, targets.kcal,
        )
        ProgressScreen(profile, targets, weights, history, pad, {}, {})
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun progressTallDark() = shot("progress_tall_dark", dark = true) {
        // A week of days: kcal per day (0 = nothing logged), to show every kind of card.
        val kcals = listOf(1650f, 2310f, 1820f, 0f, 1240f, 1900f, 1760f)
        val sample = kcals.mapIndexed { i, k -> entries.map { it.copy(day = today - i) }.let { list ->
            val f = if (k == 0f) 0f else k / list.sumOf { it.kcal.toDouble() }.toFloat()
            list.map { it.copy(kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f, fat = it.fat * f) }.filter { k > 0f }
        } }.flatten()
        val history = com.mrboora.plate.data.History.summarise(
            sample, listOf(com.mrboora.plate.data.Water(today, 1750)), weights, targets.kcal,
        )
        ProgressScreen(profile, targets, weights, history, pad, {}, {})
    }

    @Test fun addFood() = shot("add_food") {
        AddFoodScreen(
            Meal.LUNCH, "Today", FoodCatalog.foods.filter { it.name in setOf("Roti / chapati", "Dal (lentil curry)", "Paneer", "Basmati rice, cooked") },
            { emptyList() }, { Result.success(emptyList()) }, { Result.success(null) }, { emptyList() }, { emptyList() }, { emptyList() }, { null }, { _, _, _ -> }, {}, {}, { _, _ -> }, {},
        )
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun todayTall() = shot("today_tall") {
        TodayScreen(profile, targets, today, entries, 1250, pad, {}, {}, {}, {}, {})
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun todayTallDark() = shot("today_tall_dark", dark = true) {
        TodayScreen(profile, targets, today, entries, 1250, pad, {}, {}, {}, {}, {})
    }

    @Test fun portionHelper() = shot("portion_helper") {
        val rice = FoodItem("Rice, white, long-grain, cooked", kcal100 = 130f, protein100 = 2.7f, carbs100 = 28f, fat100 = 0.3f, servingG = 195f, servingLabel = "250 ml")
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp_)) {
            androidx.compose.material3.Text(rice.name, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            com.mrboora.plate.ui.screens.PortionHelper(rice, 195f) {}
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp_))
            com.mrboora.plate.ui.screens.PortionHelper(FoodItem("Whisky", kcal100 = 250f, protein100 = 0f, carbs100 = 0f, fat100 = 0f), null) {}
        }
    }

    /** Compare lines photos up by the eyes: the after photo's eyes land on the before photo's. */
    @Test fun selfiesLineUp() {
        val A = com.mrboora.plate.data.SelfieAlign
        // Before: eyes at the middle, 40% down, 20% of the width apart, in a 600 × 750 photo.
        val before = com.mrboora.plate.data.SelfieAlign.Eyes(0.5f, 0.4f, 0.2f)
        // After: further away and off to the left (eyes 10% apart, at 30% across, 50% down).
        val after = com.mrboora.plate.data.SelfieAlign.Eyes(0.3f, 0.5f, 0.1f)
        val view = 400f to 500f
        val b = A.fill(600, 750, view.first, view.second)
        val p = A.match(before, 600, 750, after, 600, 750, view.first, view.second)!!
        // Eyes land on the same spot...
        org.junit.Assert.assertEquals(b.dx + 0.5f * 600 * b.scale, p.dx + 0.3f * 600 * p.scale, 0.5f)
        org.junit.Assert.assertEquals(b.dy + 0.4f * 750 * b.scale, p.dy + 0.5f * 750 * p.scale, 0.5f)
        // ...the same distance apart (so the after photo is drawn twice as big).
        org.junit.Assert.assertEquals(0.2f * 600 * b.scale, 0.1f * 600 * p.scale, 0.5f)
        // No face in one photo: nothing to line up.
        org.junit.Assert.assertNull(A.match(null, 600, 750, after, 600, 750, 400f, 500f))
        // Face photo vs body photo.
        org.junit.Assert.assertEquals(com.mrboora.plate.data.SelfieAlign.Kind.FACE, A.kind(before))
        org.junit.Assert.assertEquals(com.mrboora.plate.data.SelfieAlign.Kind.BODY, A.kind(com.mrboora.plate.data.SelfieAlign.Eyes(0.5f, 0.15f, 0.04f)))
        org.junit.Assert.assertEquals(com.mrboora.plate.data.SelfieAlign.Kind.BODY, A.kind(null))
    }

    /** A stand-in photo: warm gradient with a soft face-like circle. */
    private fun fakeSelfie(day: Long) {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = Bitmap.createBitmap(600, 750, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 600f, 750f, 0xFFF3C6B4.toInt(), 0xFFB7A3D9.toInt(), android.graphics.Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, 600f, 750f, p)
        p.shader = null; p.color = 0xFF8A5A44.toInt(); cv.drawCircle(300f, 300f, 150f, p)
        p.color = 0xFF3A2230.toInt(); cv.drawRect(130f, 520f, 470f, 750f, p)
        com.mrboora.plate.data.Selfies.file(ctx, day).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    @Test @Config(qualifiers = "w411dp-h1400dp-xxhdpi") fun selfie() {
        fakeSelfie(today)
        shot("selfie") {
            androidx.compose.foundation.layout.Column(
                Modifier.padding(top = 32.dp_), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp_),
            ) {
                val takers = com.mrboora.plate.ui.components.SelfieTakers({})
                com.mrboora.plate.ui.components.SelfieCard(
                    today, has = true, version = 0, revealed = false, locked = true, canCompare = true, takers = takers,
                    onReveal = {}, onHide = {}, onCompare = {}, onDelete = {}, modifier = Modifier.padding(horizontal = 16.dp_),
                )
                com.mrboora.plate.ui.components.SelfieSettings(
                    count = 12, locked = true, canLock = true, reminder = true, reminderHour = 9,
                    onLock = {}, onReminder = { _, _ -> }, onDeleteAll = {}, modifier = Modifier.padding(horizontal = 16.dp_),
                )
            }
        }
    }

    @Test @Config(qualifiers = "w411dp-h1400dp-xxhdpi") fun selfieEmptyAndShown() {
        fakeSelfie(today - 3)
        shot("selfie_empty_shown") {
            androidx.compose.foundation.layout.Column(
                Modifier.padding(top = 32.dp_), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp_),
            ) {
                val takers = com.mrboora.plate.ui.components.SelfieTakers({})
                com.mrboora.plate.ui.components.SelfieCard(
                    today, has = false, version = 0, revealed = false, locked = false, canCompare = false, takers = takers,
                    onReveal = {}, onHide = {}, onCompare = {}, onDelete = {}, modifier = Modifier.padding(horizontal = 16.dp_),
                )
                com.mrboora.plate.ui.components.SelfieCard(
                    today - 3, has = true, version = 0, revealed = true, locked = false, canCompare = true, takers = takers,
                    onReveal = {}, onHide = {}, onCompare = {}, onDelete = {}, modifier = Modifier.padding(horizontal = 16.dp_),
                )
            }
        }
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun profileForecast() = shot("profile_forecast") {
        ProfileScreen(
            profile, targets, current, weights.first().kg, pad, {}, {},
            forecast = {
                com.mrboora.plate.ui.screens.ForecastCard(
                    profile, current, entries, 1250,
                    listOf(com.mrboora.plate.data.Workout(1, today, "Brisk walk", 40, 220)), weights, today,
                    onAddWorkout = { _, _, _ -> }, onDeleteWorkout = {},
                    modifier = Modifier.padding(horizontal = 16.dp_),
                )
            },
        )
    }

    private val imperial = com.mrboora.plate.data.Units.Prefs(
        com.mrboora.plate.data.Units.Weight.LB, com.mrboora.plate.data.Units.Height.FT_IN, com.mrboora.plate.data.Units.Volume.FL_OZ,
    )

    /** The same screen with lb, ft in and fl oz. */
    private fun imperialShot(name: String, content: @Composable () -> Unit) = shot(name) {
        androidx.compose.runtime.CompositionLocalProvider(com.mrboora.plate.ui.components.LocalUnits provides imperial) { content() }
    }

    @Test @Config(qualifiers = "w411dp-h3200dp-xxhdpi") fun profileImperial() = imperialShot("profile_imperial") {
        ProfileScreen(
            profile, targets, current, weights.first().kg, pad, {}, {},
            forecast = {
                com.mrboora.plate.ui.screens.ForecastCard(
                    profile, current, entries, 1250,
                    listOf(com.mrboora.plate.data.Workout(1, today, "Brisk walk", 40, 220)), weights, today,
                    onAddWorkout = { _, _, _ -> }, onDeleteWorkout = {},
                    modifier = Modifier.padding(horizontal = 16.dp_),
                )
            },
        )
    }

    /** A day gone past the target: the calorie card turns light red, like an over day on Progress. */
    @Test fun todayOver() = shot("today_over") {
        TodayScreen(profile, targets, today, entries + entry(Meal.DINNER, "Poha", 600f) + entry(Meal.DINNER, "Banana", 400f), 1250, pad, {}, {}, {}, {}, {})
    }

    @Test fun todayImperial() = imperialShot("today_imperial") {
        TodayScreen(profile, targets, today, entries, 1250, pad, {}, {}, {}, {}, {})
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun formImperial() {
        var saved: com.mrboora.plate.data.Profile? = null
        val start = profile.copy(waterGoalMl = 2500)
        imperialShot("profile_form_imperial") {
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp_)) {
                com.mrboora.plate.ui.screens.ProfileForm(start, "Save changes") { saved = it }
            }
        }
        // Saving without changing anything keeps every number exactly (no lb / fl oz rounding).
        rule.onNodeWithText("Save changes").performClick()
        rule.mainClock.advanceTimeBy(100)
        org.junit.Assert.assertEquals(start, saved)
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun shakeBuilder() = shot("shake_builder") {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = com.mrboora.plate.data.CanadianFoods(ctx)
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS).map { it.second } + com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.CRUST_FOODS + com.mrboora.plate.data.Templates.foodNames
        val foods = names.mapNotNull { n -> (cnf.byName(n) ?: com.mrboora.plate.data.FoodCatalog.foods.firstOrNull { it.name == n })?.let { n to it } }.toMap().toMutableMap()
        foods[com.mrboora.plate.data.Mix.MIXED_BERRIES] = com.mrboora.plate.data.Mix.mixedBerries(com.mrboora.plate.data.Mix.BERRY_PARTS.mapNotNull { foods[it] })!!
        foods[com.mrboora.plate.data.Mix.WATER] = com.mrboora.plate.data.Mix.water
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods)
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun shakeFromText() = shot("shake_from_text") {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = com.mrboora.plate.data.CanadianFoods(ctx)
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS).map { it.second } + com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.CRUST_FOODS + com.mrboora.plate.data.Templates.foodNames
        val foods = names.mapNotNull { n -> (cnf.byName(n) ?: com.mrboora.plate.data.FoodCatalog.foods.firstOrNull { it.name == n })?.let { n to it } }.toMap().toMutableMap()
        foods[com.mrboora.plate.data.Mix.MIXED_BERRIES] = com.mrboora.plate.data.Mix.mixedBerries(com.mrboora.plate.data.Mix.BERRY_PARTS.mapNotNull { foods[it] })!!
        foods[com.mrboora.plate.data.Mix.WATER] = com.mrboora.plate.data.Mix.water
        val draft = com.mrboora.plate.data.MixParser.parse(
            "1 banana with around 30g 4 barries-blend with a regular spoon of peanut butter over with 3.25% milk around 400ml",
            foods, search = { cnf.search(it, 1).firstOrNull() })
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods, draft = draft)
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun pizzaFromText() = shot("pizza_from_text") {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = com.mrboora.plate.data.CanadianFoods(ctx)
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS).map { it.second } + com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.CRUST_FOODS + com.mrboora.plate.data.Templates.foodNames
        val foods = names.mapNotNull { n -> (cnf.byName(n) ?: com.mrboora.plate.data.FoodCatalog.foods.firstOrNull { it.name == n })?.let { n to it } }.toMap()
        val draft = com.mrboora.plate.data.MixParser.parse("party size cheese pizza with paneer topping with onion", foods, search = { cnf.search(it, 1).firstOrNull() })
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods, draft = draft)
    }

    private fun mixFoods(): Pair<Map<String, com.mrboora.plate.data.FoodItem>, com.mrboora.plate.data.CanadianFoods> {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = com.mrboora.plate.data.CanadianFoods(ctx)
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS).map { it.second } +
            com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.CRUST_FOODS + com.mrboora.plate.data.Templates.foodNames
        val foods = names.mapNotNull { n -> (cnf.byName(n) ?: com.mrboora.plate.data.FoodCatalog.foods.firstOrNull { it.name == n })?.let { n to it } }.toMap().toMutableMap()
        foods[com.mrboora.plate.data.Mix.WATER] = com.mrboora.plate.data.Mix.water
        return foods to cnf
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun chaiFromText() = shot("chai_from_text") {
        val (foods, cnf) = mixFoods()
        val draft = com.mrboora.plate.data.MixParser.parse("chai with 2% milk and 2 spoons sugar", foods, search = { cnf.search(it, 1).firstOrNull() })
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods, draft = draft)
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun coffeeFromText() = shot("coffee_from_text") {
        val (foods, cnf) = mixFoods()
        val draft = com.mrboora.plate.data.MixParser.parse("large coffee with milk and 2 spoons sugar", foods, search = { cnf.search(it, 1).firstOrNull() })
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods, draft = draft)
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun rotiFromText() = shot("roti_from_text") {
        val (foods, cnf) = mixFoods()
        val draft = com.mrboora.plate.data.MixParser.parse("2 roti with dal and a spoon of ghee", foods, search = { cnf.search(it, 1).firstOrNull() })
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods, draft = draft)
    }

    @Test fun addFoodWithAmount() = shot("add_food_amount") {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = com.mrboora.plate.data.CanadianFoods(ctx)
        AddFoodScreen(
            Meal.SNACK, "Today", emptyList(),
            { q -> FoodCatalog.search(q) + cnf.search(q, 5) }, { Result.success(emptyList()) }, { Result.success(null) },
            { emptyList() }, { emptyList() }, { emptyList() }, { null }, { _, _, _ -> }, {}, {}, { _, _ -> }, {},
            startQuery = "peanut butter two spoon",
        )
    }

    @Test fun eggSheet() = shot("egg_sheet") {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cnf = com.mrboora.plate.data.CanadianFoods(ctx)
        val egg = cnf.byName("Egg, chicken, whole, fresh or frozen, raw")!!
        com.mrboora.plate.ui.screens.AmountSheet(egg, initial = 182f, onDismiss = {}, foodTypes = { f -> com.mrboora.plate.data.Portions.types(f, cnf::startingWith) }, inline = true) { _, _ -> }
    }

    @Test fun spoonSheet() = shot("spoon_sheet") {
        val pb = com.mrboora.plate.data.FoodItem("Peanut butter, smooth", kcal100 = 588f, protein100 = 25f, carbs100 = 20f, fat100 = 50f, servingG = 32f, servingLabel = "2 tbsp")
        com.mrboora.plate.ui.screens.AmountSheet(pb, initial = 32f, onDismiss = {}, inline = true) { _, _ -> }
    }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi") fun eggsFromText() = shot("eggs_from_text") {
        val (foods, cnf) = mixFoods()
        val draft = com.mrboora.plate.data.MixParser.parse("omelette with 3 eggs and onion", foods, search = { cnf.search(it, 1).firstOrNull() })
        com.mrboora.plate.ui.screens.ShakeBuilder({ foods }, { emptyList() }, {}, { _, _ -> }, preview = foods, draft = draft,
            foodTypes = { f -> com.mrboora.plate.data.Portions.types(f, cnf::startingWith) })
    }

    /** An item typed without its restaurant: the form says what's missing. */
    @Test fun requestForm() = shot("request_form") {
        androidx.compose.foundation.layout.Column(Modifier.padding(24.dp_)) {
            com.mrboora.plate.ui.screens.RequestForm("", "Iced capp", "", {}, {}, {}, {})
        }
    }

    @Test fun onboarding() = shot("onboarding") { OnboardingScreen {} }
}

private val Int.dp_ get() = androidx.compose.ui.unit.Dp(this.toFloat())

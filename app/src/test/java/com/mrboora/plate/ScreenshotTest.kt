package com.mrboora.plate

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
            { emptyList() }, { Result.success(emptyList()) }, { Result.success(null) }, { emptyList() }, { emptyList() }, { emptyList() }, { null }, { _, _ -> }, {}, {}, { _, _ -> }, {},
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

    @Test fun onboarding() = shot("onboarding") { OnboardingScreen {} }
}

private val Int.dp_ get() = androidx.compose.ui.unit.Dp(this.toFloat())

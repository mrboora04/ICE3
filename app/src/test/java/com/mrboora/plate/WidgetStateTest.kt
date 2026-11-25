package com.mrboora.plate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.FoodEntry
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Meal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.PlateDatabase
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Sex
import com.mrboora.plate.data.Water
import com.mrboora.plate.data.WaterContainers
import com.mrboora.plate.widget.loadState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The widgets show the same numbers as the app: water, kcal left, and the user's own buttons. */
@RunWith(RobolectricTestRunner::class)
class WidgetStateTest {
    @Test
    fun widgetReadsTodaysNumbers() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dao = PlateDatabase.get(ctx).dao()
        val p = Profile(name = "V", sex = Sex.MALE, age = 22, heightCm = 180f, weightKg = 110f, goalWeightKg = 85f,
            activity = Activity.LIGHT, goal = Goal.LOSE, paceKgPerWeek = 0.75f, waterGoalMl = 3000)
        dao.saveProfile(p)
        val day = LocalDate.now().toEpochDay()
        dao.saveWater(Water(day, 1250))
        dao.addEntry(FoodEntry(day = day, meal = Meal.LUNCH, name = "Poha", brand = "", grams = 200f, kcal = 260f, protein = 5f, carbs = 46f, fat = 6f))
        WaterContainers.save(ctx, listOf(WaterContainers.Container("Bottle", 500)))
        com.mrboora.plate.data.Units.save(ctx, com.mrboora.plate.data.Units.Prefs())

        val s = loadState(ctx)
        assertEquals(1250, s.waterMl)
        assertEquals(3000, s.waterGoal)
        assertEquals(Nutrition.targets(p).kcal - 260, s.kcalLeft)
        // The widget shows the first of the app's own buttons, from the same list.
        assertEquals(WaterContainers.buttons(ctx).take(2), s.buttons)
        assertEquals(listOf("+ Bottle 500 ml" to 500, "½ Bottle" to 250), s.buttons.map { it.label to it.ml })
    }

    @Test
    fun tappingAWidgetButtonAddsWater() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dao = PlateDatabase.get(ctx).dao()
        val day = LocalDate.now().toEpochDay()
        dao.saveWater(Water(day, 1250))
        val id = object : androidx.glance.GlanceId {}
        // "+ Bottle 500 ml", then "½ Bottle": the same action the buttons run.
        com.mrboora.plate.widget.AddWaterAction().onAction(ctx, id, androidx.glance.action.actionParametersOf(com.mrboora.plate.widget.MlKey to 500))
        assertEquals(1750, dao.waterNow(day)?.ml)
        com.mrboora.plate.widget.AddWaterAction().onAction(ctx, id, androidx.glance.action.actionParametersOf(com.mrboora.plate.widget.MlKey to 250))
        assertEquals(2000, dao.waterNow(day)?.ml)
    }

    @Test
    fun widgetNumbersFollowEveryChangeLive() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dao = PlateDatabase.get(ctx).dao()
        val day = LocalDate.now().toEpochDay()
        dao.saveWater(Water(day, 1000))
        val seen = mutableListOf<Int>()
        // What the widget draws from: it must change on its own, without the app open.
        val job = launch(kotlinx.coroutines.Dispatchers.IO) { com.mrboora.plate.widget.stateFlow(ctx).collect { seen += it.waterMl } }
        kotlinx.coroutines.withTimeout(5000) { while (seen.isEmpty()) kotlinx.coroutines.delay(20) }
        com.mrboora.plate.widget.AddWaterAction().onAction(ctx, object : androidx.glance.GlanceId {}, androidx.glance.action.actionParametersOf(com.mrboora.plate.widget.MlKey to 500))
        kotlinx.coroutines.withTimeout(5000) { while (1500 !in seen) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); kotlinx.coroutines.delay(20) } }
        job.cancel()
        assertEquals(listOf(1000, 1500), seen.distinct())
    }

    @Test
    fun rememberedReadingsSaveAndForget() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        com.mrboora.plate.data.UserAliases.remember(ctx, "Milk", "Milk 3.25%")
        com.mrboora.plate.data.UserAliases.remember(ctx, "scoop\tthing", "Oats")
        assertEquals("Milk 3.25%", com.mrboora.plate.data.UserAliases.load(ctx)["milk"])
        assertEquals(2, com.mrboora.plate.data.UserAliases.load(ctx).size)
        com.mrboora.plate.data.UserAliases.forget(ctx, "milk")
        assertEquals(null, com.mrboora.plate.data.UserAliases.load(ctx)["milk"])
    }
}

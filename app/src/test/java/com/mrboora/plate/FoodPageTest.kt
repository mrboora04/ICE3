package com.mrboora.plate

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mrboora.plate.data.FoodCatalog
import com.mrboora.plate.data.FoodItem
import com.mrboora.plate.data.Meal
import com.mrboora.plate.ui.screens.AddFoodScreen
import com.mrboora.plate.ui.theme.PlateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Tapping a food row grows it into the food page; Back returns to the list; logging is unchanged. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FoodPageTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val banana = FoodCatalog.foods.first { it.name == "Banana" }
    private var added: Pair<FoodItem, Float>? = null

    private fun open() {
        rule.setContent {
            PlateTheme {
                AddFoodScreen(
                    Meal.SNACK, "Today", listOf(banana),
                    { emptyList() }, { Result.success(emptyList()) }, { Result.success(null) }, { emptyList() }, { emptyList() }, { emptyList() },
                    { null }, { _, _, _ -> }, {}, {}, { f, g -> added = f to g }, {},
                )
            }
        }
    }

    private fun frame(name: String) {
        val bmp = capture(rule.activity)
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun rowGrowsIntoThePageAndLoggingIsUnchanged() {
        open()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Banana").performClick()
        // Halfway through the 300 ms: the row is on its way to becoming the page.
        rule.mainClock.advanceTimeBy(150)
        frame("food_page_mid")
        rule.mainClock.advanceTimeBy(400)
        frame("food_page_open")
        rule.mainClock.autoAdvance = true
        // The page has the amount and the same Add button; adding logs exactly as before.
        rule.onNodeWithText("Add").performClick()
        rule.waitForIdle()
        assertEquals(banana.name, added?.first?.name)
        assertTrue((added?.second ?: 0f) > 0f)
    }

    @Test fun backReturnsToTheList() {
        open()
        rule.onNodeWithText("Banana").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Add").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        // The list is back (the search box), and nothing was logged.
        assertTrue(rule.onAllNodesWithText("Recent and your foods", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(null, added)
    }
}

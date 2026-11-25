package com.mrboora.plate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.mrboora.plate.data.Activity
import com.mrboora.plate.data.FoodEntry
import com.mrboora.plate.data.Goal
import com.mrboora.plate.data.Meal
import com.mrboora.plate.data.PlateDatabase
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.Sex
import com.mrboora.plate.data.Water
import com.mrboora.plate.data.WaterContainers
import com.mrboora.plate.widget.TodayWidget
import com.mrboora.plate.widget.WaterWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Draws the widgets the way a launcher does (Glance → RemoteViews → views) at their smallest,
 * default and largest sizes, light and dark, into docs/screenshots/widget_*.png, and checks
 * that the water buttons are tappable on their own and the widget as a whole is not.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetRenderTest {

    private fun seed(ctx: Context) = runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PlateDatabase.get(ctx).clearAllTables() }
        val dao = PlateDatabase.get(ctx).dao()
        dao.saveProfile(Profile(name = "V", sex = Sex.MALE, age = 22, heightCm = 180f, weightKg = 110f, goalWeightKg = 85f,
            activity = Activity.LIGHT, goal = Goal.LOSE, paceKgPerWeek = 0.75f, waterGoalMl = 3000))
        val day = LocalDate.now().toEpochDay()
        dao.saveWater(Water(day, 1250))
        dao.addEntry(FoodEntry(day = day, meal = Meal.LUNCH, name = "Poha", brand = "", grams = 200f, kcal = 426f, protein = 8f, carbs = 70f, fat = 9f))
        WaterContainers.save(ctx, listOf(WaterContainers.Container("Bottle", 500)))
        com.mrboora.plate.data.Units.save(ctx, units)
    }

    private var units = com.mrboora.plate.data.Units.Prefs()

    private fun draw(widget: GlanceAppWidget, w: Int, h: Int, name: String, dark: Boolean, preview: String? = null) = runBlocking {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        seed(ctx)
        val rv = widget.compose(ctx, size = DpSize(w.dp, h.dp))
        val parent = FrameLayout(ctx)
        val view = rv.apply(ctx, parent)
        val d = ctx.resources.displayMetrics.density
        val pw = (w * d).toInt(); val ph = (h * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, pw, ph)
        val bmp = Bitmap.createBitmap(pw + 80, ph + 80, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (dark) 0xFF1F2733.toInt() else 0xFF8FA2B8.toInt())   // a wallpaper-ish backdrop
        c.translate(40f, 40f)
        view.draw(c)
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The widget picker's preview is this same drawing (no backdrop), so it always matches the widget.
        preview?.let { file ->
            val clean = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(clean))
            File("src/main/res/drawable-nodpi/$file.png").outputStream().use { clean.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        // Taps: the water buttons have their own click, and the whole widget does not
        // (so a tap on a button can never open the app instead).
        assertFalse("$name: the widget root must not be clickable", view.hasOnClickListeners())
        val clickable = mutableListOf<View>()
        fun walk(v: View) { if (v.hasOnClickListeners()) clickable += v; if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(view)
        assertTrue("$name: expected the two water buttons (and the numbers) to be clickable, found ${clickable.size}", clickable.size >= 3)
        // All 12 drops (3,000 ml goal) are there: widgets silently drop children past 10 in a row.
        val images = mutableListOf<View>()
        fun imgs(v: View) { if (v is android.widget.ImageView) images += v; if (v is ViewGroup) for (i in 0 until v.childCount) imgs(v.getChildAt(i)) }
        imgs(view)
        val drops = images.count { (it.width in 1..((26 * d).toInt())) }
        assertTrue("$name: expected 12 drops, found $drops", drops >= 12)
    }

    // Sizes: smallest allowed, default (from the widget info), largest allowed.
    @Test fun water() {
        // Must match water_widget_info.xml: min 290 × 170, max 420 × 200.
        draw(WaterWidget(), 290, 170, "widget_water_min", false)
        draw(WaterWidget(), 340, 180, "widget_water_default", false, preview = "widget_water_preview")
        draw(WaterWidget(), 420, 200, "widget_water_max", false)
    }
    /** lb, ft in, fl oz: the drinks read "/ 101.4 fl oz" and "+ Bottle 16.9 fl oz". */
    @Test fun waterImperial() {
        units = com.mrboora.plate.data.Units.Prefs(com.mrboora.plate.data.Units.Weight.LB, com.mrboora.plate.data.Units.Height.FT_IN, com.mrboora.plate.data.Units.Volume.FL_OZ)
        draw(WaterWidget(), 340, 180, "widget_water_imperial", false)
    }
    @Test fun waterDark() = draw(WaterWidget(), 340, 180, "widget_water_default_dark", true)
    @Test fun today() {
        // Must match today_widget_info.xml: min 290 × 300, max 420 × 400.
        draw(TodayWidget(), 290, 300, "widget_today_min", false)
        draw(TodayWidget(), 340, 340, "widget_today_default", false, preview = "widget_today_preview")
        draw(TodayWidget(), 420, 400, "widget_today_max", false)
    }
    @Test fun todayDark() = draw(TodayWidget(), 340, 340, "widget_today_default_dark", true)
}

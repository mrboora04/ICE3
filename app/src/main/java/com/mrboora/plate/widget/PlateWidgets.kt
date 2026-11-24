package com.mrboora.plate.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.OutlineButton
import androidx.glance.ButtonDefaults
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mrboora.plate.MainActivity
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.PlateDatabase
import com.mrboora.plate.data.Water
import com.mrboora.plate.data.WaterContainers
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Home-screen widgets. Both read today's numbers straight from Plate's database, follow the
 * phone's light/dark mode, and show nothing private (no weight, no selfie): a home screen is
 * seen by others. Water buttons are the user's own containers from the water card.
 */
object PlateWidgets {
    /** Redraw both widgets, e.g. after water or food is logged in the app. */
    suspend fun updateAll(context: Context) {
        runCatching { WaterWidget().updateAll(context) }
        runCatching { TodayWidget().updateAll(context) }
    }
}

/** Today's numbers for the widgets. */
internal data class WidgetState(
    val waterMl: Int,
    val waterGoal: Int,
    val kcalLeft: Int?,
    val kcalTarget: Int?,
    /** The first two of the app's own water buttons (the same list as the water card). */
    val buttons: List<WaterContainers.Button>,
)

internal suspend fun loadState(context: Context): WidgetState {
    val dao = PlateDatabase.get(context).dao()
    val day = LocalDate.now().toEpochDay()
    val water = dao.water(day).first()?.ml ?: 0
    val profile = dao.profile().first()
    val latest = dao.weights().first().lastOrNull()?.kg
    val targets = profile?.let { Nutrition.targets(if (latest != null) it.copy(weightKg = latest) else it) }
    val eaten = dao.entries(day).first().sumOf { it.kcal.toDouble() }.roundToInt()
    return WidgetState(
        waterMl = water, waterGoal = profile?.waterGoalMl ?: 2000,
        kcalLeft = targets?.let { it.kcal - eaten }, kcalTarget = targets?.kcal,
        buttons = WaterContainers.buttons(context).take(2),
    )
}

// ---- Colours: Plate's palette, light and dark ----------------------------------------------
private val Card = ColorProvider(day = Color(0xFFFFF9F6), night = Color(0xFF261B21))
private val Ink = ColorProvider(day = Color(0xFF3A2230), night = Color(0xFFF6E7ED))
private val Muted = ColorProvider(day = Color(0xFF6E4A5C), night = Color(0xFFC6A9B7))
private val Track = ColorProvider(day = Color(0xFFEED6DD), night = Color(0xFF3D2A34))
private val WaterBlue = ColorProvider(day = Color(0xFF5C7CE0), night = Color(0xFF8FA8FF))
private val Accent = ColorProvider(day = Color(0xFFD2477A), night = Color(0xFFFF7AA8))

private fun n(v: Int) = "%,d".format(v)

internal val MlKey = ActionParameters.Key<Int>("ml")

/** One tap on a water button: add that container's amount to today. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ml = parameters[MlKey] ?: return
        val dao = PlateDatabase.get(context).dao()
        val day = LocalDate.now().toEpochDay()
        val now = dao.waterNow(day)?.ml ?: 0
        dao.saveWater(Water(day, (now + ml).coerceIn(0, 10_000)))
        PlateWidgets.updateAll(context)
    }
}

@Composable
private fun WaterBlock(s: WidgetState, big: Boolean) {
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        Text(n(s.waterMl), style = TextStyle(color = Ink, fontSize = if (big) 30.sp else 20.sp, fontWeight = FontWeight.Bold))
        Text(" / ${n(s.waterGoal)} ml", style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
    Spacer(GlanceModifier.height(6.dp))
    LinearProgressIndicator(
        progress = if (s.waterGoal > 0) (s.waterMl.toFloat() / s.waterGoal).coerceIn(0f, 1f) else 0f,
        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
        color = WaterBlue, backgroundColor = Track,
    )
}

/**
 * The water buttons, from the app's one list. Glance's own button components handle their
 * taps themselves, so a tap always adds water instead of opening the app.
 */
@Composable
private fun WaterButtons(s: WidgetState) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        s.buttons.forEachIndexed { i, b ->
            if (i > 0) Spacer(GlanceModifier.width(8.dp))
            val add = actionRunCallback<AddWaterAction>(actionParametersOf(MlKey to b.ml))
            if (b.whole) {
                FilledButton(
                    text = b.label, onClick = add, maxLines = 1,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Track, contentColor = Ink),
                    modifier = GlanceModifier.defaultWeight(),
                )
            } else {
                // The half button only takes the room it needs, so "+ Bottle 500 ml" is never cut off.
                OutlineButton(text = b.label, contentColor = Accent, onClick = add, maxLines = 1)
            }
        }
    }
}

/** A · Plate · Water: the app's water card, same layout. */
class WaterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadState(context)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(Card).padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // Only the numbers open the app; the buttons add water.
                Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())) {
                    Text("WATER", style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                    WaterBlock(s, big = true)
                }
                Spacer(GlanceModifier.height(12.dp))
                WaterButtons(s)
            }
        }
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}

/** The kcal ring as a picture: widgets can't draw shapes, so it's drawn once per update. */
private fun ring(progress: Float, over: Boolean): Bitmap {
    val size = 240
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val stroke = 24f
    val r = RectF(stroke / 2 + 4, stroke / 2 + 4, size - stroke / 2 - 4, size - stroke / 2 - 4)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    p.color = 0x40E0578A
    c.drawArc(r, 0f, 360f, false, p)
    p.color = if (over) 0xFFE5735E.toInt() else 0xFFE0578A.toInt()
    if (progress > 0f) c.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
    return bmp
}

/** B · Plate · Today (2 × 2). */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadState(context)
        val left = s.kcalLeft
        val target = s.kcalTarget
        val progress = if (left != null && target != null && target > 0) (target - left).toFloat() / target else 0f
        val bmp = ring(progress, (left ?: 0) < 0)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(Card).padding(16.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // The ring and numbers open the app; the buttons add water.
                Box(GlanceModifier.size(104.dp).clickable(actionStartActivity<MainActivity>()), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.size(104.dp))
                    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        Text(
                            if (left == null) "–" else n(kotlin.math.abs(left)),
                            style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            if ((left ?: 0) < 0) "kcal over" else "kcal left",
                            style = TextStyle(color = Muted, fontSize = 11.sp),
                        )
                    }
                }
                Spacer(GlanceModifier.height(12.dp))
                Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())) {
                    Text("WATER", style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                    WaterBlock(s, big = false)
                }
                Spacer(GlanceModifier.height(10.dp))
                WaterButtons(s)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

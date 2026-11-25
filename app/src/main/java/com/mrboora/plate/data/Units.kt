package com.mrboora.plate.data

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How the user likes to see measures: body weight in kg or lb, height in cm or feet and inches,
 * drinks in ml or US fl oz. Each is chosen on its own (many Canadians weigh in pounds but drink
 * in millilitres). Plate always stores kg, cm and ml; these only change what is shown and typed.
 */
object Units {
    enum class Weight(val label: String) { KG("kg"), LB("lb") }
    enum class Height(val label: String) { CM("cm"), FT_IN("ft in") }
    enum class Volume(val label: String) { ML("ml"), FL_OZ("fl oz") }

    data class Prefs(val weight: Weight = Weight.KG, val height: Height = Height.CM, val volume: Volume = Volume.ML)

    // Exact definitions.
    const val KG_PER_LB = 0.45359237f
    const val CM_PER_IN = 2.54f
    const val ML_PER_FL_OZ = 29.5735295625f

    /**
     * What the phone's region uses, from Android's own locale data (ICU): the US system gives
     * lb, ft in and fl oz; everywhere else metric. A region setting on the phone that asks for
     * a measurement system is followed too, since ICU reads it from the locale.
     */
    fun phoneDefault(locale: Locale = Locale.getDefault()): Prefs {
        val us = runCatching {
            android.icu.util.LocaleData.getMeasurementSystem(android.icu.util.ULocale.forLocale(locale)) ==
                android.icu.util.LocaleData.MeasurementSystem.US
        }.getOrElse { locale.country in US_SYSTEM }
        return if (us) Prefs(Weight.LB, Height.FT_IN, Volume.FL_OZ) else Prefs()
    }

    /** Where ICU isn't available: the countries on the US system (CLDR). */
    private val US_SYSTEM = setOf("US", "LR", "MM")

    private const val FILE = "plate_units"

    /** The user's choice, or the phone's default until they pick. */
    fun load(context: Context): Prefs {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = phoneDefault()
        return Prefs(
            sp.getString("weight", null)?.let { runCatching { Weight.valueOf(it) }.getOrNull() } ?: d.weight,
            sp.getString("height", null)?.let { runCatching { Height.valueOf(it) }.getOrNull() } ?: d.height,
            sp.getString("volume", null)?.let { runCatching { Volume.valueOf(it) }.getOrNull() } ?: d.volume,
        )
    }

    fun save(context: Context, p: Prefs) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("weight", p.weight.name).putString("height", p.height.name).putString("volume", p.volume.name).apply()
    }
}

// ---- Body weight ----------------------------------------------------------------------------

/** kg → the number shown, rounded to 0.1 so going back and forth doesn't drift. */
fun Units.Prefs.weightValue(kg: Float): Float =
    ((if (weight == Units.Weight.LB) kg / Units.KG_PER_LB else kg) * 10f).roundToInt() / 10f

/** A typed weight in the user's unit → kg. */
fun Units.Prefs.kgFrom(value: Float): Float = if (weight == Units.Weight.LB) value * Units.KG_PER_LB else value

/** kg per week (or any weight change) in the user's unit, unrounded. */
fun Units.Prefs.weightRaw(kg: Float): Float = if (weight == Units.Weight.LB) kg / Units.KG_PER_LB else kg

/** "110.2 kg" or "243 lb"; whole numbers lose the ".0". */
fun Units.Prefs.weight(kg: Float): String = "${num1(weightValue(kg))} ${weight.label}"

/** Weekly pace choices, in the user's unit: ¼, ½, ¾, 1 kg, or ½, 1, 1½, 2 lb (about the same). */
fun Units.Prefs.paceSteps(): List<Float> =
    if (weight == Units.Weight.LB) listOf(0.5f, 1f, 1.5f, 2f) else listOf(0.25f, 0.5f, 0.75f, 1f)

/** kcal in one unit of body weight: 7,700 per kg, which is about 3,500 per lb. */
fun Units.Prefs.kcalPerWeightUnit(): Float =
    if (weight == Units.Weight.LB) ((Forecast.KCAL_PER_KG * Units.KG_PER_LB) / 100f).roundToInt() * 100f else Forecast.KCAL_PER_KG

// ---- Height ---------------------------------------------------------------------------------

/** "180 cm" or "5′ 11″". */
fun Units.Prefs.height(cm: Float): String = when (height) {
    Units.Height.CM -> "${cm.roundToInt()} cm"
    Units.Height.FT_IN -> feetInches(cm).let { (f, i) -> "$f′ $i″" }
}

/** Whole feet and inches, the inches rounded (and carried into feet at 12). */
fun feetInches(cm: Float): Pair<Int, Int> {
    val totalIn = (cm / Units.CM_PER_IN).roundToInt()
    return totalIn / 12 to totalIn % 12
}

fun cmFrom(feet: Float, inches: Float): Float = (feet * 12f + inches) * Units.CM_PER_IN

// ---- Drinks ---------------------------------------------------------------------------------

/** ml → the number shown: whole ml, or fl oz to 0.1. */
fun Units.Prefs.volumeValue(ml: Int): Float =
    if (volume == Units.Volume.FL_OZ) ((ml / Units.ML_PER_FL_OZ) * 10f).roundToInt() / 10f else ml.toFloat()

/** A typed amount in the user's unit → whole ml. */
fun Units.Prefs.mlFrom(value: Float): Int = (if (volume == Units.Volume.FL_OZ) value * Units.ML_PER_FL_OZ else value).roundToInt()

/** "1,250 ml" or "42.3 fl oz". */
fun Units.Prefs.volume(ml: Int): String = "${volumeNumber(ml)} ${volume.label}"

/** Just the number: "1,250" or "42.3". */
fun Units.Prefs.volumeNumber(ml: Int): String =
    if (volume == Units.Volume.FL_OZ) num1(volumeValue(ml)) else "%,d".format(ml)

/** One decimal, dropped when it's .0, with thousands separators: 1,250 · 42.3 · 243. */
fun num1(v: Float): String {
    val r = (v * 10f).roundToInt() / 10f
    return if (r % 1f == 0f) "%,d".format(r.roundToInt()) else "%,.1f".format(r)
}

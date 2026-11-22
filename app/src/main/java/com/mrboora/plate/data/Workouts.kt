package com.mrboora.plate.data

import kotlin.math.roundToInt

/**
 * Workout calories from type and minutes, using MET values from the Compendium of Physical
 * Activities. Plate counts only the extra burn, (MET − 1) × kg × hours, because resting energy
 * is already in the day's burn.
 */
object Workouts {
    data class Kind(val name: String, val met: Float)

    val kinds = listOf(
        Kind("Walk", 3.5f),
        Kind("Brisk walk", 4.3f),
        Kind("Run", 9.8f),
        Kind("Cycling", 7.5f),
        Kind("Gym / weights", 5.0f),
        Kind("HIIT", 8.0f),
        Kind("Swimming", 7.0f),
        Kind("Sports", 7.0f),
        Kind("Dance", 5.0f),
        Kind("Yoga", 2.5f),
    )

    fun kcal(kind: Kind, minutes: Int, kg: Float): Int = ((kind.met - 1f) * kg * minutes / 60f).roundToInt()
}

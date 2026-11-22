package com.mrboora.plate.data

import android.content.Context

/**
 * What each person drinks water from: their bottle, glass or cup, with its size. One tap on the
 * Today card adds that container's amount, so a 500 ml bottle is one tap, not two.
 */
object WaterContainers {
    data class Container(val name: String, val ml: Int)

    /** Starting set: a glass and the common 500 ml bottle. Anyone can change these. */
    val defaults = listOf(Container("Glass", 250), Container("Bottle", 500))

    /** Sizes to pick from when adding one. */
    val presets = listOf(
        Container("Cup", 200), Container("Glass", 250), Container("Can", 355), Container("Bottle", 500),
        Container("Bottle", 750), Container("Bottle", 1000), Container("Big bottle", 1500),
    )

    const val MAX = 4
    private const val PREFS = "plate_water"
    private const val KEY = "containers"

    fun load(c: Context): List<Container> {
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return defaults
        return decode(raw).ifEmpty { defaults }
    }

    fun save(c: Context, list: List<Container>) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(list.take(MAX))).apply()
    }

    /** "Bottle:500|Glass:250". Names lose any | or : so the format can't break. */
    fun encode(list: List<Container>): String =
        list.joinToString("|") { "${it.name.replace(Regex("[|:]"), " ").trim()}:${it.ml}" }

    fun decode(raw: String): List<Container> = raw.split('|').mapNotNull { part ->
        val name = part.substringBeforeLast(':').trim()
        val ml = part.substringAfterLast(':').toIntOrNull()
        if (name.isBlank() || ml == null || ml !in 20..3000) null else Container(name, ml)
    }

    /** "½ Bottle" helpers for containers big enough that people often drink part of one. */
    fun halves(list: List<Container>): List<Container> = list.filter { it.ml >= 500 }.map { Container("½ ${it.name}", it.ml / 2) }
}

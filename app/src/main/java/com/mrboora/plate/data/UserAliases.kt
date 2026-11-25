package com.mrboora.plate.data

import android.content.Context

/**
 * The user's own readings: a word they type → the ingredient they mean, e.g. "milk" → "Milk 3.25%".
 * Set by tapping a line under "How I read it" and keeping "Remember for next time". The builder's
 * reader checks these before its built-in words. Stored only on this phone.
 */
object UserAliases {
    private const val PREFS = "plate_aliases"
    private const val KEY = "map"

    fun load(c: Context): Map<String, String> =
        decode(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")

    fun remember(c: Context, word: String, label: String) = save(c, load(c) + (word.trim().lowercase() to label))
    fun forget(c: Context, word: String) = save(c, load(c) - word.trim().lowercase())

    private fun save(c: Context, map: Map<String, String>) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(map)).apply()

    /** One "word<TAB>label" per line; tabs and newlines can't appear in either. */
    fun encode(map: Map<String, String>): String = map.entries.joinToString("\n") { (w, l) ->
        "${w.replace(Regex("[\t\n]"), " ")}\t${l.replace(Regex("[\t\n]"), " ")}"
    }

    fun decode(raw: String): Map<String, String> = raw.lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
    }.toMap()
}

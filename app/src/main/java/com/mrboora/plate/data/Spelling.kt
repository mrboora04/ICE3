package com.mrboora.plate.data

import kotlin.math.abs
import kotlin.math.min

/** Offline "Did you mean…?" for food and restaurant searches. */
object Spelling {
    private val split = Regex("[^a-z0-9]+")

    /** Lowercase words of 3+ letters, apostrophes dropped ("Osmow's" -> "osmows"). */
    fun words(text: String): List<String> =
        text.lowercase().replace("'", "").replace("’", "").split(split).filter { it.length >= 3 }

    /**
     * The same word, typed a little wrong: a start of it ("gaj" for "gajar"), the same letters in
     * another order ("garaj"), or one letter off ("gajer").
     */
    fun close(typed: String, word: String): Boolean {
        if (word.startsWith(typed)) return true
        if (typed.length >= 4 && typed.length == word.length && typed.toList().sorted() == word.toList().sorted()) return true
        return typed.length >= 4 && distance(typed, word) <= 1
    }

    /** Optimal string alignment distance: insertions, deletions, substitutions and swaps. */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /**
     * Replaces each unknown word with the closest known one, if it's close enough
     * (1 edit for short words, about a third of the letters for longer ones).
     * Returns null when nothing needed correcting.
     */
    fun suggest(query: String, vocab: Set<String>): String? {
        val tokens = query.lowercase().replace("'", "").replace("’", "").split(split).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        var changed = false
        val fixed = tokens.map { t ->
            if (t.length < 3 || t in vocab || vocab.any { it.startsWith(t) }) return@map t
            val limit = maxOf(1, t.length / 3)
            val best = vocab.asSequence()
                .filter { abs(it.length - t.length) <= limit }
                .map { it to distance(t, it) }
                .filter { it.second <= limit }
                .minWithOrNull(compareBy({ it.second }, { abs(it.first.length - t.length) }))
            if (best != null) { changed = true; best.first } else t
        }
        return if (changed) fixed.joinToString(" ") else null
    }
}

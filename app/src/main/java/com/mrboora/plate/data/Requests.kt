package com.mrboora.plate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A food or restaurant someone asked to have added. Kept on the phone until it shows up in the data. */
data class FoodRequest(
    val id: Long,
    val restaurant: String,
    val item: String,
    val createdAt: Long,
    val done: Boolean,
    /** Anything specific they wrote ("the large one, with oat milk"); goes in the email. */
    val details: String = "",
) {
    val label: String get() = listOf(restaurant, item).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { details.take(40) }

    /** Words that must all appear in a restaurant row for the request to count as added. */
    val terms: List<String> get() = Spelling.words("$restaurant $item")

    /**
     * Whether the phone can tell by itself when it's been added: it names a restaurant (and maybe
     * an item). A request with only details is read by a person and can't be matched automatically.
     */
    val trackable: Boolean get() = terms.isNotEmpty() || homemade

    /** Something Plate's builders make (a shake, a sabji): it's ready as soon as the app has the builder. */
    val homemade: Boolean get() = restaurant.isBlank() &&
        (Mix.isShake("$item $details") || Templates.forText("$item $details")?.homemade == true)
}

object Requests {
    /** Where requests are emailed. */
    const val EMAIL = "booraparth@muse.ai"

    private const val KEY = "requests"
    private fun prefs(c: Context) = c.getSharedPreferences("plate_requests", Context.MODE_PRIVATE)

    fun all(c: Context): List<FoodRequest> {
        val arr = runCatching { JSONArray(prefs(c).getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                FoodRequest(it.optLong("id"), it.optString("restaurant"), it.optString("item"), it.optLong("at"), it.optBoolean("done"), it.optString("details"))
            }
        }
    }

    private fun save(c: Context, list: List<FoodRequest>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("restaurant", it.restaurant).put("item", it.item).put("at", it.createdAt).put("done", it.done).put("details", it.details))
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    /**
     * What's missing before a request can be sent, or null when it's fine. Anything specific is
     * enough on its own; a menu item needs its restaurant; a restaurant alone asks for its menu.
     */
    fun problem(restaurant: String, item: String, details: String): String? = when {
        details.isNotBlank() -> null
        item.isNotBlank() && restaurant.isBlank() -> "Which restaurant is it from? Or describe it below."
        restaurant.isBlank() -> "Name a restaurant, or write what you'd like below."
        else -> null
    }

    /** Requests still waiting that the phone can check for by itself. */
    fun pending(c: Context): List<FoodRequest> = all(c).filter { !it.done && it.trackable }

    fun add(c: Context, restaurant: String, item: String, details: String = ""): FoodRequest {
        val r = FoodRequest(System.currentTimeMillis(), restaurant.trim(), item.trim(), System.currentTimeMillis(), false, details.trim())
        save(c, all(c) + r)
        return r
    }

    fun markDone(c: Context, ids: Set<Long>) {
        if (ids.isEmpty()) return
        save(c, all(c).map { if (it.id in ids) it.copy(done = true) else it })
    }
}

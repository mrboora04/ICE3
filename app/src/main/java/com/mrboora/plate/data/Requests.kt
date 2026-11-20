package com.mrboora.plate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A food or restaurant someone asked to have added. Kept on the phone until it shows up in the data. */
data class FoodRequest(val id: Long, val restaurant: String, val item: String, val createdAt: Long, val done: Boolean) {
    val label: String get() = listOf(restaurant, item).filter { it.isNotBlank() }.joinToString(" – ")

    /** Words that must all appear in a restaurant row for the request to count as added. */
    val terms: List<String> get() = Spelling.words("$restaurant $item")
}

object Requests {
    /** Where requests are emailed. */
    const val EMAIL = "justhinkingroup@gmail.com"

    private const val KEY = "requests"
    private fun prefs(c: Context) = c.getSharedPreferences("plate_requests", Context.MODE_PRIVATE)

    fun all(c: Context): List<FoodRequest> {
        val arr = runCatching { JSONArray(prefs(c).getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                FoodRequest(it.optLong("id"), it.optString("restaurant"), it.optString("item"), it.optLong("at"), it.optBoolean("done"))
            }
        }
    }

    private fun save(c: Context, list: List<FoodRequest>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("restaurant", it.restaurant).put("item", it.item).put("at", it.createdAt).put("done", it.done))
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(c: Context, restaurant: String, item: String): FoodRequest {
        val r = FoodRequest(System.currentTimeMillis(), restaurant.trim(), item.trim(), System.currentTimeMillis(), false)
        save(c, all(c) + r)
        return r
    }

    fun markDone(c: Context, ids: Set<Long>) {
        if (ids.isEmpty()) return
        save(c, all(c).map { if (it.id in ids) it.copy(done = true) else it })
    }
}

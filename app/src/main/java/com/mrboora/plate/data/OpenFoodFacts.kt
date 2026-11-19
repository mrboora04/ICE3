package com.mrboora.plate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Food search using the open Open Food Facts database (data under the ODbL). */
object OpenFoodFacts {
    private const val WORLD = "https://world.openfoodfacts.org"
    private const val SEARCH = "https://search.openfoodfacts.org"
    private const val FIELDS = "code,product_name,brands,nutriments,serving_quantity,serving_size"

    /**
     * Uses the newer search service first; the classic search.pl endpoint is often
     * overloaded (HTTP 503), so it is only a fallback.
     */
    suspend fun search(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val primary = runCatching {
            val json = get("$SEARCH/search?q=$q&page_size=30&fields=$FIELDS")
            parseList(json.optJSONArray("hits"))
        }
        primary.getOrNull()?.takeIf { it.isNotEmpty() } ?: run {
            val fallback = runCatching {
                val json = get("$WORLD/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=25&fields=$FIELDS")
                parseList(json.optJSONArray("products"))
            }
            // Report an error only if both services failed.
            fallback.getOrNull() ?: primary.getOrNull() ?: throw (fallback.exceptionOrNull() ?: IOException("search failed"))
        }
    }

    suspend fun barcode(code: String): FoodItem? = withContext(Dispatchers.IO) {
        val clean = code.filter { it.isDigit() }
        if (clean.isEmpty()) return@withContext null
        val json = try {
            get("$WORLD/api/v2/product/$clean.json?fields=$FIELDS")
        } catch (e: HttpException) {
            if (e.code == 404) return@withContext null else throw e
        }
        if (json.optInt("status") != 1) null else json.optJSONObject("product")?.let(::parse)
    }

    private fun parseList(arr: JSONArray?): List<FoodItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parse) }.distinctBy { it.key }
    }

    internal fun parse(p: JSONObject): FoodItem? {
        val name = p.optString("product_name").trim()
        if (name.isEmpty()) return null
        val n = p.optJSONObject("nutriments") ?: return null
        val kcal = when {
            n.has("energy-kcal_100g") -> n.optDouble("energy-kcal_100g")
            n.has("energy_100g") -> n.optDouble("energy_100g") / 4.184
            else -> return null
        }
        if (kcal.isNaN()) return null
        val serving = p.optDouble("serving_quantity", 100.0).let { if (it.isNaN() || it <= 0) 100.0 else it }
        // The new search service returns brands as an array, the classic API as a comma-separated string.
        val brand = when (val b = p.opt("brands")) {
            is JSONArray -> b.optString(0)
            is String -> b.split(",").first()
            else -> ""
        }.trim()
        return FoodItem(
            name = name,
            brand = brand,
            kcal100 = kcal.toFloat(),
            protein100 = n.optDouble("proteins_100g", 0.0).orZero(),
            carbs100 = n.optDouble("carbohydrates_100g", 0.0).orZero(),
            fat100 = n.optDouble("fat_100g", 0.0).orZero(),
            servingG = serving.toFloat(),
            servingLabel = p.optString("serving_size"),
            source = FoodItem.Source.ONLINE,
        )
    }

    private fun Double.orZero() = if (isNaN()) 0f else toFloat()

    class HttpException(val code: Int) : IOException("HTTP $code")

    /** GET with two retries on server errors and timeouts. */
    private suspend fun get(url: String): JSONObject {
        var last: IOException? = null
        repeat(3) { attempt ->
            try {
                return getOnce(url)
            } catch (e: HttpException) {
                if (e.code < 500 && e.code != 429) throw e
                last = e
            } catch (e: IOException) {
                last = e
            }
            delay(600L * (attempt + 1))
        }
        throw last ?: IOException("request failed")
    }

    private fun getOnce(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "PlateAndroid/1.1")
        conn.setRequestProperty("Accept", "application/json")
        return try {
            if (conn.responseCode !in 200..299) throw HttpException(conn.responseCode)
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}

package com.mrboora.plate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Food search using the open Open Food Facts database (data under the ODbL). */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val FIELDS = "product_name,brands,nutriments,serving_quantity,serving_size"

    suspend fun search(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=25&fields=$FIELDS")
        val products = json.optJSONArray("products") ?: return@withContext emptyList()
        (0 until products.length()).mapNotNull { parse(products.getJSONObject(it)) }
            .distinctBy { it.key }
    }

    suspend fun barcode(code: String): FoodItem? = withContext(Dispatchers.IO) {
        val clean = code.filter { it.isDigit() }
        if (clean.isEmpty()) return@withContext null
        val json = get("$BASE/api/v2/product/$clean.json?fields=$FIELDS")
        if (json.optInt("status") != 1) null else json.optJSONObject("product")?.let(::parse)
    }

    private fun parse(p: JSONObject): FoodItem? {
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
        return FoodItem(
            name = name,
            brand = p.optString("brands").split(",").firstOrNull()?.trim().orEmpty(),
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

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "PlateAndroid/1.0")
        return try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}

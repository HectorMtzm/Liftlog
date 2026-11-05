package com.hectormtzm.liftlog.data.nutrition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal Open Food Facts client: barcode lookup and product search (packaged foods). */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val FIELDS = "code,product_name,brands,nutriments,serving_size,serving_quantity"
    private const val USER_AGENT = "Liftlog/0.2 (Android; personal fitness app)"

    suspend fun byBarcode(code: String): FoodEntity? = withContext(Dispatchers.IO) {
        val json = get("$BASE/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json?fields=$FIELDS") ?: return@withContext null
        val o = JSONObject(json)
        if (o.optInt("status", 0) != 1) return@withContext null
        parseProduct(o.optJSONObject("product") ?: return@withContext null, code)
    }

    suspend fun search(query: String): List<FoodEntity> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=25&fields=$FIELDS")
            ?: throw java.io.IOException("Search failed")
        val arr = JSONObject(json).optJSONArray("products") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { parseProduct(arr.getJSONObject(it), null) }
    }

    internal fun parseProduct(p: JSONObject, fallbackCode: String?): FoodEntity? {
        val code = p.optString("code").ifBlank { fallbackCode ?: return null }
        val name = p.optString("product_name").trim().ifEmpty { return null }
        val n = p.optJSONObject("nutriments") ?: return null
        fun nut(key: String): Double? = if (n.has(key)) n.optDouble(key).takeIf { it.isFinite() } else null
        val kcal = nut("energy-kcal_100g") ?: nut("energy_100g")?.let { it / 4.184 } ?: return null
        val portions = buildList {
            val sq = p.optDouble("serving_quantity", Double.NaN)
            if (sq.isFinite() && sq > 0) add(Portion(p.optString("serving_size").ifBlank { "1 serving" }.take(40), sq))
        }
        return FoodEntity(
            id = "off_$code",
            name = name,
            brand = p.optString("brands").split(',').firstOrNull()?.trim().orEmpty(),
            source = Source.OFF,
            kcal100 = kcal,
            protein100 = nut("proteins_100g") ?: 0.0,
            carbs100 = nut("carbohydrates_100g") ?: 0.0,
            fat100 = nut("fat_100g") ?: 0.0,
            portions = FoodEntity.encodePortions(portions),
            barcode = code,
        )
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode == 404) null
            else if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

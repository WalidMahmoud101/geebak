package com.nabd.money.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * أسعار الدهب بالجنيه: سعر الأوقية العالمي بالدولار (gold-api.com، مجاني من غير حساب)
 * × سعر الدولار بالجنيه. ده سعر "الخام" اللي بيتبني عليه سعر السوق؛ محلات الصاغة بتزود مصنعية ودمغة،
 * وفي علاوة أو خصم محلي ساعات، فيه خانة تضبطها.
 */
object Gold {
    const val GRAMS_PER_OUNCE = 31.1034768
    const val POUND_GRAMS_21K = 8.0

    enum class Karat(val k: Int, val ar: String) {
        K24(24, "عيار 24"), K21(21, "عيار 21"), K18(18, "عيار 18"), K14(14, "عيار 14")
    }

    /** سعر جرام العيار بالجنيه */
    fun gram(ounceUsd: Double, usdEgp: Double, karat: Int, premiumPct: Double = 0.0): Double =
        ounceUsd / GRAMS_PER_OUNCE * usdEgp * (1 + premiumPct / 100.0) * karat / 24.0

    fun pound(ounceUsd: Double, usdEgp: Double, premiumPct: Double = 0.0) =
        gram(ounceUsd, usdEgp, 21, premiumPct) * POUND_GRAMS_21K

    fun ounceEgp(ounceUsd: Double, usdEgp: Double, premiumPct: Double = 0.0) =
        ounceUsd * usdEgp * (1 + premiumPct / 100.0)

    /** {"price": 4026.9, ...} */
    fun parse(json: String): Double? = runCatching {
        JSONObject(json).getDouble("price").takeIf { it in 100.0..100_000.0 }
    }.getOrNull()

    /** goldprice.org: {"items":[{"xauPrice":4026.9,...}]} (احتياطي لو الأول فشل) */
    fun parseBackup(json: String): Double? = runCatching {
        JSONObject(json).getJSONArray("items").getJSONObject(0).getDouble("xauPrice").takeIf { it in 100.0..100_000.0 }
    }.getOrNull()

    suspend fun fetchOunceUsd(): Double? = withContext(Dispatchers.IO) {
        suspend fun get(url: String): String? = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "Nabd/${com.nabd.money.BuildConfig.VERSION_NAME} (Android)")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
        get("https://api.gold-api.com/price/XAU")?.let(::parse)
            ?: get("https://data-asg.goldprice.org/dbXRates/USD")?.let(::parseBackup)
    }
}

package com.nabd.money.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * سعر الصرف الحي: كل عملة مقابل دولار واحد، من open.er-api.com (مجاني ومن غير حساب).
 * ده سعر السوق الرسمي، ولو سعرك غيره (البنك مثلاً) تقدر تقفل المزامنة وتكتب السعر بإيدك.
 */
object Rates {
    val CURRENCIES = listOf("EGP", "USD", "USDT", "EUR", "GBP", "SAR", "AED")

    data class Live(val perUsd: Map<String, Double>)

    suspend fun fetch(): Live? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            if (c.responseCode != 200) return@runCatching null
            parse(c.inputStream.bufferedReader().use { it.readText() })
        }.getOrNull()
    }

    fun parse(json: String): Live? {
        val o = JSONObject(json)
        if (o.optString("result") != "success") return null
        val r = o.getJSONObject("rates")
        val out = mutableMapOf<String, Double>()
        for (c in CURRENCIES) if (r.has(c)) out[c] = r.getDouble(c)
        val egp = out["EGP"] ?: return null
        if (egp < 1.0) return null
        out["USD"] = 1.0
        out["USDT"] = 1.0
        return Live(out)
    }
}

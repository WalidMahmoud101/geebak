package com.nabd.money.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class SavedFilter(val name: String, val filter: TxnFilter)

/** حفظ الفلاتر كـ JSON في الإعدادات */
object FilterCodec {
    fun encode(list: List<SavedFilter>): String = JSONArray().apply {
        list.forEach { s ->
            val f = s.filter
            put(JSONObject().apply {
                put("name", s.name)
                put("period", f.period.name)
                f.month?.let { put("month", it.toString()) }
                f.fromDay?.let { put("from", it) }
                f.toDay?.let { put("to", it) }
                f.direction?.let { put("dir", it.name) }
                put("purposes", JSONArray(f.purposes.map { it.name }))
                put("categories", JSONArray(f.categories.toList()))
                put("accounts", JSONArray(f.accountIds.toList()))
                put("sources", JSONArray(f.sources.map { it.name }))
                put("query", f.query)
                f.minAmount?.let { put("min", it) }
                f.maxAmount?.let { put("max", it) }
                put("company", f.companyOnly)
                put("excluded", f.showExcluded)
                put("showCompany", f.showCompany)
            })
        }
    }.toString()

    fun decode(json: String?): List<SavedFilter> = runCatching {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            SavedFilter(
                o.getString("name"),
                TxnFilter(
                    period = Period.valueOf(o.optString("period", "THIS_MONTH")),
                    month = o.optString("month").takeIf { it.isNotBlank() }?.let(YearMonth::parse),
                    fromDay = if (o.has("from")) o.getLong("from") else null,
                    toDay = if (o.has("to")) o.getLong("to") else null,
                    direction = o.optString("dir").takeIf { it.isNotBlank() }?.let(Direction::valueOf),
                    purposes = strings("purposes").map(Purpose::valueOf).toSet(),
                    categories = strings("categories").toSet(),
                    accountIds = o.optJSONArray("accounts")?.let { a -> (0 until a.length()).map { a.getLong(it) } }?.toSet() ?: emptySet(),
                    sources = strings("sources").map(TxnSource::valueOf).toSet(),
                    query = o.optString("query"),
                    minAmount = if (o.has("min")) o.getDouble("min") else null,
                    maxAmount = if (o.has("max")) o.getDouble("max") else null,
                    companyOnly = o.optBoolean("company"),
                    showExcluded = o.optBoolean("excluded"),
                    showCompany = o.optBoolean("showCompany"),
                )
            )
        }
    }.getOrDefault(emptyList())
}

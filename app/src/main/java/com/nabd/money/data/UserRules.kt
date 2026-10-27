package com.nabd.money.data

import org.json.JSONArray
import org.json.JSONObject

/** قاعدة من عندك: أي عملية فيها الكلمة دي تتصنف كده لوحدها */
data class UserRule(val keyword: String, val category: String, val purpose: Purpose?)

/** تصنيفاتك وقواعدك: محفوظة JSON في الإعدادات */
data class UserTaxonomy(
    val customCategories: List<String> = emptyList(),
    /** الغرض الثابت لكل تصنيف، مثلاً ترفيه ← الخروجات */
    val categoryPurpose: Map<String, Purpose> = emptyMap(),
    val rules: List<UserRule> = emptyList(),
) {
    fun encode(): String = JSONObject().apply {
        put("custom", JSONArray(customCategories))
        put("purposes", JSONObject().apply { categoryPurpose.forEach { (k, v) -> put(k, v.name) } })
        put("rules", JSONArray().apply {
            rules.forEach { r ->
                put(JSONObject().put("k", r.keyword).put("c", r.category).put("p", r.purpose?.name ?: ""))
            }
        })
    }.toString()

    companion object {
        fun decode(json: String?): UserTaxonomy = runCatching {
            val o = JSONObject(json ?: "{}")
            val custom = o.optJSONArray("custom")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val purposes = o.optJSONObject("purposes")?.let { p ->
                p.keys().asSequence().mapNotNull { k -> runCatching { k to Purpose.valueOf(p.getString(k)) }.getOrNull() }.toMap()
            } ?: emptyMap()
            val rules = o.optJSONArray("rules")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { r ->
                    UserRule(r.getString("k"), r.getString("c"), r.optString("p").takeIf { it.isNotBlank() }?.let(Purpose::valueOf))
                }
            } ?: emptyList()
            UserTaxonomy(custom, purposes, rules)
        }.getOrDefault(UserTaxonomy())
    }
}

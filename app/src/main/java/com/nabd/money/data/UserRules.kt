package com.nabd.money.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * قاعدة من عندك: أي عملية فيها الجزء ده من الرسالة تتصنف كده لوحدها وتاخد الاسم ده.
 * الجزء ممكن فيه # = أي رقم (مثلاً "تحويل الى 010#" بتطابق أي رقم تليفون).
 */
data class UserRule(val keyword: String, val category: String, val purpose: Purpose?, val name: String? = null) {
    private val matcher: (String) -> Boolean by lazy {
        val k = com.nabd.money.agents.ArabicNumbers.norm(keyword).trim()
        if ('#' in k) {
            val rx = Regex(k.split('#').joinToString("\\d+(?:[.,]\\d+)*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
            ({ hay: String -> rx.containsMatchIn(hay) })
        } else {
            ({ hay: String -> k.isNotEmpty() && hay.contains(k) })
        }
    }

    /** hay لازم يكون متنضف بـ ArabicNumbers.norm */
    fun matches(hay: String): Boolean = matcher(hay)

    fun matchesRaw(vararg parts: String?): Boolean =
        matches(com.nabd.money.agents.ArabicNumbers.norm(parts.filterNotNull().joinToString(" ")))
}

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
                put(JSONObject().put("k", r.keyword).put("c", r.category).put("p", r.purpose?.name ?: "").put("n", r.name ?: ""))
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
                    UserRule(r.getString("k"), r.getString("c"), r.optString("p").takeIf { it.isNotBlank() }?.let(Purpose::valueOf), r.optString("n").takeIf { it.isNotBlank() })
                }
            } ?: emptyList()
            UserTaxonomy(custom, purposes, rules)
        }.getOrDefault(UserTaxonomy())
    }
}

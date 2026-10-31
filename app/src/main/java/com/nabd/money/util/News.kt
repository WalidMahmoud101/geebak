package com.nabd.money.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * رسايل صاحب التطبيق لكل المستخدمين، من غير سيرفر ولا حسابات: ملف news.json على موقع جيبك.
 * التطبيق بيقراه بس، ومفيش حاجة عن المستخدم بتتبعت. الرسالة نص بس، فمفيش روابط ولا أوامر.
 */
object News {
    data class Message(val id: Int, val title: String, val body: String)

    /** الاسمين علشان تغيير اسم الريبو مايقطعش الرسايل */
    private val URLS = listOf(
        "https://walidmahmoud101.github.io/geebak/news.json",
        "https://walidmahmoud101.github.io/nabd/news.json",
    )
    private const val MAX_TITLE = 80
    private const val MAX_BODY = 500

    /** أحدث رسالة في الملف. أي شكل غلط بيتجاهل بهدوء */
    fun parse(json: String): Message? = runCatching {
        val list = JSONObject(json).getJSONArray("messages")
        (0 until list.length()).map { list.getJSONObject(it) }.mapNotNull { o ->
            val id = o.optInt("id")
            val title = o.optString("title").trim().take(MAX_TITLE)
            val body = o.optString("body").trim().take(MAX_BODY)
            Message(id, title, body).takeIf { id > 0 && title.isNotEmpty() }
        }.maxByOrNull { it.id }
    }.getOrNull()

    suspend fun latest(): Message? = withContext(Dispatchers.IO) {
        for (url in URLS) {
            val text = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.useCaches = false
                if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            }.getOrNull() ?: continue
            return@withContext parse(text)
        }
        null
    }
}

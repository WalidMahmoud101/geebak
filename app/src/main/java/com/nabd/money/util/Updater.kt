package com.nabd.money.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val release: Updater.Release, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * التحديث التلقائي من GitHub Releases: كل نسخة جديدة بتتنشر كـ release اسمه v<versionCode>
 * وجواه ملف nabd.apk. التطبيق بيشيك، ينزل، ويفتح شاشة التثبيت.
 * ده الاتصال الوحيد بالنت في التطبيق، وبياناتك المالية مابتطلعش من الموبايل.
 */
object Updater {
    const val REPO = "WalidMahmoud101/nabd"

    data class Release(
        val versionCode: Int,
        val name: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    /** نتيجة الفحص: نسخة، أو سبب إننا ماقدرناش نوصل (علشان نقولك ليه بالظبط) */
    data class Check(val release: Release?, val error: String?)

    suspend fun latest(): Release? = check().release

    /**
     * بيجرب API بتاع GitHub الأول (فيه الحجم وملاحظات النسخة)، ولو فشل (حد الطلبات، حجب، بطء…)
     * بيجرب صفحة النسخة الأخيرة العادية اللي مالهاش حد طلبات، وبعدها بنزل الملف من رابطه المباشر.
     */
    suspend fun check(): Check = withContext(Dispatchers.IO) {
        var why: String? = null
        for (attempt in 1..2) {
            val viaApi = runCatching { fromApi() }
            viaApi.getOrNull()?.let { return@withContext Check(it, null) }
            why = viaApi.exceptionOrNull()?.let(::explain) ?: "رد غير متوقع من السيرفر"
            val viaPage = runCatching { fromReleasePage() }
            viaPage.getOrNull()?.let { return@withContext Check(it, null) }
            why = viaPage.exceptionOrNull()?.let(::explain) ?: why
        }
        Check(null, why)
    }

    private class HttpFail(val code: Int) : Exception("HTTP $code")

    private fun open(url: String, redirects: Boolean = true): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = redirects
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Nabd/${com.nabd.money.BuildConfig.VERSION_NAME} (Android)")
        return c
    }

    private fun fromApi(): Release? {
        val c = open("https://api.github.com/repos/$REPO/releases/latest")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) throw HttpFail(c.responseCode)
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val code = o.getString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: return null
        val assets = o.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        return Release(
            versionCode = code,
            name = o.optString("name").ifBlank { o.getString("tag_name") },
            notes = o.optString("body"),
            apkUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    /** من غير API: github.com/.../releases/latest بيحوّل لصفحة آخر نسخة (v17)، ومنها نعرف رقم النسخة ورابط الملف */
    private fun fromReleasePage(): Release? {
        val c = open("https://github.com/$REPO/releases/latest", redirects = false)
        c.requestMethod = "HEAD"
        if (c.responseCode !in 300..399) throw HttpFail(c.responseCode)
        return releaseFromLocation(c.getHeaderField("Location"))
    }

    fun releaseFromLocation(location: String?): Release? {
        val tag = location?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.trim('/').orEmpty()
        val code = tag.filter { it.isDigit() }.toIntOrNull() ?: return null
        return Release(
            versionCode = code,
            name = "نبض (النسخة $code)",
            notes = "",
            apkUrl = "https://github.com/$REPO/releases/download/$tag/nabd.apk",
            sizeBytes = 0,
        )
    }

    /** السبب بالعربي، علشان تعرف المشكلة من النت ولا من السيرفر ولا من الموبايل */
    fun explain(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "مفيش اتصال بالنت، أو موقع GitHub محجوب على شبكتك"
        is java.net.SocketTimeoutException -> "النت بطيء أو بيقطع"
        is javax.net.ssl.SSLException -> "مشكلة أمان في الاتصال، اتأكد إن تاريخ وساعة الموبايل صح"
        is HttpFail -> when (e.code) {
            403, 429 -> "السيرفر رفض مؤقتاً (كتير طلبات)، جرب بعد شوية"
            404 -> "ماوجدتش النسخة على السيرفر"
            else -> "السيرفر رد بخطأ ${e.code}"
        }
        else -> e.javaClass.simpleName
    }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /**
     * لو النسخة دي اتنزلت قبل كده: نتأكد إن الملف كامل وسليم (الحجم، وإنه نبض بنفس رقم النسخة)
     * ونرجعه علشان مانزلوش تاني. أي ملف ناقص أو قديم أو بايظ بيتمسح.
     */
    suspend fun cached(ctx: Context, r: Release): File? = withContext(Dispatchers.IO) {
        val keep = File(dir(ctx), "nabd-${r.versionCode}.apk")
        dir(ctx).listFiles()?.filter { it != keep }?.forEach { it.delete() }
        if (!keep.exists()) return@withContext null
        val sizeOk = r.sizeBytes <= 0 || keep.length() == r.sizeBytes
        val info = if (sizeOk) runCatching { ctx.packageManager.getPackageArchiveInfo(keep.path, 0) }.getOrNull() else null
        val valid = info != null && info.packageName == ctx.packageName &&
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) == r.versionCode.toLong()
        if (valid) keep else { keep.delete(); null }
    }

    /** بعد ما التطبيق يتحدث: نمسح ملفات التحديث اللي خلصت */
    fun cleanup(ctx: Context, currentVersionCode: Int) {
        dir(ctx).listFiles()?.forEach { f ->
            val code = f.name.removePrefix("nabd-").substringBefore('.').toIntOrNull()
            if (code == null || code <= currentVersionCode) f.delete()
        }
    }

    suspend fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File? {
        // مرتين: النت ساعات بيقطع في نص التحميل
        return downloadOnce(ctx, r, onProgress) ?: downloadOnce(ctx, r, onProgress)
    }

    private suspend fun downloadOnce(ctx: Context, r: Release, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = dir(ctx)
            dir.listFiles()?.forEach { it.delete() }
            val final = File(dir, "nabd-${r.versionCode}.apk")
            // بينزل في ملف مؤقت ويتسمى باسمه الحقيقي لما يخلص بس، علشان الناقص مايتحسبش نازل
            val out = File(dir, "nabd-${r.versionCode}.part")
            var url = URL(r.apkUrl)
            var c: HttpURLConnection
            // GitHub بيحول التحميل لسيرفر تاني
            while (true) {
                c = url.openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 20_000
                c.readTimeout = 60_000
                c.setRequestProperty("User-Agent", "Nabd/${com.nabd.money.BuildConfig.VERSION_NAME} (Android)")
                if (c.responseCode in 300..399) {
                    url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue
                }
                break
            }
            if (c.responseCode != 200) return@runCatching null
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                    }
                }
            }
            val expected = if (r.sizeBytes > 0) r.sizeBytes else total
            if (expected > 0 && out.length() != expected) { out.delete(); return@runCatching null }
            if (!out.renameTo(final)) return@runCatching null
            final
        }.getOrNull()
    }

    fun install(ctx: Context, file: File) {
        val pm = ctx.packageManager
        if (!pm.canRequestPackageInstalls()) {
            // أول مرة بس: لازم تسمح لنبض يثبت تحديثاته
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

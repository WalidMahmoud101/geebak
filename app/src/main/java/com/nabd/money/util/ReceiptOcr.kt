package com.nabd.money.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.googlecode.tesseract.android.TessBaseAPI
import com.nabd.money.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** النص اللي طلع من الصورة، ومتوسط ثقة Tesseract فيه (0-100) */
data class OcrResult(val text: String, val confidence: Int)

/**
 * قراءة الفاتورة على الموبايل بس (Tesseract): الصورة مابتطلعش برا التطبيق، ولا النص.
 * لقطة الكاميرا بتتمسح بعد القراية، وصورة المعرض مابنلمسهاش ولا بناخد منها نسخة.
 * اللي بيفضل عندنا هو الأصناف والأسعار اللي انت راجعتها.
 */
object ReceiptOcr {
    private const val LANGS = "ara+eng"
    /** أطول ضلع: أكبر من كده بطء من غير فايدة، وذاكرة زيادة على الموبايلات الضعيفة */
    private const val MAX_SIDE = 3000
    /** عرض أقل من كده الحروف بتبقى صغيرة على Tesseract، فبنكبّر */
    private const val MIN_SIDE = 1000

    private val busy = Mutex()

    /** ملف مؤقت للكاميرا، في مكان مابيدخلش في أي نسخة احتياطية */
    fun newCaptureUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    }

    fun clearCaptures(ctx: Context) {
        File(ctx.cacheDir, "receipts").listFiles()?.forEach { it.delete() }
    }

    suspend fun read(ctx: Context, uri: Uri): OcrResult = busy.withLock {
        withContext(Dispatchers.Default) {
            val bitmap = load(ctx, uri)?.let(::prepared) ?: error("مقدرتش أفتح الصورة")
            val dataDir = ensureModels(ctx)
            val tess = TessBaseAPI()
            try {
                check(tess.init(dataDir.absolutePath, LANGS, TessBaseAPI.OEM_LSTM_ONLY)) { "مقدرتش أشغّل قارئ الفواتير" }
                // عمود واحد من السطور، وتظبيط الإضاءة لكل حتة لوحدها علشان ضل الإيد والفاتورة المكرمشة
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                tess.setVariable("preserve_interword_spaces", "1")
                tess.setVariable("thresholding_method", "2")
                tess.setVariable("user_defined_dpi", "300")
                tess.setImage(bitmap)
                val text = tess.getUTF8Text().orEmpty()
                OcrResult(text, tess.meanConfidence())
            } finally {
                tess.recycle()
                bitmap.recycle()
            }
        }
    }

    /** بنفرد الورقة ونشيل الخلفية والضل، ولو الموبايل ماستحملش نقرا الصورة زي ما هي */
    private fun prepared(photo: Bitmap): Bitmap = try {
        val argb = IntArray(photo.width * photo.height)
        photo.getPixels(argb, 0, photo.width, 0, 0, photo.width, photo.height)
        val gray = ReceiptImage.prepare(argb, photo.width, photo.height)
        val out = IntArray(gray.pixels.size) { i ->
            val v = gray.pixels[i].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(out, gray.width, gray.height, Bitmap.Config.ARGB_8888).also { photo.recycle() }
    } catch (_: OutOfMemoryError) {
        photo
    }

    /** Tesseract محتاج ملفات حقيقية، فبننسخ النماذج من جوه التطبيق مرة واحدة لكل إصدار */
    private suspend fun ensureModels(ctx: Context): File = withContext(Dispatchers.IO) {
        val root = File(ctx.noBackupFilesDir, "tesseract")
        val dir = File(root, "tessdata").apply { mkdirs() }
        val stamp = File(root, "ready-${BuildConfig.VERSION_CODE}")
        LANGS.split('+').forEach { lang ->
            val name = "$lang.traineddata"
            val target = File(dir, name)
            if (stamp.exists() && target.exists()) return@forEach
            val tmp = File(dir, "$name.tmp")
            ctx.assets.open("tessdata/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "مقدرتش أجهّز قارئ الفواتير" }
        }
        stamp.createNewFile()
        root
    }

    private fun load(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = cr.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } ?: 0
        val longest = maxOf(raw.width, raw.height).toFloat()
        val shortest = minOf(raw.width, raw.height).toFloat()
        val scale = when {
            longest > MAX_SIDE -> MAX_SIDE / longest
            shortest < MIN_SIDE -> minOf(MIN_SIDE / shortest, MAX_SIDE / longest)
            else -> 1f
        }
        if (degrees == 0 && scale == 1f) return raw
        val m = Matrix().apply { postRotate(degrees.toFloat()); postScale(scale, scale) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }

    private fun rotationOf(e: ExifInterface) = when (e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

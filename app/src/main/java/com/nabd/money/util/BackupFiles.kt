package com.nabd.money.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.nabd.money.BuildConfig
import com.nabd.money.NabdApp
import com.nabd.money.data.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * حفظ وقراية ملف النسخة الاحتياطية في أي مكان تختاره (Google Drive أو فولدر على الموبايل)
 * عن طريق شاشة الملفات بتاعة أندرويد، من غير تسجيل دخول.
 */
object BackupFiles {
    const val MIME = "application/json"
    private const val AUTO_PREFIX = "nabd-auto-"
    private const val KEEP = 7

    fun suggestedName(prefix: String = "nabd-backup-"): String {
        val d = LocalDateTime.now()
        return "%s%04d-%02d-%02d_%02d%02d.json".format(java.util.Locale.US, prefix, d.year, d.monthValue, d.dayOfMonth, d.hour, d.minute)
    }

    suspend fun build(app: NabdApp): String = Backup.export(app.db, app.settings, BuildConfig.VERSION_NAME)

    suspend fun writeTo(ctx: Context, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** ابعت النسخة (واتساب، Drive، إيميل…) من شاشة المشاركة */
    suspend fun share(ctx: Context, json: String) {
        val file = withContext(Dispatchers.IO) {
            File(File(ctx.cacheDir, "exports").apply { mkdirs() }, suggestedName()).apply { writeText(json) }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية من جيبك")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "احفظ النسخة في…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** اسم الفولدر اللي اخترته للنسخة التلقائية */
    fun folderName(ctx: Context, tree: String?): String? = runCatching {
        val uri = Uri.parse(tree ?: return null)
        val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /**
     * النسخة التلقائية: ملف جديد في الفولدر اللي اخترته مرة في اليوم،
     * وبيفضل آخر 7 نسخ بس علشان المساحة.
     */
    suspend fun autoBackup(app: NabdApp): Boolean = withContext(Dispatchers.IO) {
        val tree = app.settings.backupFolder ?: return@withContext false
        val today = Dates.dayKey(System.currentTimeMillis())
        if (app.settings.lastAutoBackupDay == today) return@withContext false
        runCatching {
            val treeUri = Uri.parse(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val created = DocumentsContract.createDocument(app.contentResolver, parent, MIME, suggestedName(AUTO_PREFIX))
                ?: return@runCatching false
            writeTo(app, created, build(app))
            app.settings.lastAutoBackupDay = today
            app.settings.lastBackupAt = System.currentTimeMillis()
            prune(app, treeUri)
            true
        }.getOrDefault(false)
    }

    private fun prune(ctx: Context, treeUri: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val autos = mutableListOf<Pair<String, String>>() // id to name
        ctx.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(AUTO_PREFIX)) autos += c.getString(0) to name
            }
        }
        // الأسماء فيها التاريخ، فالترتيب بالاسم = بالتاريخ
        autos.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id)) }
        }
    }
}

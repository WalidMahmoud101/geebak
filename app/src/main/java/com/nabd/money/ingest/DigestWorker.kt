package com.nabd.money.ingest

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nabd.money.NabdApp
import java.util.concurrent.TimeUnit

/** المتابعة الدورية: وكلاء التحليل + ملخص اليوم */
class DigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NabdApp
        // مراجعة تلقائية: أي رسالة بنك فاتت (الموبايل كان مقفول مثلاً) تتسجل من غير ما تلمس حاجة
        InboxScanner.scan(applicationContext, app.engine, days = 3, notify = true)
        app.engine.runPeriodicInsights(notify = true)
        app.engine.dailyDigest()
        // نسخة احتياطية تلقائية مرة في اليوم لو اخترت فولدر
        com.nabd.money.util.BackupFiles.autoBackup(app)
        // التحديثات: إشعار مرة واحدة لكل نسخة جديدة
        com.nabd.money.util.Updater.latest()?.let { r ->
            if (r.versionCode > com.nabd.money.BuildConfig.VERSION_CODE && app.settings.notifiedUpdate < r.versionCode) {
                app.settings.notifiedUpdate = r.versionCode
                com.nabd.money.util.Notifier.update(applicationContext, r.name, r.notes.lineSequence().take(3).joinToString("\n"))
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DigestWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("nabd_digest", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

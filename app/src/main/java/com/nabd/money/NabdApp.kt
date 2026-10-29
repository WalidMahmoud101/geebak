package com.nabd.money

import android.app.Application
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.nabd.money.ingest.NotificationCatcher
import com.nabd.money.agents.AgentEngine
import com.nabd.money.data.NabdDatabase
import com.nabd.money.data.Settings
import com.nabd.money.ingest.DigestWorker
import com.nabd.money.util.Notifier
import kotlinx.coroutines.launch

class NabdApp : Application() {
    lateinit var db: NabdDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var engine: AgentEngine
        private set

    override fun onCreate() {
        super.onCreate()
        db = NabdDatabase.build(this)
        settings = Settings(this)
        engine = AgentEngine(this, db.dao(), settings)
        Notifier.ensureChannels(this)
        // مرة واحدة بعد التحديث: نلم الكروت والحسابات اللي اتكررت قبل كده
        // وبعد كده: نشيل الإعلانات اللي اتسجلت كأنها عمليات
        if (settings.accountsTidied < 4) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching {
                    if (settings.accountsTidied < 1) engine.tidyAccounts()
                    if (settings.accountsTidied < 2) engine.purgeAds()
                    if (settings.accountsTidied < 3) engine.adoptCash()
                    engine.fixFeeDirections()
                }.onSuccess { settings.accountsTidied = 4 }
            }
        }
        runCatching { DigestWorker.schedule(this) }
        // لو السيستم قفل خدمة الإشعارات، نطلب منه يرجعها علشان التسجيل يفضل تلقائي
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(this, NotificationCatcher::class.java))
            }
        }
    }
}

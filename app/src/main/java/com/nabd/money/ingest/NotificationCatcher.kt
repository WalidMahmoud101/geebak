package com.nabd.money.ingest

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nabd.money.NabdApp
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.TxnSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * بيقرا إشعارات التطبيقات اللي مالهاش رسائل SMS:
 * InstaPay، RedotPay، Kast، Binance، Google Wallet، تطبيقات البنوك والمحافظ.
 */
class NotificationCatcher : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // تطبيقات الرسائل والشات: الـ SMS بتتقري من SmsReceiver، والشات مش فلوس
    private val skip = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca",
        "org.telegram.messenger", "com.android.systemui", "android", "com.google.android.gm",
    )

    /** أول ما الخدمة تشتغل: نلقط الإشعارات اللي لسه ظاهرة (وصلت والخدمة واقفة) */
    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in skip) return
        if (pkg == runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (text.isBlank()) return

        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("")

        val app = application as NabdApp
        scope.launch {
            app.engine.ingest(
                RawMessage(
                    channel = TxnSource.NOTIFICATION,
                    sender = pkg,
                    appLabel = label,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime,
                )
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

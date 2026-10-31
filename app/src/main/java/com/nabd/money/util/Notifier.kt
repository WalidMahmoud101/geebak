package com.nabd.money.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nabd.money.R
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.Account
import com.nabd.money.data.Direction
import com.nabd.money.data.Insight
import com.nabd.money.data.Txn
import com.nabd.money.ui.MainActivity

object Notifier {
    private const val CH_CAPTURE = "captures"
    private const val CH_ALERTS = "alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CAPTURE, "العمليات الملتقطة", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "تنبيهات الوكلاء", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun capture(ctx: Context, t: Txn, account: Account) {
        val agent = AgentRegistry.byId(t.agentId)?.name ?: "جيبك"
        val verb = if (t.direction == Direction.OUT) "اتخصم" else "اتضاف"
        val title = "$verb ${Money.fmt(t.amount, t.currency)} • ${account.name}"
        val rest = Money.fmt(account.balance, account.currency)
        val body = buildString {
            t.merchant?.let { append("$it • ") }
            append("${t.purpose.ar} / ${t.category}")
            append("\nالباقي: $rest")
            if (!account.balanceKnown) append(" (تقديري)")
            append("\nالتقطها: $agent")
        }
        post(ctx, CH_CAPTURE, (t.id % Int.MAX_VALUE).toInt(), title, body)
    }

    fun insight(ctx: Context, i: Insight) {
        post(ctx, CH_ALERTS, 100_000 + (i.dedupKey.hashCode() and 0xFFFF), i.title, i.body)
    }

    fun update(ctx: Context, name: String, notes: String) {
        post(ctx, CH_ALERTS, 98, "تحديث جديد: $name", notes.ifBlank { "افتح جيبك ودوس \"نزّل وثبّت\"." })
    }

    fun digest(ctx: Context, title: String, body: String) {
        post(ctx, CH_ALERTS, 99, title, body)
    }

    private fun canNotify(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, channel: String, id: Int, title: String, body: String) {
        if (!canNotify(ctx)) return
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setColor(0xFF2EF2B5.toInt())
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

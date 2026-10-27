package com.nabd.money.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.nabd.money.agents.AgentEngine
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.TxnSource

/** بيفحص الرسائل القديمة في الصندوق علشان يبني تاريخ مصاريفك من أول يوم */
object InboxScanner {
    data class Result(val scanned: Int, val captured: Int)

    suspend fun scan(ctx: Context, engine: AgentEngine, days: Int, notify: Boolean = false): Result {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result(0, 0)
        }
        val since = System.currentTimeMillis() - days * 86_400_000L
        var scanned = 0
        var captured = 0
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                scanned++
                val msg = RawMessage(TxnSource.SMS, address, text = body, timestamp = c.getLong(2))
                if (engine.ingest(msg, notify) != null) captured++
            }
        }
        return Result(scanned, captured)
    }
}

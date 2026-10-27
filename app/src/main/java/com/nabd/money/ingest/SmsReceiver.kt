package com.nabd.money.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nabd.money.NabdApp
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.TxnSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** بيستقبل رسائل البنوك والمحافظ أول ما توصل */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val grouped = parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }
        val app = context.applicationContext as NabdApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                grouped.forEach { (address, msgs) ->
                    val body = msgs.joinToString("") { it.messageBody.orEmpty() }
                    app.engine.ingest(
                        RawMessage(
                            channel = TxnSource.SMS,
                            sender = address,
                            text = body,
                            timestamp = msgs.first().timestampMillis,
                        )
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}

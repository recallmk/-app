package com.personal.autoledger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "短信"
        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody ?: "")
        }

        val parsed = BillParser.parse("sms", sender, fullBody.toString())
        if (parsed != null) {
            QuickRecordLauncher.triggerQuickRecord(context, parsed)
        }
    }
}

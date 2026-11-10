package com.personal.autoledger

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PaymentNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // 忽略本应用自己发出的记账提醒通知
        if (pkg == packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val content = if (bigText.isNotBlank()) bigText else text
        if (title.isBlank() && content.isBlank()) return

        val parsed = BillParser.parse(pkg, title, content)
        if (parsed != null) {
            QuickRecordLauncher.triggerQuickRecord(applicationContext, parsed)
        }
    }
}

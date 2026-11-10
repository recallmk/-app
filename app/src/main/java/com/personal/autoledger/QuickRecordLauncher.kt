package com.personal.autoledger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification

object QuickRecordLauncher {

    private const val CHANNEL_ID = "auto_ledger_quick_record"

    /**
     * 当监听到短信、微信或支付宝账单时：
     * 1. 先以“待补充用途”自动写入本地数据库，确保哪怕当时没空点弹窗也绝不漏账；
     * 2. 立即拉起快速备注弹窗（并发送一条高优先级通知作为后台拉起兜底），方便随手填写“这笔钱用来干什么”。
     */
    fun triggerQuickRecord(context: Context, parsed: ParsedBill) {
        if (BillParser.isDuplicate(parsed.channel, parsed.amount)) {
            return
        }

        // 先落库保存，防止用户锁屏或忙时漏记
        val db = DatabaseHelper(context)
        val recordId = db.insertBill(
            BillRecord(
                type = parsed.type,
                amount = parsed.amount,
                channel = parsed.channel,
                merchant = parsed.merchant,
                purpose = "待补充用途（${parsed.merchant}）",
                category = if (parsed.type == "INCOME") "收入" else "日常消费",
                timestamp = System.currentTimeMillis(),
                rawText = parsed.rawText
            )
        )

        val popupIntent = Intent(context, QuickRecordActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("record_id", recordId)
            putExtra("type", parsed.type)
            putExtra("amount", parsed.amount)
            putExtra("channel", parsed.channel)
            putExtra("merchant", parsed.merchant)
            putExtra("raw_text", parsed.rawText)
        }

        // 发送高优先级通知（兼容 Android 10+ 后台弹窗限制）
        sendHighPriorityNotification(context, recordId.toInt(), parsed, popupIntent)

        // 尝试直接拉起弹窗 Activity
        try {
            context.startActivity(popupIntent)
        } catch (_: Exception) {
            // 若受后台限制未能直接弹出，用户点击通知栏卡片即可打开同一个弹窗
        }
    }

    private fun sendHighPriorityNotification(
        context: Context,
        notificationId: Int,
        parsed: ParsedBill,
        popupIntent: Intent
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自动记账用途填写提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测到微信/支付宝/短信收支时提醒填写资金用途"
            }
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = if (parsed.type == "INCOME") "收入 +" else "支出 -"
        val title = "已自动记下 [${parsed.channel}] $typeLabel¥${String.format("%.2f", parsed.amount)}"
        val body = "点击补充这笔钱用来干什么（商户：${parsed.merchant}）"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        nm.notify(notificationId, notification)
    }
}

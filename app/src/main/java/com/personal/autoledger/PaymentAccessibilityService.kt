package com.personal.autoledger

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PaymentAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.tencent.mm" && pkg != "com.eg.android.AlipayGphone") return

        val rootNode = rootInActiveWindow ?: return
        val collectedTexts = mutableListOf<String>()
        traverseNodeText(rootNode, collectedTexts)

        if (collectedTexts.isEmpty()) return
        val joined = collectedTexts.joinToString(" ")

        // 仅当页面包含明确的“支付成功”、“付款成功”、“收款成功”特征词时才触发解析
        val hasPaymentFinishedSignal = joined.contains("支付成功") ||
                joined.contains("付款成功") ||
                joined.contains("成功付款") ||
                joined.contains("收款到账")

        if (hasPaymentFinishedSignal) {
            val parsed = BillParser.parse(pkg, "支付结果页", joined)
            if (parsed != null) {
                QuickRecordLauncher.triggerQuickRecord(applicationContext, parsed)
            }
        }
    }

    private fun traverseNodeText(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { outList.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { outList.add(it) }
        for (i in 0 until node.childCount) {
            traverseNodeText(node.getChild(i), outList)
        }
    }

    override fun onInterrupt() {}
}

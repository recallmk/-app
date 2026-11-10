package com.personal.autoledger

import java.util.regex.Pattern

data class ParsedBill(
    val type: String,       // "EXPENSE" (支出) 或 "INCOME" (收入)
    val amount: Double,     // 金额
    val channel: String,    // "微信" | "支付宝" | "银行短信"
    val merchant: String,   // 交易对方 / 商户名称
    val rawText: String     // 原始捕获文本
)

object BillParser {

    // 防止短时间内通知栏+无障碍重复触发同一笔交易
    private var lastTriggerTime = 0L
    private var lastAmount = -1.0
    private var lastChannel = ""

    @Synchronized
    fun isDuplicate(channel: String, amount: Double): Boolean {
        val now = System.currentTimeMillis()
        if (channel == lastChannel && Math.abs(amount - lastAmount) < 0.001 && (now - lastTriggerTime) < 6000L) {
            return true
        }
        lastChannel = channel
        lastAmount = amount
        lastTriggerTime = now
        return false
    }

    /**
     * 解析微信、支付宝通知或银行动账短信
     */
    fun parse(sourcePackage: String, title: String, content: String): ParsedBill? {
        val fullText = "${title.trim()} ${content.trim()}".replace("\s+".toRegex(), " ")

        // 1. 微信解析规则 (com.tencent.mm)
        if (sourcePackage == "com.tencent.mm" || fullText.contains("微信支付") || fullText.contains("微信收款")) {
            return parseWeChat(fullText)
        }

        // 2. 支付宝解析规则 (com.eg.android.AlipayGphone)
        if (sourcePackage == "com.eg.android.AlipayGphone" || fullText.contains("支付宝")) {
            return parseAlipay(fullText)
        }

        // 3. 银行短信解析规则
        return parseBankSms(fullText)
    }

    private fun parseWeChat(text: String): ParsedBill? {
        // 微信收款：微信支付收款0.01元 / 个人收款码到账￥15.50 / 收款到账28.00元
        val incomeMatcher = Pattern.compile("(?:收款|到账|收到转账|已收钱)[^0-9]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?").matcher(text)
        if (incomeMatcher.find()) {
            val amount = incomeMatcher.group(1)?.toDoubleOrNull() ?: return null
            if (amount > 0) {
                return ParsedBill(
                    type = "INCOME",
                    amount = amount,
                    channel = "微信",
                    merchant = extractMerchant(text, "微信收款"),
                    rawText = text
                )
            }
        }

        // 微信支出：微信支付：¥35.00 / 付款金额￥18.50 / 扣费成功...25.00元 / 支付成功...￥12.00
        val expenseMatcher = Pattern.compile("(?:付款金额|支付金额|微信支付|支付成功|扣费|消费|向.*付款)[^0-9¥￥]*[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)").matcher(text)
        if (expenseMatcher.find()) {
            val amount = expenseMatcher.group(1)?.toDoubleOrNull() ?: return null
            if (amount > 0) {
                return ParsedBill(
                    type = "EXPENSE",
                    amount = amount,
                    channel = "微信",
                    merchant = extractMerchant(text, "微信商户"),
                    rawText = text
                )
            }
        }

        // 兜底：包含¥/￥符号且包含支付/收款字样
        val symbolMatcher = Pattern.compile("[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)").matcher(text)
        if (symbolMatcher.find() && (text.contains("支付") || text.contains("付款") || text.contains("收款"))) {
            val amount = symbolMatcher.group(1)?.toDoubleOrNull() ?: return null
            val isIncome = text.contains("收款") || text.contains("到账")
            return ParsedBill(
                type = if (isIncome) "INCOME" else "EXPENSE",
                amount = amount,
                channel = "微信",
                merchant = extractMerchant(text, "微信交易"),
                rawText = text
            )
        }
        return null
    }

    private fun parseAlipay(text: String): ParsedBill? {
        // 支付宝收入：成功收款15.00元 / 收到一笔转账200.00元 / 余额宝收益除外
        val incomeMatcher = Pattern.compile("(?:成功收款|收钱码收款|收到.*转账|转入到账)[^0-9]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?").matcher(text)
        if (incomeMatcher.find()) {
            val amount = incomeMatcher.group(1)?.toDoubleOrNull() ?: return null
            if (amount > 0) {
                return ParsedBill(
                    type = "INCOME",
                    amount = amount,
                    channel = "支付宝",
                    merchant = extractMerchant(text, "支付宝收款"),
                    rawText = text
                )
            }
        }

        // 支付宝支出：成功付款26.50元 / 支出19.90元 / 付款成功...￥32.00
        val expenseMatcher = Pattern.compile("(?:成功付款|付款成功|支出|消费|向.*付款|自动扣款)[^0-9¥￥]*[¥￥]?\s*([0-9]+(?:\.[0-9]{1,2})?)\s*元?").matcher(text)
        if (expenseMatcher.find()) {
            val amount = expenseMatcher.group(1)?.toDoubleOrNull() ?: return null
            if (amount > 0) {
                return ParsedBill(
                    type = "EXPENSE",
                    amount = amount,
                    channel = "支付宝",
                    merchant = extractMerchant(text, "支付宝商户"),
                    rawText = text
                )
            }
        }
        return null
    }

    private fun parseBankSms(text: String): ParsedBill? {
        // 过滤验证码短信
        if (text.contains("验证码") || text.contains("校验码") || text.contains("动态密码")) {
            return null
        }
        // 必须包含银行常见标识或尾号
        val isBankMsg = text.contains("银行") || text.contains("尾号") || text.contains("账户") || text.contains("信用卡")
        if (!isBankMsg) return null

        // 银行支出：消费/支出/扣款/转出/支取/快捷支付 RMB128.50元
        val expenseMatcher = Pattern.compile("(?:支出|消费|扣款|转出|支取|付款|快捷支付)(?:人民币|RMB|￥|¥)?\s*([0-9]+(?:\.[0-9]{1,2})?)\s*元?").matcher(text)
        if (expenseMatcher.find()) {
            val amount = expenseMatcher.group(1)?.toDoubleOrNull() ?: return null
            if (amount > 0) {
                return ParsedBill(
                    type = "EXPENSE",
                    amount = amount,
                    channel = "银行短信",
                    merchant = extractBankName(text),
                    rawText = text
                )
            }
        }

        // 银行收入：收入/入账/转入/存入/工资 RMB5000.00元
        val incomeMatcher = Pattern.compile("(?:收入|入账|转入|存入|到账|代发工资)(?:人民币|RMB|￥|¥)?\s*([0-9]+(?:\.[0-9]{1,2})?)\s*元?").matcher(text)
        if (incomeMatcher.find()) {
            val amount = incomeMatcher.group(1)?.toDoubleOrNull() ?: return null
            if (amount > 0) {
                return ParsedBill(
                    type = "INCOME",
                    amount = amount,
                    channel = "银行短信",
                    merchant = extractBankName(text),
                    rawText = text
                )
            }
        }
        return null
    }

    private fun extractMerchant(text: String, fallback: String): String {
        val m1 = Pattern.compile("(?:向|在|给|商户[:：]|收款方[:：])\s*([^\s，,。：:0-9￥¥]{2,16})").matcher(text)
        if (m1.find()) {
            return m1.group(1) ?: fallback
        }
        return fallback
    }

    private fun extractBankName(text: String): String {
        val m = Pattern.compile("【([^】]{2,10}银行[^】]*)】").matcher(text)
        if (m.find()) {
            return m.group(1) ?: "银行卡动账"
        }
        return "银行卡动账"
    }
}

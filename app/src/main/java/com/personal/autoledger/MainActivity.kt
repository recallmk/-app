package com.personal.autoledger

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var summaryText: TextView
    private lateinit var permissionStatusText: TextView
    private lateinit var listContainer: LinearLayout
    private var currentKeyword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        // 1. 顶部标题与收支总览卡片
        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(48, 56, 48, 44)
        }

        val appTitle = TextView(this).apply {
            text = "随手纯本地记账 (100% 离线)"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        headerBox.addView(appTitle)

        summaryText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 16, 0, 8)
        }
        headerBox.addView(summaryText)

        permissionStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
        }
        headerBox.addView(permissionStatusText)
        root.addView(headerBox)

        // 2. 权限开启与功能操作栏
        val actionRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 4)
        }

        val btnSmsPerm = Button(this).apply {
            text = "1.短信权限"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { requestSmsPermissions() }
        }
        val btnNotifPerm = Button(this).apply {
            text = "2.通知监听"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }
        val btnAccessPerm = Button(this).apply {
            text = "3.无障碍识别"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        actionRow1.addView(btnSmsPerm)
        actionRow1.addView(btnNotifPerm)
        actionRow1.addView(btnAccessPerm)
        root.addView(actionRow1)

        val actionRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 4, 24, 12)
        }
        val btnManualAdd = Button(this).apply {
            text = "+ 手动记一笔"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, QuickRecordActivity::class.java))
            }
        }
        val btnSimulate = Button(this).apply {
            text = "模拟微信/短信入账"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showSimulationDialog() }
        }
        val btnExport = Button(this).apply {
            text = "导出本地CSV"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { exportCsvFile() }
        }
        actionRow2.addView(btnManualAdd)
        actionRow2.addView(btnSimulate)
        actionRow2.addView(btnExport)
        root.addView(actionRow2)

        // 3. 搜索过滤框（按“用来干什么”或分类检索）
        val searchInput = EditText(this).apply {
            hint = "搜索资金用途、分类或商户（如：火锅、打车、微信）..."
            textSize = 14f
            setPadding(36, 24, 36, 24)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentKeyword = s?.toString()?.trim() ?: ""
                    refreshBillList()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchInput)

        // 4. 账单滚动列表
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 64)
        }
        scrollView.addView(listContainer)
        root.addView(scrollView)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        refreshBillList()
    }

    private fun updatePermissionStatus() {
        val hasSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val hasNotif = enabledListeners.contains(packageName)
        val enabledAccess = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val hasAccess = enabledAccess.contains(packageName)

        permissionStatusText.text =
            "监听状态：短信[${if (hasSms) "已开启" else "未授权"}]  |  " +
            "微信/支付宝通知[${if (hasNotif) "已开启" else "未开启"}]  |  " +
            "支付页无障碍[${if (hasAccess) "已开启" else "未开启"}]"
    }

    private fun refreshBillList() {
        listContainer.removeAllViews()
        val bills = db.getAllBills(currentKeyword)

        var totalExpense = 0.0
        var totalIncome = 0.0
        for (b in bills) {
            if (b.type == "INCOME") totalIncome += b.amount else totalExpense += b.amount
        }

        summaryText.text = String.format(
            Locale.CHINA,
            "总支出：¥%.2f   |   总收入：¥%.2f   |   净结余：¥%.2f （共 %d 笔）",
            totalExpense,
            totalIncome,
            totalIncome - totalExpense,
            bills.size
        )

        if (bills.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "暂无账单记录。\n你可以点击上方「模拟微信/短信入账」测试自动弹窗，或点击「+ 手动记一笔」。"
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(24, 96, 24, 96)
            }
            listContainer.addView(emptyView)
            return
        }

        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        for (bill in bills) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(36, 28, 36, 28)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, 24)
                layoutParams = lp

                // 点击卡片可随时修改“用来干什么”
                setOnClickListener {
                    val editIntent = Intent(this@MainActivity, QuickRecordActivity::class.java).apply {
                        putExtra("record_id", bill.id)
                        putExtra("type", bill.type)
                        putExtra("amount", bill.amount)
                        putExtra("channel", bill.channel)
                        putExtra("merchant", bill.merchant)
                        putExtra("purpose", bill.purpose)
                        putExtra("category", bill.category)
                    }
                    startActivity(editIntent)
                }

                // 长按删除该笔账单
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("删除账单")
                        .setMessage("确定删除「${bill.purpose} (¥${bill.amount})」吗？")
                        .setPositiveButton("删除") { _, _ ->
                            db.deleteBill(bill.id)
                            refreshBillList()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val purposeView = TextView(this).apply {
                text = "[${bill.category}] ${bill.purpose}"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(
                    if (bill.purpose.startsWith("待补充用途")) Color.parseColor("#D97706")
                    else Color.parseColor("#0F172A")
                )
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val amountView = TextView(this).apply {
                val sign = if (bill.type == "INCOME") "+" else "-"
                text = "$sign¥${String.format(Locale.US, "%.2f", bill.amount)}"
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(
                    if (bill.type == "INCOME") Color.parseColor("#16A34A")
                    else Color.parseColor("#DC2626")
                )
            }

            topRow.addView(purposeView)
            topRow.addView(amountView)
            card.addView(topRow)

            val metaView = TextView(this).apply {
                text = "${sdf.format(Date(bill.timestamp))}  ·  ${bill.channel}  ·  ${bill.merchant}  （点击修改用途 / 长按删除）"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 12, 0, 0)
            }
            card.addView(metaView)

            listContainer.addView(card)
        }
    }

    private fun requestSmsPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(perms.toTypedArray(), 101)
    }

    private fun showSimulationDialog() {
        val samples = arrayOf(
            "微信支付：向老乡鸡付款28.50元" to ("com.tencent.mm" to "微信支付凭证：向老乡鸡付款金额￥28.50元"),
            "支付宝：地铁出行扣款4.00元" to ("com.eg.android.AlipayGphone" to "支付宝通知：在城市轨道交通成功付款4.00元"),
            "银行短信：招商银行消费168.00元" to ("sms" to "【招商银行】您尾号8888账户11月10日快捷支付消费人民币168.00元"),
            "微信收款：个人收款码到账66.00元" to ("com.tencent.mm" to "微信支付收款66.00元(朋友聚餐AA)")
        )

        val titles = samples.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择要模拟测试的账单消息")
            .setItems(titles) { _, which ->
                val (pkg, raw) = samples[which].second
                val parsed = BillParser.parse(pkg, "模拟通知", raw)
                if (parsed != null) {
                    QuickRecordLauncher.triggerQuickRecord(this, parsed)
                }
            }
            .show()
    }

    private fun exportCsvFile() {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            val file = db.exportToCsv(dir)
            AlertDialog.Builder(this)
                .setTitle("本地 CSV 导出成功")
                .setMessage("账单文件已保存在手机本地路径：\n${file.absolutePath}")
                .setPositiveButton("确定", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

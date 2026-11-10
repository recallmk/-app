package com.personal.autoledger

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class QuickRecordActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recordId = intent.getLongExtra("record_id", -1L)
        val initType = intent.getStringExtra("type") ?: "EXPENSE"
        val initAmount = intent.getDoubleExtra("amount", 0.0)
        val initChannel = intent.getStringExtra("channel") ?: "手动记账"
        val initMerchant = intent.getStringExtra("merchant") ?: "日常收支"
        val initPurpose = intent.getStringExtra("purpose") ?: ""
        val initCategory = intent.getStringExtra("category") ?: "餐饮"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 48, 56, 48)
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = if (recordId > 0) "捕获到新账单 · 记一笔用途" else "手动记一笔"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        }
        root.addView(titleView)

        val subTitleView = TextView(this).apply {
            text = "来源渠道：$initChannel  |  商户/对方：$initMerchant"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 12, 0, 24)
        }
        root.addView(subTitleView)

        // 收支类型切换
        val typeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val rbExpense = RadioButton(this).apply {
            id = 1001
            text = "支出 (-)"
            isChecked = (initType != "INCOME")
        }
        val rbIncome = RadioButton(this).apply {
            id = 1002
            text = "收入 (+)"
            isChecked = (initType == "INCOME")
        }
        typeGroup.addView(rbExpense)
        typeGroup.addView(rbIncome)
        root.addView(typeGroup)

        // 金额输入框
        val amountInput = EditText(this).apply {
            hint = "金额 (元)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (initAmount > 0) {
                setText(String.format(Locale.US, "%.2f", initAmount))
            }
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(amountInput)

        // 核心输入框：这笔钱用来干什么
        val purposeInput = EditText(this).apply {
            hint = "这笔钱用来干什么？（例如：和朋友吃火锅、打车回家、发工资）"
            if (initPurpose.isNotBlank() && !initPurpose.startsWith("待补充用途")) {
                setText(initPurpose)
            }
            textSize = 16f
        }
        root.addView(purposeInput)

        // 快捷分类与用途标签
        var selectedCategory = initCategory
        val categoryLabel = TextView(this).apply {
            text = "当前分类：$selectedCategory （点击下方标签快速填入）"
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 20, 0, 12)
        }
        root.addView(categoryLabel)

        val quickTags = listOf(
            "餐饮" to "吃早午晚餐",
            "买菜" to "超市买菜水果",
            "交通" to "地铁公交打车",
            "购物" to "日用百货网购",
            "居家" to "水电房租物业",
            "娱乐" to "电影会员休闲",
            "人情" to "红包请客送礼",
            "工资" to "工资奖金收入"
        )

        val tagScroll = HorizontalScrollView(this)
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for ((cat, samplePurpose) in quickTags) {
            val chip = Button(this).apply {
                text = cat
                textSize = 12f
                setOnClickListener {
                    selectedCategory = cat
                    categoryLabel.text = "当前分类：$selectedCategory"
                    if (purposeInput.text.toString().isBlank()) {
                        purposeInput.setText(samplePurpose)
                        purposeInput.setSelection(purposeInput.text.length)
                    }
                }
            }
            tagRow.addView(chip)
        }
        tagScroll.addView(tagRow)
        root.addView(tagScroll)

        // 底部保存与取消按钮
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 36, 0, 0)
        }

        val cancelBtn = Button(this).apply {
            text = "稍后再填"
            setOnClickListener { finish() }
        }

        val saveBtn = Button(this).apply {
            text = "保存用途"
            setOnClickListener {
                val amt = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (amt <= 0.0) {
                    Toast.makeText(this@QuickRecordActivity, "请输入有效金额", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val finalType = if (rbIncome.isChecked) "INCOME" else "EXPENSE"
                val finalPurpose = purposeInput.text.toString().trim().ifEmpty {
                    "$selectedCategory ($initMerchant)"
                }

                val db = DatabaseHelper(this@QuickRecordActivity)
                if (recordId > 0) {
                    db.updateBillPurpose(recordId, finalPurpose, selectedCategory)
                } else {
                    db.insertBill(
                        BillRecord(
                            type = finalType,
                            amount = amt,
                            channel = initChannel,
                            merchant = initMerchant,
                            purpose = finalPurpose,
                            category = selectedCategory,
                            timestamp = System.currentTimeMillis(),
                            rawText = "手动录入"
                        )
                    )
                }
                Toast.makeText(this@QuickRecordActivity, "已记账：$finalPurpose", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(saveBtn)
        root.addView(btnRow)

        setContentView(root)
        purposeInput.requestFocus()
    }
}

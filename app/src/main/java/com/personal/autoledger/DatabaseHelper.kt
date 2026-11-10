package com.personal.autoledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BillRecord(
    val id: Long = 0,
    val type: String,        // "EXPENSE" 或 "INCOME"
    val amount: Double,      // 金额
    val channel: String,     // "微信" | "支付宝" | "银行短信" | "手动记账"
    val merchant: String,    // 商户/来源
    val purpose: String,     // 这笔钱用来干什么（核心备注字段）
    val category: String,    // 分类标签（餐饮/交通/购物/居家/娱乐/工资/其他）
    val timestamp: Long,     // 毫秒时间戳
    val rawText: String      // 原始短信或通知内容
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "auto_ledger_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                channel TEXT NOT NULL,
                merchant TEXT NOT NULL,
                purpose TEXT NOT NULL,
                category TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                raw_text TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS bills")
        onCreate(db)
    }

    fun insertBill(bill: BillRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("type", bill.type)
            put("amount", bill.amount)
            put("channel", bill.channel)
            put("merchant", bill.merchant)
            put("purpose", bill.purpose)
            put("category", bill.category)
            put("timestamp", bill.timestamp)
            put("raw_text", bill.rawText)
        }
        return db.insert("bills", null, values)
    }

    fun updateBillPurpose(id: Long, purpose: String, category: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("purpose", purpose)
            put("category", category)
        }
        db.update("bills", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteBill(id: Long) {
        writableDatabase.delete("bills", "id = ?", arrayOf(id.toString()))
    }

    fun getAllBills(keyword: String = ""): List<BillRecord> {
        val list = mutableListOf<BillRecord>()
        val db = readableDatabase
        val selection = if (keyword.isBlank()) null else "purpose LIKE ? OR category LIKE ? OR merchant LIKE ? OR channel LIKE ?"
        val args = if (keyword.isBlank()) null else Array(4) { "%$keyword%" }

        val cursor = db.query("bills", null, selection, args, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BillRecord(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        type = it.getString(it.getColumnIndexOrThrow("type")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        channel = it.getString(it.getColumnIndexOrThrow("channel")),
                        merchant = it.getString(it.getColumnIndexOrThrow("merchant")),
                        purpose = it.getString(it.getColumnIndexOrThrow("purpose")),
                        category = it.getString(it.getColumnIndexOrThrow("category")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        rawText = it.getString(it.getColumnIndexOrThrow("raw_text")) ?: ""
                    )
                )
            }
        }
        return list
    }

    /**
     * 导出所有账单到本地 CSV 文件（UTF-8 with BOM，Excel 直接打开不乱码）
     */
    fun exportToCsv(exportDir: File): File {
        val bills = getAllBills()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val fileName = "随手记账导出_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.csv"
        val outFile = File(exportDir, fileName)

        FileOutputStream(outFile).use { fos ->
            // 写入 UTF-8 BOM 防止 Excel 中文乱码
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val header = "时间,收支类型,金额(元),资金用途(用来干什么),分类,支付渠道,商户/对方,原始凭证\n"
            fos.write(header.toByteArray(Charsets.UTF_8))

            for (b in bills) {
                val timeStr = sdf.format(Date(b.timestamp))
                val typeStr = if (b.type == "INCOME") "收入" else "支出"
                val safePurpose = b.purpose.replace(",", "，").replace("\n", " ")
                val safeMerchant = b.merchant.replace(",", "，").replace("\n", " ")
                val safeRaw = b.rawText.replace(",", "，").replace("\n", " ")
                val row = "$timeStr,$typeStr,${String.format(Locale.US, "%.2f", b.amount)},$safePurpose,${b.category},${b.channel},$safeMerchant,$safeRaw\n"
                fos.write(row.toByteArray(Charsets.UTF_8))
            }
        }
        return outFile
    }
}

package com.example.sa_app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Models ----------

data class Session(
    val userId: Long?,          // เจ้าของร้าน (User)
    val employeePk: Long?,      // พนักงาน (Employee.id_employee)
    val name: String,
    val isOwner: Boolean,
    val businessId: Long,
    val businessName: String,
    val branchId: Long,
    val branchCode: String,
    val branchName: String,
    val actorCode: String       // Employee.employee_id หรือ "U"+id_user
)

data class Business(val id: Long, val userId: Long, val name: String, val logo: String?)
data class Branch(val id: Long, val businessId: Long, val code: String, val name: String, val location: String?)

data class Employee(
    val id: Long = 0, val branchId: Long, val code: String, val firstname: String, val lastname: String,
    val position: String?, val salary: Double?, val image: String?, val loginId: String,
    val branchName: String = ""
)

data class Product(
    val id: Long = 0, val businessId: Long, val sku: String, val barcode: String?, val name: String,
    val type: String?, val price: Double, val details: String?, val image: String?, val typeSell: String?
)

data class Equipment(
    val id: Long = 0, val businessId: Long, val sku: String, val name: String,
    val type: String?, val details: String?, val image: String?
)

enum class Kind(
    val label: String, val store: String, val storePk: String,
    val hist: String, val histPk: String, val itemCol: String, val itemTable: String
) {
    PRODUCT("สินค้า", "Store_Product", "id_store_product", "History_Store_Product",
        "id_history_store_product", "id_product", "Product"),
    EQUIPMENT("อุปกรณ์", "Store_Equipment", "id_store_equipment", "History_Store_Equipment",
        "id_history_store_equipment", "id_equipment", "Equipment")
}

data class StockSummary(
    val itemId: Long, val name: String, val sku: String, val type: String?,
    val qty: Double, val nearestExp: String?, val price: Double
)

data class Lot(val id: Long, val qty: Double, val mfg: String?, val exp: String?, val location: String?)

data class StockHistory(
    val id: String, val type: String?, val before: Double, val after: Double,
    val by: String, val details: String?, val date: String
)

data class Promotion(
    val id: Long = 0, val businessId: Long, val name: String, val discountType: String,
    val value: Double, val start: String?, val end: String?, val status: String
) {
    fun discountFor(subtotal: Double): Double = when (discountType) {
        "percent" -> (subtotal * value / 100.0).coerceAtMost(subtotal)
        else -> value.coerceAtMost(subtotal)
    }.coerceAtLeast(0.0)

    val label: String
        get() = if (discountType == "percent") "ลด ${qtyFmt(value)}%" else "ลด ${money(value)}"
}

data class CartLine(val product: Product, val qty: Double)

data class Bill(
    val id: String, val total: Double, val discount: Double, val typePay: String?,
    val date: String, val by: String, val promoName: String?, val items: Int
)

data class BillLine(val name: String, val volume: Double, val price: Double)

data class ChatBox(val id: Long, val date: String, val preview: String)
data class ChatMsg(
    val id: Long, val role: String, val content: String, val model: String?,
    val date: String, val tools: List<String> = emptyList()
)

data class Dashboard(
    val todaySales: Double, val todayBills: Int, val monthSales: Double,
    val lowStock: List<StockSummary>, val nearExpiry: List<StockSummary>,
    val last7: List<Pair<String, Double>>, val top: List<Pair<String, Double>>
)

const val LOW_STOCK = 5.0

val PAY_TYPES = listOf("cash" to "เงินสด", "transfer" to "โอนเงิน", "qr" to "QR พร้อมเพย์", "card" to "บัตร")
fun payLabel(code: String?) = PAY_TYPES.firstOrNull { it.first == code }?.second ?: (code ?: "-")

// ---------- Utils ----------

object Time {
    private fun f(p: String) = SimpleDateFormat(p, Locale.US) // Locale.US กันปฏิทินพุทธศักราช
    fun now(): String = f("yyyy-MM-dd HH:mm:ss").format(Date())
    fun today(): String = f("yyyy-MM-dd").format(Date())
    fun stamp(): String = f("yyyyMMddHHmmss").format(Date())
    fun dayOffset(days: Int): String {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, days)
        return f("yyyy-MM-dd").format(c.time)
    }
    fun isDate(s: String) = Regex("""\d{4}-\d{2}-\d{2}""").matches(s)
    fun short(dt: String?): String = when {
        dt == null -> "-"
        dt.length >= 16 -> "${dt.substring(8, 10)}/${dt.substring(5, 7)} ${dt.substring(11, 16)}"
        dt.length >= 10 -> "${dt.substring(8, 10)}/${dt.substring(5, 7)}/${dt.substring(0, 4)}"
        else -> dt
    }
}

/** รหัสประสม: YYYYMMDDHHMMSS-รหัสสาขา-รหัสพนักงาน-สุ่ม4ตัว */
object Ids {
    private val rnd = SecureRandom()
    private const val CH = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun clean(s: String) = s.filter { it.code < 128 && it.isLetterOrDigit() }.take(20).ifEmpty { "X" }
    fun new(branchCode: String, actor: String): String =
        "${Time.stamp()}-${clean(branchCode)}-${clean(actor)}-" +
            (1..4).map { CH[rnd.nextInt(CH.length)] }.joinToString("")
}

object Pw {
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun hash(pw: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        return "sha256\$$salt\$${sha(salt + pw)}"
    }

    fun verify(pw: String, stored: String): Boolean {
        val p = stored.split("$")
        return p.size == 3 && sha(p[1] + pw) == p[2]
    }
}

fun money(v: Double): String = "฿" + String.format(Locale.US, "%,.2f", v)
fun qtyFmt(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

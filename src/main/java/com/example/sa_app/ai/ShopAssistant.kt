package com.example.sa_app.ai

import com.example.sa_app.data.Kind
import com.example.sa_app.data.LOW_STOCK
import com.example.sa_app.data.Repo
import com.example.sa_app.data.Session
import com.example.sa_app.data.Time
import com.example.sa_app.data.money
import com.example.sa_app.data.qtyFmt
import org.json.JSONArray
import org.json.JSONObject

/**
 * ผู้ช่วย AI แบบทำงานในเครื่อง (offline) — เลือก "เครื่องมือ" จากคำถาม
 * แล้วดึงข้อมูลจริงจากฐานข้อมูล ทุกครั้งที่เรียกเครื่องมือจะบันทึกลง Ai_Active
 *
 * ต้องการใช้ LLM จริง (เช่น Claude ผ่านเซิร์ฟเวอร์ของคุณ): ให้เซิร์ฟเวอร์เรียกเครื่องมือชุดเดียวกัน
 * (ชื่อ tool + arguments JSON) แล้วบันทึกผลแบบเดียวกัน — โครงสร้างตารางรองรับอยู่แล้ว
 */
class ShopAssistant(private val repo: Repo) {

    val model = "local-tools-v1"

    private data class Call(val tool: String, val args: JSONObject)
    private data class Out(val text: String, val result: JSONObject, val targetTable: String?)

    fun ask(s: Session, boxId: Long, question: String) {
        repo.addMessage(boxId, "user", question, null)
        val calls = plan(question)
        val outputs = calls.map { c -> c to runCatching { run(s, c) } }
        val reply = if (outputs.isEmpty()) help()
        else outputs.joinToString("\n\n") { (c, r) ->
            r.fold({ it.text }, { "ใช้เครื่องมือ ${c.tool} ไม่สำเร็จ: ${it.message}" })
        }
        val msgId = repo.addMessage(boxId, "assistant", reply, model)
        outputs.forEach { (c, r) ->
            repo.addAiActive(msgId, c.tool, c.args, r.getOrNull()?.result, r.exceptionOrNull()?.message,
                null, r.getOrNull()?.targetTable)
        }
    }

    // ---------- เลือกเครื่องมือจากคำถาม ----------
    private fun plan(text: String): List<Call> {
        val t = text.lowercase()
        val calls = mutableListOf<Call>()
        val days = when {
            listOf("เดือน", "30").any { it in t } -> 30
            listOf("สัปดาห์", "อาทิตย์", "7 วัน", "week").any { it in t } -> 7
            listOf("เมื่อวาน").any { it in t } -> 2
            else -> 1
        }
        if (listOf("ขายดี", "ยอดนิยม", "top", "best").any { it in t })
            calls += Call("top_products", JSONObject().put("days", maxOf(days, 7)))
        else if (listOf("ยอดขาย", "ขายได้", "รายได้", "sales", "สรุป").any { it in t })
            calls += Call("sales_summary", JSONObject().put("days", days))
        val lowAsked = listOf("หมดสต็อก", "สต็อกต่ำ", "ต้องเติม", "ควรเติม", "ต้องสั่ง", "low stock").any { it in t } ||
            ("ใกล้หมด" in t && "หมดอายุ" !in t)
        if (lowAsked)
            calls += Call("low_stock", JSONObject().put("threshold", LOW_STOCK))
        if (listOf("หมดอายุ", "expire", "exp").any { it in t })
            calls += Call("near_expiry", JSONObject().put("days", 30))
        if (listOf("โปร", "ส่วนลด", "promotion").any { it in t })
            calls += Call("active_promotions", JSONObject())
        if (calls.isEmpty() && listOf("ราคา", "คงเหลือ", "เหลือ", "มีไหม", "หา", "ค้นหา", "price", "stock").any { it in t }) {
            val kw = extractKeyword(text)
            if (kw.isNotBlank()) calls += Call("find_product", JSONObject().put("keyword", kw))
        }
        return calls
    }

    private fun extractKeyword(text: String): String {
        var k = text
        listOf("ค้นหา", "ราคา", "คงเหลือ", "เหลือ", "สินค้า", "มีไหม", "เท่าไหร่", "เท่าไร", "กี่ชิ้น", "กี่",
            "ชิ้น", "หา", "ของ", "ครับ", "ค่ะ", "คะ", "ไหม", "?", "price", "stock")
            .forEach { k = k.replace(it, " ", ignoreCase = true) }
        return k.trim().split(Regex("\\s+")).maxByOrNull { it.length } ?: ""
    }

    // ---------- เครื่องมือ ----------
    private fun run(s: Session, c: Call): Out = when (c.tool) {
        "sales_summary" -> {
            val days = c.args.getInt("days")
            val (total, bills) = repo.salesSince(s.branchId, Time.dayOffset(-days + 1))
            val label = if (days == 1) "วันนี้" else "$days วันล่าสุด"
            val avg = if (bills > 0) total / bills else 0.0
            Out("ยอดขาย$label (${s.branchName}): ${money(total)} จาก $bills บิล\nเฉลี่ยบิลละ ${money(avg)}",
                JSONObject().put("total", total).put("bills", bills).put("avg", avg), "Bill")
        }
        "top_products" -> {
            val days = c.args.getInt("days")
            val top = repo.topProducts(s.branchId, days)
            val text = if (top.isEmpty()) "ยังไม่มีการขายใน $days วันล่าสุด"
            else "สินค้าขายดี $days วันล่าสุด:\n" + top.mapIndexed { i, (n, v) -> "${i + 1}. $n — ${qtyFmt(v)} ชิ้น" }.joinToString("\n")
            Out(text, JSONObject().put("items", JSONArray(top.map { JSONObject().put("name", it.first).put("qty", it.second) })), "Basket")
        }
        "low_stock" -> {
            val th = c.args.getDouble("threshold")
            val low = repo.stock(Kind.PRODUCT, s).filter { it.qty <= th }.sortedBy { it.qty }
            val text = if (low.isEmpty()) "สต็อกทุกรายการมากกว่า ${qtyFmt(th)} ชิ้น ยังไม่ต้องเติม"
            else "สินค้าที่ควรเติม (${low.size} รายการ):\n" + low.take(15).joinToString("\n") { "• ${it.name} เหลือ ${qtyFmt(it.qty)}" }
            Out(text, JSONObject().put("count", low.size), "Store_Product")
        }
        "near_expiry" -> {
            val days = c.args.getInt("days")
            val limit = Time.dayOffset(days)
            val list = repo.stock(Kind.PRODUCT, s).filter { it.nearestExp != null && it.nearestExp <= limit }
                .sortedBy { it.nearestExp }
            val text = if (list.isEmpty()) "ไม่มีสินค้าที่หมดอายุภายใน $days วัน"
            else "สินค้าใกล้หมดอายุภายใน $days วัน:\n" + list.take(15).joinToString("\n") {
                "• ${it.name} หมดอายุ ${Time.short(it.nearestExp)} (คงเหลือ ${qtyFmt(it.qty)})"
            }
            Out(text, JSONObject().put("count", list.size), "Store_Product")
        }
        "active_promotions" -> {
            val list = repo.activePromotions(s.businessId)
            val text = if (list.isEmpty()) "ตอนนี้ไม่มีโปรโมชันที่ใช้งานอยู่"
            else "โปรโมชันที่ใช้ได้ตอนนี้:\n" + list.joinToString("\n") { "• ${it.name} (${it.label})" }
            Out(text, JSONObject().put("count", list.size), "Promotion")
        }
        "find_product" -> {
            val kw = c.args.getString("keyword")
            val list = repo.stock(Kind.PRODUCT, s, kw)
            val text = if (list.isEmpty()) "ไม่พบสินค้าที่ตรงกับ \"$kw\""
            else list.take(10).joinToString("\n") { "• ${it.name} (${it.sku}) ราคา ${money(it.price)} คงเหลือ ${qtyFmt(it.qty)}" }
            Out(text, JSONObject().put("count", list.size), "Product")
        }
        else -> error("ไม่รู้จักเครื่องมือ ${c.tool}")
    }

    private fun help() = """ถามข้อมูลร้านได้เลย เช่น
• ยอดขายวันนี้ / ยอดขาย 7 วัน / ยอดขายเดือนนี้
• สินค้าขายดีสัปดาห์นี้
• สินค้าใกล้หมดสต็อก
• สินค้าใกล้หมดอายุ
• โปรโมชันที่ใช้ได้
• ราคาและคงเหลือของ (ชื่อสินค้า)"""

    companion object {
        val SUGGESTIONS = listOf("ยอดขายวันนี้", "สินค้าขายดีสัปดาห์นี้", "สินค้าใกล้หมดสต็อก",
            "สินค้าใกล้หมดอายุ", "โปรโมชันที่ใช้ได้", "ยอดขายเดือนนี้")
    }
}

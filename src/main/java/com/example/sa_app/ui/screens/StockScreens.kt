@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sa_app.data.*
import com.example.sa_app.ui.*

@Composable
fun StockScreen(c: Ctx) {
    var kind by remember { mutableStateOf(Kind.PRODUCT) }
    var search by remember { mutableStateOf("") }
    var onlyLow by remember { mutableStateOf(false) }
    val list by load(kind, search, c.session.branchId) { c.repo.stock(kind, c.session, search) }

    ScreenFrame("คลังสินค้า", c.session.branchName) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(Kind.entries.map { it to it.label } , kind, { kind = it })
                Field("ค้นหา", search, { search = it }, leading = Icons.Filled.Search)
                Row {
                    FilterChip(selected = onlyLow, onClick = { onlyLow = !onlyLow },
                        label = { Text("เฉพาะเหลือน้อย (≤ ${qtyFmt(LOW_STOCK)})") })
                }
            }
            val rows = list.orEmpty().filter { !onlyLow || it.qty <= LOW_STOCK }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (list != null && rows.isEmpty()) item {
                    Empty("ยังไม่มี${kind.label}ในรายการ", if (c.session.isOwner) "เพิ่ม${kind.label}" else null) {
                        c.go(if (kind == Kind.PRODUCT) Dest.ProductEdit(0) else Dest.EquipmentEdit(0))
                    }
                }
                items(rows, key = { it.itemId }) { s ->
                    RowItem(
                        s.name,
                        listOfNotNull(s.sku, s.type, s.nearestExp?.let { "หมดอายุ ${Time.short(it)}" }).joinToString(" • "),
                        leading = { Initials(s.name) },
                        trailing = { Pill(qtyFmt(s.qty), warn = s.qty <= LOW_STOCK) }
                    ) { c.go(Dest.StockDetail(kind, s.itemId)) }
                }
            }
        }
    }
}

@Composable
fun StockDetailScreen(c: Ctx, kind: Kind, itemId: Long) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val summary by load(tick) { c.repo.stock(kind, c.session).firstOrNull { it.itemId == itemId } }
    val lots by load(tick) { c.repo.lots(kind, c.session.branchId, itemId) }
    val history by load(tick) { c.repo.stockHistory(kind, c.session.branchId, itemId) }
    var action by remember { mutableStateOf<String?>(null) }

    ScreenFrame(summary?.name ?: kind.label, "${kind.label} • ${c.session.branchName}", onBack = c.back, actions = {
        if (c.session.isOwner) IconButton(onClick = {
            c.go(if (kind == Kind.PRODUCT) Dest.ProductEdit(itemId) else Dest.EquipmentEdit(itemId))
        }) { Icon(Icons.Filled.Edit, "แก้ไข") }
    }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("คงเหลือทั้งหมด", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(qtyFmt(summary?.qty ?: 0.0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        color = if ((summary?.qty ?: 0.0) <= LOW_STOCK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { action = "in" }, Modifier.weight(1f)) { Text("รับเข้า") }
                        FilledTonalButton(onClick = { action = "out" }, Modifier.weight(1f)) { Text("เบิกออก") }
                        OutlinedButton(onClick = { action = "adjust" }, Modifier.weight(1f)) { Text("ปรับยอด") }
                    }
                }
            }
            item {
                Section("ล็อตในคลัง") {
                    if (lots.orEmpty().isEmpty()) RowItem("ยังไม่มีของในคลัง", "กด \"รับเข้า\" เพื่อเพิ่มล็อตแรก")
                    lots.orEmpty().forEach { l ->
                        RowItem(
                            "ล็อต #${l.id}" + (l.location?.let { " • $it" } ?: ""),
                            listOfNotNull(l.mfg?.let { "ผลิต ${Time.short(it)}" }, l.exp?.let { "หมดอายุ ${Time.short(it)}" })
                                .joinToString(" • ").ifEmpty { "ไม่ระบุวันที่" },
                            trailing = { Pill(qtyFmt(l.qty), warn = l.qty <= 0) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Section("ประวัติการเคลื่อนไหว") {
                    if (history.orEmpty().isEmpty()) RowItem("ยังไม่มีประวัติ")
                    history.orEmpty().forEach { h ->
                        val label = when (h.type) { "in" -> "รับเข้า"; "out" -> "ออก"; "adjust" -> "ปรับยอด"; else -> h.type ?: "-" }
                        RowItem(
                            "$label ${qtyFmt(h.before)} → ${qtyFmt(h.after)}",
                            "${Time.short(h.date)} • ${h.by}" + (h.details?.let { "\n$it" } ?: "")
                        )
                    }
                }
            }
        }
    }

    action?.let { type ->
        MoveDialog(type, kind, lots.orEmpty(), onDismiss = { action = null }) { qty, lotId, mfg, exp, loc, note ->
            scope.io({ c.repo.moveStock(c.session, kind, itemId, type, qty, lotId, mfg, exp, loc, note) },
                onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) {
                action = null; tick++; c.toast("บันทึกแล้ว")
            }
        }
    }
}

@Composable
private fun MoveDialog(
    type: String, kind: Kind, lots: List<Lot>, onDismiss: () -> Unit,
    onSave: (Double, Long?, String?, String?, String?, String?) -> Unit
) {
    var qty by remember { mutableStateOf("") }
    var lotId by remember { mutableLongStateOf(if (type == "adjust") lots.firstOrNull()?.id ?: 0L else 0L) }
    var mfg by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val title = when (type) { "in" -> "รับ${kind.label}เข้า"; "out" -> "เบิก${kind.label}ออก"; else -> "ปรับยอดคงเหลือ" }
    val newLot = type == "in" || (type == "adjust" && lotId == 0L)

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (type != "in" && lots.isNotEmpty()) {
                    Text("เลือกล็อต", style = MaterialTheme.typography.labelLarge)
                    val first = if (type == "out") 0L to "อัตโนมัติ (หมดอายุก่อนออกก่อน)" else 0L to "ล็อตใหม่"
                    ChipRow(listOf(first) + lots.map { it.id to "#${it.id} (${qtyFmt(it.qty)})" }, lotId, { lotId = it })
                }
                Field(if (type == "adjust") "ยอดที่นับได้จริง" else "จำนวน", qty,
                    { qty = it.filter { ch -> ch.isDigit() || ch == '.' } }, number = true)
                if (newLot) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("วันผลิต", mfg, { mfg = it }, Modifier.weight(1f), placeholder = "2026-11-01")
                        Field("วันหมดอายุ", exp, { exp = it }, Modifier.weight(1f), placeholder = "2027-05-01")
                    }
                    Field("ตำแหน่งจัดเก็บ", loc, { loc = it }, placeholder = "เช่น ชั้น A2")
                }
                Field("หมายเหตุ", note, { note = it })
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toDoubleOrNull()
                when {
                    q == null || (q <= 0 && type != "adjust") -> err = "กรอกจำนวนให้ถูกต้อง"
                    (mfg.isNotBlank() && !Time.isDate(mfg)) || (exp.isNotBlank() && !Time.isDate(exp)) ->
                        err = "วันที่ต้องเป็นรูปแบบ ปปปป-ดด-วว (ค.ศ.)"
                    else -> onSave(q ?: 0.0, lotId.takeIf { it != 0L }, mfg.ifBlank { null }, exp.ifBlank { null },
                        loc.ifBlank { null }, note.ifBlank { null })
                }
            }) { Text("บันทึก") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

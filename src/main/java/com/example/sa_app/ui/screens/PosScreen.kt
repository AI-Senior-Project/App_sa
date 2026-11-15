@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sa_app.data.*
import com.example.sa_app.ui.*

@Composable
fun PosScreen(c: Ctx, cart: SnapshotStateList<CartLine>) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var showCart by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var receipt by remember { mutableStateOf<Pair<String, Double>?>(null) }

    val products by load(search, tick) { c.repo.products(c.session.businessId, search) }
    val types by load(tick) { c.repo.productTypes(c.session.businessId) }
    val qtyMap by load(tick) { c.repo.productQtyMap(c.session.branchId) }
    val promos by load(tick) { c.repo.activePromotions(c.session.businessId) }

    fun add(p: Product, delta: Double = 1.0) {
        val i = cart.indexOfFirst { it.product.id == p.id }
        if (i < 0) { if (delta > 0) cart.add(CartLine(p, delta)) }
        else {
            val q = cart[i].qty + delta
            if (q <= 0) cart.removeAt(i) else cart[i] = cart[i].copy(qty = q)
        }
    }

    val subtotal = cart.sumOf { it.product.price * it.qty }
    val count = cart.sumOf { it.qty }

    if (showCart) {
        CartView(c, cart, promos.orEmpty(), subtotal, onBack = { showCart = false }, onAdd = ::add) { promo, pay ->
            scope.io({ c.repo.checkout(c.session, cart.toList(), promo, pay) }, onError = { c.toast(it.message ?: "ชำระเงินไม่สำเร็จ") }) {
                receipt = it; cart.clear(); showCart = false; tick++
            }
        }
    } else ScreenFrame("ขายสินค้า", c.session.branchName) {
        Column(Modifier.fillMaxSize()) {
            Field("ค้นหาชื่อ / SKU / บาร์โค้ด", search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leading = Icons.Filled.Search)
            val typeOptions = listOf("" to "ทั้งหมด") + types.orEmpty().map { it to it }
            if (typeOptions.size > 1) ChipRow(typeOptions, type, { type = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            else Spacer(Modifier.height(8.dp))

            val list = products.orEmpty().filter { type.isEmpty() || it.type == type }
            Box(Modifier.weight(1f)) {
                if (products != null && list.isEmpty())
                    Empty(if (search.isBlank()) "ยังไม่มีสินค้า" else "ไม่พบ \"$search\"",
                        if (c.session.isOwner && search.isBlank()) "เพิ่มสินค้า" else null) { c.go(Dest.ProductEdit(0)) }
                LazyVerticalGrid(
                    GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { p ->
                        val inCart = cart.firstOrNull { it.product.id == p.id }?.qty ?: 0.0
                        ProductTile(p, qtyMap?.get(p.id) ?: 0.0, inCart) { add(p) }
                    }
                }
            }
            if (cart.isNotEmpty()) CheckoutBar("${qtyFmt(count)} ชิ้น • ดูตะกร้า", money(subtotal)) { showCart = true }
        }
    }

    receipt?.let { (id, total) ->
        AlertDialog(
            onDismissRequest = { receipt = null },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("ชำระเงินแล้ว ${money(total)}") },
            text = { Text("เลขที่บิล\n$id") },
            confirmButton = { TextButton(onClick = { receipt = null }) { Text("ขายรายการถัดไป") } },
            dismissButton = { TextButton(onClick = { receipt = null; c.go(Dest.BillDetail(id)) }) { Text("ดูบิล") } }
        )
    }
}

@Composable
private fun ProductTile(p: Product, stock: Double, inCart: Double, onClick: () -> Unit) {
    val selected = inCart > 0
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp).heightIn(min = 104.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (selected) Box(
                    Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) { Text(qtyFmt(inCart), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(8.dp))
            Text(money(p.price), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
            Text(
                "คงเหลือ ${qtyFmt(stock)}${p.typeSell?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = if (stock <= LOW_STOCK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** แถบชำระเงินสีหญ้าฝรั่น — จุดเด่นของหน้าขาย */
@Composable
private fun CheckoutBar(label: String, amount: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Saffron else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Medium)
        Text(amount, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CartView(
    c: Ctx, cart: SnapshotStateList<CartLine>, promos: List<Promotion>, subtotal: Double,
    onBack: () -> Unit, onAdd: (Product, Double) -> Unit, onPay: (Promotion?, String) -> Unit
) {
    var promoId by remember { mutableLongStateOf(0L) }
    var pay by remember { mutableStateOf("cash") }
    var received by remember { mutableStateOf("") }
    val promo = promos.firstOrNull { it.id == promoId }
    val discount = promo?.discountFor(subtotal) ?: 0.0
    val net = subtotal - discount
    val change = (received.toDoubleOrNull() ?: 0.0) - net

    ScreenFrame("ตะกร้า", "${cart.size} รายการ", onBack = onBack, actions = {
        IconButton(onClick = { cart.clear(); onBack() }) { Icon(Icons.Filled.Delete, "ล้างตะกร้า") }
    }) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(cart, key = { it.product.id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.product.name, fontWeight = FontWeight.Medium)
                            Text("${money(line.product.price)} × ${qtyFmt(line.qty)} = ${money(line.product.price * line.qty)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(onClick = { onAdd(line.product, -1.0) }) { Icon(Icons.Filled.KeyboardArrowDown, "ลด") }
                        Text(qtyFmt(line.qty), Modifier.widthIn(min = 32.dp), fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        FilledTonalIconButton(onClick = { onAdd(line.product, 1.0) }) { Icon(Icons.Filled.Add, "เพิ่ม") }
                    }
                }
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (promos.isNotEmpty()) {
                            Text("โปรโมชัน", style = MaterialTheme.typography.titleSmall)
                            ChipRow(listOf(0L to "ไม่ใช้") + promos.map { it.id to "${it.name} (${it.label})" }, promoId, { promoId = it })
                        }
                        Text("ชำระด้วย", style = MaterialTheme.typography.titleSmall)
                        ChipRow(PAY_TYPES, pay, { pay = it })
                        if (pay == "cash") {
                            Field("รับเงินมา", received, { received = it.filter { ch -> ch.isDigit() || ch == '.' } }, number = true)
                            if (received.isNotBlank()) Text(
                                if (change >= 0) "เงินทอน ${money(change)}" else "ยังขาด ${money(-change)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (change >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        HorizontalDivider()
                        Line("รวม", money(subtotal))
                        if (discount > 0) Line("ส่วนลด", "−${money(discount)}")
                    }
                }
            }
            val cashShort = pay == "cash" && received.isNotBlank() && change < 0
            CheckoutBar("ชำระเงิน", money(net), enabled = cart.isNotEmpty() && !cashShort) { onPay(promo, pay) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sa_app.data.Kind
import com.example.sa_app.data.Time
import com.example.sa_app.data.money
import com.example.sa_app.data.qtyFmt
import com.example.sa_app.ui.*

@Composable
fun HomeScreen(c: Ctx) {
    var tick by remember { mutableIntStateOf(0) }
    val data by load(tick, c.session.branchId) { c.repo.dashboard(c.session) }
    val s = c.session

    ScreenFrame(
        title = s.businessName, subtitle = "${s.branchName} (${s.branchCode}) • ${s.name}",
        actions = { IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, "รีเฟรช") } }
    ) {
        val d = data
        if (d == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            // ยอดขายวันนี้ — องค์ประกอบหลักของหน้า
            item {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(JadeDeep).padding(22.dp)
                ) {
                    Text("ยอดขายวันนี้", color = Color.White.copy(alpha = 0.8f))
                    Text(money(d.todaySales), color = Saffron, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text("${d.todayBills} บิล • เดือนนี้ ${money(d.monthSales)}", color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(18.dp))
                    WeekBars(d.last7)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("เปิดขาย", Icons.Filled.ShoppingCart, Modifier.weight(1f)) { c.go(Dest.Pos) }
                    QuickAction("รับของเข้า", Icons.AutoMirrored.Filled.List, Modifier.weight(1f)) { c.go(Dest.Stock) }
                    QuickAction("ถาม AI", Icons.Filled.Star, Modifier.weight(1f)) { c.go(Dest.Ai) }
                    if (s.isOwner) QuickAction("สินค้าใหม่", Icons.Filled.Add, Modifier.weight(1f)) { c.go(Dest.ProductEdit(0)) }
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                Section("ต้องเติมสต็อก (${d.lowStock.size})") {
                    if (d.lowStock.isEmpty()) RowItem("ทุกรายการยังมีของพอขาย")
                    d.lowStock.take(5).forEach {
                        RowItem(it.name, it.sku, trailing = { Pill(qtyFmt(it.qty), warn = true) }) {
                            c.go(Dest.StockDetail(Kind.PRODUCT, it.itemId))
                        }
                    }
                }
            }
            item {
                Section("ใกล้หมดอายุใน 30 วัน (${d.nearExpiry.size})") {
                    if (d.nearExpiry.isEmpty()) RowItem("ไม่มีสินค้าใกล้หมดอายุ")
                    d.nearExpiry.take(5).forEach {
                        RowItem(it.name, "คงเหลือ ${qtyFmt(it.qty)}",
                            trailing = { Pill(Time.short(it.nearestExp), warn = true) }) {
                            c.go(Dest.StockDetail(Kind.PRODUCT, it.itemId))
                        }
                    }
                }
            }
            item {
                Section("ขายดี 30 วัน") {
                    if (d.top.isEmpty()) RowItem("ยังไม่มีการขาย", "เริ่มขายที่แท็บ \"ขาย\"")
                    d.top.forEachIndexed { i, (name, v) ->
                        RowItem(name, leading = { Initials("${i + 1}") }, trailing = { Text("${qtyFmt(v)} ชิ้น") })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(days: kotlin.collections.List<Pair<String, Double>>) {
    val max = (days.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { i, (day, v) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth(0.7f).fillMaxHeight((v / max).toFloat().coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (i == days.lastIndex) Saffron else Color.White.copy(alpha = 0.35f))
                    )
                }
                Text("${day.substring(8, 10)}/${day.substring(5, 7)}", color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

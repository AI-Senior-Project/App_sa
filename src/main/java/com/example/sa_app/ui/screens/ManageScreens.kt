@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sa_app.data.*
import com.example.sa_app.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ======================= More =======================
@Composable
fun MoreScreen(c: Ctx) {
    val s = c.session
    var pickBranch by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val branches by load(pickBranch) { c.repo.branches(s.businessId) }
    val pending by load(Unit) { c.repo.pendingCount() }

    ScreenFrame("เพิ่มเติม") {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Section {
                    RowItem(s.name, if (s.isOwner) "เจ้าของร้าน • ${s.businessName}" else "พนักงาน (${s.actorCode}) • ${s.businessName}",
                        leading = { Initials(s.name) })
                    if (s.isOwner) MenuRow("สาขาที่ใช้งาน: ${s.branchName}", Icons.Filled.Place) { pickBranch = true }
                }
            }
            item {
                Section("ร้านค้า") {
                    MenuRow("ประวัติบิลขาย", Icons.Filled.DateRange) { c.go(Dest.Bills) }
                    MenuRow("สินค้า", Icons.Filled.ShoppingCart) { c.go(Dest.Products) }
                    MenuRow("อุปกรณ์", Icons.Filled.Build) { c.go(Dest.Equipments) }
                    if (s.isOwner) MenuRow("โปรโมชัน", Icons.Filled.Star) { c.go(Dest.Promotions) }
                }
            }
            if (s.isOwner) item {
                Section("จัดการธุรกิจ") {
                    MenuRow("สาขา", Icons.Filled.LocationOn) { c.go(Dest.Branches) }
                    MenuRow("พนักงาน", Icons.Filled.Person) { c.go(Dest.Employees) }
                    MenuRow("ข้อมูลธุรกิจ", Icons.Filled.Info) { c.go(Dest.BusinessInfo) }
                }
            }
            item {
                Section("ระบบ") {
                    MenuRow("ซิงค์ข้อมูลกับเซิร์ฟเวอร์", Icons.Filled.Refresh,
                        badge = pending?.takeIf { it > 0 }?.let { "รอส่ง $it" }) { c.go(Dest.Sync) }
                    MenuRow("ออกจากระบบ", Icons.AutoMirrored.Filled.ExitToApp) { confirmLogout = true }
                }
            }
        }
    }

    if (pickBranch) AlertDialog(
        onDismissRequest = { pickBranch = false }, title = { Text("เลือกสาขา") },
        text = {
            Column {
                branches.orEmpty().forEach { b ->
                    RowItem(b.name, b.code, trailing = { if (b.id == s.branchId) Icon(Icons.Filled.Check, null) }) {
                        c.setSession(c.repo.switchBranch(s, b)); pickBranch = false
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickBranch = false }) { Text("ปิด") } }
    )
    if (confirmLogout) ConfirmDialog("ออกจากระบบ?", "ข้อมูลยังอยู่ในเครื่อง เข้าสู่ระบบใหม่ได้ทุกเมื่อ", "ออกจากระบบ",
        onConfirm = c.logout, onDismiss = { confirmLogout = false })
}

@Composable
private fun MenuRow(title: String, icon: ImageVector, badge: String? = null, onClick: () -> Unit) {
    RowItem(title, leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, trailing = {
        Row {
            if (badge != null) { Pill(badge, warn = true); Spacer(Modifier.width(6.dp)) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }, onClick = onClick)
}

@Composable
private fun AddFab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(label) },
        containerColor = Saffron, contentColor = Ink)
}

// ======================= Products =======================
@Composable
fun ProductsScreen(c: Ctx) {
    var search by remember { mutableStateOf("") }
    val list by load(search) { c.repo.products(c.session.businessId, search) }
    ScreenFrame("สินค้า", "${list?.size ?: 0} รายการ", onBack = c.back,
        fab = { if (c.session.isOwner) AddFab("เพิ่มสินค้า") { c.go(Dest.ProductEdit(0)) } }) {
        Column {
            Field("ค้นหาชื่อ / SKU / บาร์โค้ด", search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leading = Icons.Filled.Search)
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list.orEmpty(), key = { it.id }) { p ->
                    RowItem(p.name, listOfNotNull(p.sku, p.type, p.barcode).joinToString(" • "),
                        leading = { Initials(p.name) },
                        trailing = { Text(money(p.price), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    ) { c.go(if (c.session.isOwner) Dest.ProductEdit(p.id) else Dest.StockDetail(Kind.PRODUCT, p.id)) }
                }
            }
        }
    }
}

@Composable
fun ProductEditScreen(c: Ctx, id: Long) {
    val scope = rememberCoroutineScope()
    val existing by load(id) { if (id == 0L) null else c.repo.product(id) }
    val history by load(id) { if (id == 0L) emptyList() else c.repo.productEditHistory(id) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("ชิ้น") }
    var details by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        existing?.let { p ->
            sku = p.sku; barcode = p.barcode ?: ""; name = p.name; type = p.type ?: ""
            price = qtyFmt(p.price).replace(",", ""); unit = p.typeSell ?: ""; details = p.details ?: ""; image = p.image ?: ""
            loaded = true
        }
    }

    ScreenFrame(if (id == 0L) "เพิ่มสินค้า" else "แก้ไขสินค้า", onBack = c.back, actions = {
        if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "ลบ") }
    }) {
        if (loaded) FormColumn {
            Field("ชื่อสินค้า *", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("SKU *", sku, { sku = it }, Modifier.weight(1f))
                Field("บาร์โค้ด", barcode, { barcode = it }, Modifier.weight(1f), number = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("ราคาขาย (บาท) *", price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.weight(1f), number = true)
                Field("หน่วยขาย", unit, { unit = it }, Modifier.weight(1f), placeholder = "ชิ้น / กก. / แก้ว")
            }
            Field("ประเภท", type, { type = it }, placeholder = "เช่น เครื่องดื่ม")
            Field("รายละเอียด", details, { details = it }, singleLine = false)
            Field("ลิงก์รูปภาพ", image, { image = it })
            Button(onClick = {
                val pr = price.toDoubleOrNull()
                if (name.isBlank() || sku.isBlank() || pr == null) { c.toast("กรอกชื่อ SKU และราคาให้ครบ"); return@Button }
                val p = Product(id, c.session.businessId, sku.trim(), barcode.trim().ifBlank { null }, name.trim(),
                    type.trim().ifBlank { null }, pr, details.ifBlank { null }, image.trim().ifBlank { null }, unit.trim().ifBlank { null })
                scope.io({ c.repo.saveProduct(c.session, p) }, onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) {
                    c.toast("บันทึกสินค้าแล้ว"); c.back()
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("บันทึกสินค้า") }
            if (id != 0L) OutlinedButton(onClick = { c.go(Dest.StockDetail(Kind.PRODUCT, id)) }, Modifier.fillMaxWidth()) {
                Text("ดูสต็อกและรับของเข้า")
            }
            if (history.orEmpty().isNotEmpty()) {
                Text("ประวัติการแก้ไข", style = MaterialTheme.typography.titleSmall)
                history.orEmpty().forEach { (d, t) ->
                    Text("${Time.short(t)}  $d", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("ลบสินค้า?", "สินค้าจะหายจากหน้าขาย แต่บิลเก่ายังอยู่ครบ", "ลบ", onConfirm = {
        scope.io({ c.repo.deleteProduct(c.session, id) }) { c.toast("ลบสินค้าแล้ว"); c.back() }
    }, onDismiss = { confirmDelete = false })
}

// ======================= Equipment =======================
@Composable
fun EquipmentsScreen(c: Ctx) {
    var search by remember { mutableStateOf("") }
    val list by load(search) { c.repo.equipments(c.session.businessId, search) }
    ScreenFrame("อุปกรณ์", "ของใช้ในร้านที่ไม่ได้ขาย", onBack = c.back,
        fab = { if (c.session.isOwner) AddFab("เพิ่มอุปกรณ์") { c.go(Dest.EquipmentEdit(0)) } }) {
        Column {
            Field("ค้นหา", search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), leading = Icons.Filled.Search)
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (list?.isEmpty() == true) item { Empty("ยังไม่มีอุปกรณ์ เช่น แก้ว หลอด ถุง") }
                items(list.orEmpty(), key = { it.id }) { e ->
                    RowItem(e.name, listOfNotNull(e.sku, e.type).joinToString(" • "), leading = { Initials(e.name) }) {
                        c.go(Dest.StockDetail(Kind.EQUIPMENT, e.id))
                    }
                }
            }
        }
    }
}

@Composable
fun EquipmentEditScreen(c: Ctx, id: Long) {
    val scope = rememberCoroutineScope()
    val existing by load(id) { if (id == 0L) null else c.repo.equipment(id) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        existing?.let { sku = it.sku; name = it.name; type = it.type ?: ""; details = it.details ?: ""; loaded = true }
    }
    ScreenFrame(if (id == 0L) "เพิ่มอุปกรณ์" else "แก้ไขอุปกรณ์", onBack = c.back, actions = {
        if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "ลบ") }
    }) {
        if (loaded) FormColumn {
            Field("ชื่ออุปกรณ์ *", name, { name = it })
            Field("SKU *", sku, { sku = it })
            Field("ประเภท", type, { type = it })
            Field("รายละเอียด", details, { details = it }, singleLine = false)
            Button(onClick = {
                if (name.isBlank() || sku.isBlank()) { c.toast("กรอกชื่อและ SKU"); return@Button }
                val e = Equipment(id, c.session.businessId, sku.trim(), name.trim(), type.ifBlank { null }, details.ifBlank { null }, null)
                scope.io({ c.repo.saveEquipment(c.session, e) }, onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) {
                    c.toast("บันทึกอุปกรณ์แล้ว"); c.back()
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("บันทึกอุปกรณ์") }
        }
    }
    if (confirmDelete) ConfirmDialog("ลบอุปกรณ์?", "ประวัติการเบิกยังเก็บไว้", "ลบ", onConfirm = {
        scope.io({ c.repo.deleteEquipment(c.session, id) }) { c.toast("ลบอุปกรณ์แล้ว"); c.back() }
    }, onDismiss = { confirmDelete = false })
}

// ======================= Branches =======================
@Composable
fun BranchesScreen(c: Ctx) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val list by load(tick) { c.repo.branches(c.session.businessId) }
    var editing by remember { mutableStateOf<Branch?>(null) }
    var deleting by remember { mutableStateOf<Branch?>(null) }

    ScreenFrame("สาขา", onBack = c.back,
        fab = { AddFab("เพิ่มสาขา") { editing = Branch(0, c.session.businessId, "", "", null) } }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(list.orEmpty(), key = { it.id }) { b ->
                RowItem(b.name, listOfNotNull("รหัส ${b.code}", b.location).joinToString(" • "), leading = { Initials(b.code) },
                    trailing = {
                        if (b.id != c.session.branchId) IconButton(onClick = { deleting = b }) { Icon(Icons.Filled.Delete, "ลบ") }
                        else Pill("ใช้งานอยู่")
                    }) { editing = b }
            }
        }
    }
    editing?.let { b ->
        var code by remember(b) { mutableStateOf(b.code) }
        var name by remember(b) { mutableStateOf(b.name) }
        var loc by remember(b) { mutableStateOf(b.location ?: "") }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (b.id == 0L) "เพิ่มสาขา" else "แก้ไขสาขา") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("รหัสสาขา (ไม่มีขีด)", code, { code = it })
                    Field("ชื่อสาขา", name, { name = it })
                    Field("ที่ตั้ง", loc, { loc = it })
                }
            },
            confirmButton = { TextButton(onClick = {
                if (code.isBlank() || name.isBlank()) { c.toast("กรอกรหัสและชื่อสาขา"); return@TextButton }
                scope.io({ c.repo.saveBranch(c.session, b.copy(code = code, name = name.trim(), location = loc.ifBlank { null })) },
                    onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) { editing = null; tick++ }
            }) { Text("บันทึก") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("ยกเลิก") } })
    }
    deleting?.let { b ->
        ConfirmDialog("ลบสาขา ${b.name}?", "ข้อมูลบิลและประวัติของสาขานี้ยังเก็บไว้", "ลบ", onConfirm = {
            scope.io({ c.repo.deleteBranch(c.session, b.id) }, onError = { c.toast(it.message ?: "ลบไม่สำเร็จ") }) { tick++ }
        }, onDismiss = { deleting = null })
    }
}

// ======================= Employees =======================
@Composable
fun EmployeesScreen(c: Ctx) {
    val list by load(Unit) { c.repo.employees(c.session.businessId) }
    ScreenFrame("พนักงาน", "${list?.size ?: 0} คน", onBack = c.back,
        fab = { AddFab("เพิ่มพนักงาน") { c.go(Dest.EmployeeEdit(0)) } }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (list?.isEmpty() == true) item { Empty("ยังไม่มีพนักงาน เพิ่มเพื่อให้เข้าระบบขายของได้") }
            items(list.orEmpty(), key = { it.id }) { e ->
                RowItem("${e.firstname} ${e.lastname}",
                    listOfNotNull(e.code, e.position, e.branchName, "ผู้ใช้ ${e.loginId}").joinToString(" • "),
                    leading = { Initials(e.firstname) }) { c.go(Dest.EmployeeEdit(e.id)) }
            }
        }
    }
}

@Composable
fun EmployeeEditScreen(c: Ctx, id: Long) {
    val scope = rememberCoroutineScope()
    val branches by load(Unit) { c.repo.branches(c.session.businessId) }
    val existing by load(id) { if (id == 0L) null else c.repo.employees(c.session.businessId).firstOrNull { it.id == id } }
    var loaded by remember { mutableStateOf(id == 0L) }
    var branchId by remember { mutableLongStateOf(c.session.branchId) }
    var code by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        existing?.let { e ->
            branchId = e.branchId; code = e.code; first = e.firstname; last = e.lastname; position = e.position ?: ""
            salary = e.salary?.let { qtyFmt(it).replace(",", "") } ?: ""; login = e.loginId; loaded = true
        }
    }
    ScreenFrame(if (id == 0L) "เพิ่มพนักงาน" else "แก้ไขพนักงาน", onBack = c.back, actions = {
        if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "ลบ") }
    }) {
        if (loaded) FormColumn {
            Text("สาขา", style = MaterialTheme.typography.titleSmall)
            ChipRow(branches.orEmpty().map { it.id to it.name }, branchId, { branchId = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("ชื่อ *", first, { first = it }, Modifier.weight(1f))
                Field("นามสกุล *", last, { last = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("รหัสพนักงาน *", code, { code = it }, Modifier.weight(1f), placeholder = "E001")
                Field("ตำแหน่ง", position, { position = it }, Modifier.weight(1f))
            }
            Field("เงินเดือน", salary, { salary = it.filter { ch -> ch.isDigit() || ch == '.' } }, number = true)
            Field("ชื่อผู้ใช้เข้าระบบ *", login, { login = it.trim() }, leading = Icons.Filled.Person)
            Field(if (id == 0L) "รหัสผ่าน *" else "รหัสผ่านใหม่ (เว้นว่างถ้าไม่เปลี่ยน)", pw, { pw = it }, password = true,
                leading = Icons.Filled.Lock)
            Button(onClick = {
                if (first.isBlank() || last.isBlank() || code.isBlank() || login.isBlank()) { c.toast("กรอกช่องที่มี * ให้ครบ"); return@Button }
                if (pw.isNotEmpty() && pw.length < 6) { c.toast("รหัสผ่านต้องมีอย่างน้อย 6 ตัว"); return@Button }
                val e = Employee(id, branchId, code, first.trim(), last.trim(), position.ifBlank { null },
                    salary.toDoubleOrNull(), null, login)
                scope.io({ c.repo.saveEmployee(c.session, e, pw.ifBlank { null }) },
                    onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) { c.toast("บันทึกพนักงานแล้ว"); c.back() }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("บันทึกพนักงาน") }
        }
    }
    if (confirmDelete) ConfirmDialog("ลบพนักงาน?", "พนักงานจะเข้าระบบไม่ได้อีก บิลที่เคยขายยังอยู่", "ลบ", onConfirm = {
        existing?.let { e -> scope.io({ c.repo.deleteEmployee(c.session, e) }) { c.toast("ลบพนักงานแล้ว"); c.back() } }
    }, onDismiss = { confirmDelete = false })
}

// ======================= Promotions =======================
@Composable
fun PromotionsScreen(c: Ctx) {
    val list by load(Unit) { c.repo.promotions(c.session.businessId) }
    ScreenFrame("โปรโมชัน", onBack = c.back, fab = { AddFab("สร้างโปรโมชัน") { c.go(Dest.PromotionEdit(0)) } }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (list?.isEmpty() == true) item { Empty("ยังไม่มีโปรโมชัน") }
            items(list.orEmpty(), key = { it.id }) { p ->
                RowItem(p.name, listOfNotNull(p.label, p.start?.let { "เริ่ม ${Time.short(it)}" }, p.end?.let { "ถึง ${Time.short(it)}" })
                    .joinToString(" • "),
                    trailing = { Pill(if (p.status == "active") "เปิด" else "ปิด", warn = p.status != "active") }
                ) { c.go(Dest.PromotionEdit(p.id)) }
            }
        }
    }
}

@Composable
fun PromotionEditScreen(c: Ctx, id: Long) {
    val scope = rememberCoroutineScope()
    val existing by load(id) { if (id == 0L) null else c.repo.promotions(c.session.businessId).firstOrNull { it.id == id } }
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var dtype by remember { mutableStateOf("percent") }
    var value by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        existing?.let { p ->
            name = p.name; dtype = p.discountType; value = qtyFmt(p.value).replace(",", "")
            start = p.start?.take(10) ?: ""; end = p.end?.take(10) ?: ""; active = p.status == "active"; loaded = true
        }
    }
    ScreenFrame(if (id == 0L) "สร้างโปรโมชัน" else "แก้ไขโปรโมชัน", onBack = c.back, actions = {
        if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "ลบ") }
    }) {
        if (loaded) FormColumn {
            Field("ชื่อโปรโมชัน *", name, { name = it }, placeholder = "เช่น ลดต้อนรับเปิดร้าน")
            ChipRow(listOf("percent" to "ลดเป็น %", "amount" to "ลดเป็นบาท"), dtype, { dtype = it })
            Field(if (dtype == "percent") "ส่วนลด (%) *" else "ส่วนลด (บาท) *", value,
                { value = it.filter { ch -> ch.isDigit() || ch == '.' } }, number = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("เริ่ม", start, { start = it }, Modifier.weight(1f), placeholder = "2026-11-01")
                Field("สิ้นสุด", end, { end = it }, Modifier.weight(1f), placeholder = "2026-12-31")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("เปิดใช้งาน", Modifier.weight(1f)); Switch(checked = active, onCheckedChange = { active = it })
            }
            Button(onClick = {
                val v = value.toDoubleOrNull()
                when {
                    name.isBlank() || v == null || v <= 0 -> c.toast("กรอกชื่อและส่วนลดให้ถูกต้อง")
                    dtype == "percent" && (v ?: 0.0) > 100 -> c.toast("ส่วนลดเกิน 100%")
                    (start.isNotBlank() && !Time.isDate(start)) || (end.isNotBlank() && !Time.isDate(end)) ->
                        c.toast("วันที่ต้องเป็นรูปแบบ ปปปป-ดด-วว (ค.ศ.)")
                    else -> {
                        val p = Promotion(id, c.session.businessId, name.trim(), dtype, v ?: 0.0,
                            start.ifBlank { null }?.let { "$it 00:00:00" }, end.ifBlank { null }?.let { "$it 23:59:59" },
                            if (active) "active" else "inactive")
                        scope.io({ c.repo.savePromotion(c.session, p) }, onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) {
                            c.toast("บันทึกโปรโมชันแล้ว"); c.back()
                        }
                    }
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("บันทึกโปรโมชัน") }
        }
    }
    if (confirmDelete) ConfirmDialog("ลบโปรโมชัน?", "บิลที่เคยใช้โปรนี้ยังอยู่", "ลบ", onConfirm = {
        scope.io({ c.repo.deletePromotion(id) }) { c.toast("ลบโปรโมชันแล้ว"); c.back() }
    }, onDismiss = { confirmDelete = false })
}

// ======================= Bills =======================
@Composable
fun BillsScreen(c: Ctx) {
    var search by remember { mutableStateOf("") }
    val list by load(search) { c.repo.bills(c.session.branchId, search) }
    ScreenFrame("ประวัติบิลขาย", c.session.branchName, onBack = c.back) {
        Column {
            Field("ค้นหาเลขบิล", search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), leading = Icons.Filled.Search)
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (list?.isEmpty() == true) item { Empty("ยังไม่มีบิล") }
                items(list.orEmpty(), key = { it.id }) { b ->
                    RowItem(money(b.total), "${Time.short(b.date)} • ${b.items} รายการ • ${payLabel(b.typePay)} • ${b.by}",
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) { c.go(Dest.BillDetail(b.id)) }
                }
            }
        }
    }
}

@Composable
fun BillDetailScreen(c: Ctx, id: String) {
    val bill by load(id) { c.repo.bills(c.session.branchId, id).firstOrNull { it.id == id } }
    val lines by load(id) { c.repo.billLines(id) }
    ScreenFrame("รายละเอียดบิล", onBack = c.back) {
        val b = bill
        if (b != null) FormColumn {
            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(b.total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("${Time.short(b.date)} • ${payLabel(b.typePay)} • ขายโดย ${b.by}")
            HorizontalDivider()
            lines.orEmpty().forEach { l ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name)
                        Text("${money(l.price)} × ${qtyFmt(l.volume)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(l.price * l.volume), fontWeight = FontWeight.Medium)
                }
            }
            HorizontalDivider()
            val subtotal = lines.orEmpty().sumOf { it.price * it.volume }
            Row { Text("รวม", Modifier.weight(1f)); Text(money(subtotal)) }
            if (b.discount > 0) Row { Text("ส่วนลด${b.promoName?.let { " ($it)" } ?: ""}", Modifier.weight(1f)); Text("−${money(b.discount)}") }
            Row { Text("สุทธิ", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(money(b.total), fontWeight = FontWeight.Bold) }
        }
    }
}

// ======================= Business =======================
@Composable
fun BusinessScreen(c: Ctx) {
    val scope = rememberCoroutineScope()
    val biz by load(Unit) { c.repo.business(c.session.businessId) }
    var name by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    LaunchedEffect(biz) { biz?.let { name = it.name; logo = it.logo ?: "" } }
    ScreenFrame("ข้อมูลธุรกิจ", onBack = c.back) {
        FormColumn {
            Field("ชื่อธุรกิจ", name, { name = it })
            Field("ลิงก์โลโก้", logo, { logo = it })
            Button(onClick = {
                if (name.isBlank()) { c.toast("กรอกชื่อธุรกิจ"); return@Button }
                scope.io({ c.repo.updateBusiness(c.session, name.trim(), logo.ifBlank { null }) },
                    onError = { c.toast(it.message ?: "บันทึกไม่สำเร็จ") }) {
                    c.setSession(c.session.copy(businessName = name.trim())); c.toast("บันทึกแล้ว"); c.back()
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("บันทึก") }
        }
    }
}

// ======================= Sync =======================
@Composable
fun SyncScreen(c: Ctx) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(c.sync.baseUrl) }
    var token by remember { mutableStateOf(c.sync.token) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    val pending by load(tick) { c.repo.pendingCount() }

    ScreenFrame("ซิงค์ข้อมูล", onBack = c.back) {
        FormColumn {
            Text("รอส่งขึ้นเซิร์ฟเวอร์", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${pending ?: 0} รายการ", style = MaterialTheme.typography.headlineMedium)
            Text("ซิงค์ล่าสุด: ${c.sync.lastSync.ifBlank { "ยังไม่เคย" }}", style = MaterialTheme.typography.bodySmall)
            Field("ที่อยู่เซิร์ฟเวอร์", url, { url = it }, placeholder = "https://api.yourshop.com")
            Field("API token (ถ้ามี)", token, { token = it }, password = true)
            Button(enabled = !busy, onClick = {
                c.sync.baseUrl = url; c.sync.token = token; busy = true; result = null
                scope.launchSync(c) { msg -> busy = false; result = msg; tick++ }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (busy) "กำลังซิงค์…" else "ซิงค์ตอนนี้") }
            result?.let { Text(it) }
            Text("แอปบันทึกทุกอย่างในเครื่องก่อน แล้วค่อยส่งขึ้นเซิร์ฟเวอร์เมื่อมีอินเทอร์เน็ต",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun CoroutineScope.launchSync(c: Ctx, done: (String) -> Unit) =
    launch {
        done(try { c.sync.syncNow() } catch (e: Throwable) { "ซิงค์ไม่สำเร็จ: ${e.message}" })
    }

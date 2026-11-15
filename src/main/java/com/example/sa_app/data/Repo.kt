package com.example.sa_app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

class Repo(context: Context) {
    val db: SQLiteDatabase by lazy { AppDb(context).writableDatabase }

    // ---------- helpers ----------
    private fun Cursor.s(n: String): String = getString(getColumnIndexOrThrow(n)) ?: ""
    private fun Cursor.sN(n: String): String? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.l(n: String): Long = getLong(getColumnIndexOrThrow(n))
    private fun Cursor.lN(n: String): Long? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.d(n: String): Double = getDouble(getColumnIndexOrThrow(n))
    private fun Cursor.dN(n: String): Double? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getDouble(it) }

    private fun <T> q(sql: String, vararg args: Any, map: (Cursor) -> T): List<T> =
        db.rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { c ->
            val out = ArrayList<T>(); while (c.moveToNext()) out += map(c); out
        }

    private fun one(sql: String, vararg args: Any): Double =
        q(sql, *args) { if (it.isNull(0)) 0.0 else it.getDouble(0) }.firstOrNull() ?: 0.0

    private fun <T> tx(block: () -> T): T {
        db.beginTransaction()
        try { val r = block(); db.setTransactionSuccessful(); return r } finally { db.endTransaction() }
    }

    private fun cv(vararg p: Pair<String, Any?>) = ContentValues().apply {
        p.forEach { (k, v) ->
            when (v) {
                null -> putNull(k); is String -> put(k, v); is Long -> put(k, v); is Int -> put(k, v)
                is Double -> put(k, v); is Boolean -> put(k, if (v) 1 else 0); else -> put(k, v.toString())
            }
        }
        put("sync_status", 0)
    }

    private fun loginTaken(loginId: String, exceptEmp: Long = -1): Boolean =
        one("SELECT COUNT(*) FROM User WHERE login_id=?", loginId) > 0 ||
            one("SELECT COUNT(*) FROM Employee WHERE login_id=? AND id_employee<>?", loginId, exceptEmp) > 0

    // ---------- Auth ----------
    fun hasOwner(): Boolean = one("SELECT COUNT(*) FROM User WHERE deleted_at IS NULL") > 0

    fun registerOwner(
        first: String, last: String, loginId: String, pw: String,
        businessName: String, branchCode: String, branchName: String
    ): Session {
        require(!loginTaken(loginId)) { "ชื่อผู้ใช้ \"$loginId\" ถูกใช้แล้ว" }
        val now = Time.now()
        tx {
            val uid = db.insertOrThrow("User", null, cv("firstname" to first, "lastname" to last,
                "login_id" to loginId, "password" to Pw.hash(pw), "date" to now, "updated_at" to now))
            val bid = db.insertOrThrow("Business", null, cv("id_user" to uid, "name_business" to businessName,
                "date" to now, "updated_at" to now))
            db.insertOrThrow("Branch", null, cv("id_business" to bid, "branch_id" to Ids.clean(branchCode),
                "name" to branchName, "date" to now, "updated_at" to now))
        }
        return login(loginId, pw)!!
    }

    private data class OwnerRow(val id: Long, val name: String, val hash: String)

    fun login(loginId: String, pw: String): Session? {
        val owner = q("SELECT id_user, firstname, lastname, password FROM User WHERE login_id=? AND deleted_at IS NULL", loginId) {
            OwnerRow(it.l("id_user"), "${it.s("firstname")} ${it.s("lastname")}", it.s("password"))
        }.firstOrNull()
        if (owner != null) {
            if (!Pw.verify(pw, owner.hash)) return null
            val biz = q("SELECT * FROM Business WHERE id_user=? AND deleted_at IS NULL ORDER BY id_business", owner.id) {
                Business(it.l("id_business"), owner.id, it.s("name_business"), it.sN("logo"))
            }.firstOrNull() ?: return null
            val br = branches(biz.id).firstOrNull() ?: return null
            return Session(owner.id, null, owner.name, true, biz.id, biz.name, br.id, br.code, br.name, "U${owner.id}")
        }
        return q("""SELECT e.*, b.branch_id AS bcode, b.name AS bname, b.id_business, z.name_business
                    FROM Employee e JOIN Branch b ON b.id_branch=e.id_branch
                    JOIN Business z ON z.id_business=b.id_business
                    WHERE e.login_id=? AND e.deleted_at IS NULL""", loginId) {
            if (!Pw.verify(pw, it.s("password"))) null
            else Session(null, it.l("id_employee"), "${it.s("firstname")} ${it.s("lastname")}", false,
                it.l("id_business"), it.s("name_business"), it.l("id_branch"), it.s("bcode"), it.s("bname"),
                it.s("employee_id"))
        }.firstOrNull()
    }

    fun switchBranch(s: Session, branch: Branch) =
        s.copy(branchId = branch.id, branchCode = branch.code, branchName = branch.name)

    // ---------- Business ----------
    fun business(id: Long): Business? =
        q("SELECT * FROM Business WHERE id_business=?", id) {
            Business(it.l("id_business"), it.l("id_user"), it.s("name_business"), it.sN("logo"))
        }.firstOrNull()

    fun updateBusiness(s: Session, name: String, logo: String?) = tx {
        val old = business(s.businessId)
        db.update("Business", cv("name_business" to name, "logo" to logo, "updated_at" to Time.now()),
            "id_business=?", arrayOf(s.businessId.toString()))
        db.insertOrThrow("History_Business", null, cv("id_history_business" to Ids.new(s.branchCode, s.actorCode),
            "id_business" to s.businessId, "details" to "แก้ไขข้อมูลธุรกิจ: ${old?.name} → $name by ${s.name}",
            "date" to Time.now()))
    }

    // ---------- Branch ----------
    fun branches(bid: Long): List<Branch> =
        q("SELECT * FROM Branch WHERE id_business=? AND deleted_at IS NULL ORDER BY id_branch", bid) {
            Branch(it.l("id_branch"), bid, it.s("branch_id"), it.s("name"), it.sN("location"))
        }

    fun saveBranch(s: Session, b: Branch): Long = tx {
        val now = Time.now()
        val code = Ids.clean(b.code)
        require(one("SELECT COUNT(*) FROM Branch WHERE id_business=? AND branch_id=? AND id_branch<>? AND deleted_at IS NULL",
            s.businessId, code, b.id) == 0.0) { "รหัสสาขา $code ซ้ำ" }
        val id = if (b.id == 0L) db.insertOrThrow("Branch", null, cv("id_business" to s.businessId,
            "branch_id" to code, "name" to b.name, "location" to b.location, "date" to now, "updated_at" to now))
        else { db.update("Branch", cv("branch_id" to code, "name" to b.name, "location" to b.location,
            "updated_at" to now), "id_branch=?", arrayOf(b.id.toString())); b.id }
        db.insertOrThrow("History_Branch", null, cv("id_history_branch" to Ids.new(s.branchCode, s.actorCode),
            "id_branch" to id, "details" to (if (b.id == 0L) "เพิ่มสาขา ${b.name}" else "แก้ไขสาขา ${b.name}") + " by ${s.name}",
            "date" to now))
        id
    }

    fun deleteBranch(s: Session, id: Long) = tx {
        require(id != s.branchId) { "ลบสาขาที่กำลังใช้งานอยู่ไม่ได้" }
        val now = Time.now()
        db.update("Branch", cv("deleted_at" to now, "updated_at" to now), "id_branch=?", arrayOf(id.toString()))
        db.insertOrThrow("History_Branch", null, cv("id_history_branch" to Ids.new(s.branchCode, s.actorCode),
            "id_branch" to id, "details" to "ลบสาขา by ${s.name}", "date" to now))
    }

    // ---------- Employee ----------
    fun employees(bid: Long): List<Employee> =
        q("""SELECT e.*, b.name AS bname FROM Employee e JOIN Branch b ON b.id_branch=e.id_branch
             WHERE b.id_business=? AND e.deleted_at IS NULL ORDER BY b.id_branch, e.firstname""", bid) {
            Employee(it.l("id_employee"), it.l("id_branch"), it.s("employee_id"), it.s("firstname"),
                it.s("lastname"), it.sN("position"), it.dN("salary"), it.sN("image"), it.s("login_id"), it.s("bname"))
        }

    fun saveEmployee(s: Session, e: Employee, password: String?): Long = tx {
        val now = Time.now()
        require(!loginTaken(e.loginId, e.id)) { "ชื่อผู้ใช้ \"${e.loginId}\" ถูกใช้แล้ว" }
        val base = arrayOf<Pair<String, Any?>>("id_branch" to e.branchId, "employee_id" to Ids.clean(e.code), "firstname" to e.firstname,
            "lastname" to e.lastname, "position" to e.position, "salary" to e.salary, "image" to e.image,
            "login_id" to e.loginId, "updated_at" to now)
        val id = if (e.id == 0L) {
            require(!password.isNullOrBlank()) { "กรุณาตั้งรหัสผ่าน" }
            db.insertOrThrow("Employee", null, cv(*base, "password" to Pw.hash(password!!), "date" to now))
        } else {
            val values = if (password.isNullOrBlank()) cv(*base) else cv(*base, "password" to Pw.hash(password))
            db.update("Employee", values, "id_employee=?", arrayOf(e.id.toString())); e.id
        }
        db.insertOrThrow("History_Employee", null, cv("id_history_employee" to Ids.new(s.branchCode, s.actorCode),
            "id_branch" to e.branchId, "id_employee" to id,
            "details" to (if (e.id == 0L) "เพิ่มพนักงาน" else "แก้ไขพนักงาน") + " ${e.firstname} by ${s.name}",
            "date" to now))
        id
    }

    fun deleteEmployee(s: Session, e: Employee) = tx {
        val now = Time.now()
        db.update("Employee", cv("deleted_at" to now, "updated_at" to now), "id_employee=?", arrayOf(e.id.toString()))
        db.insertOrThrow("History_Employee", null, cv("id_history_employee" to Ids.new(s.branchCode, s.actorCode),
            "id_branch" to e.branchId, "id_employee" to e.id, "details" to "ลบพนักงาน ${e.firstname} by ${s.name}",
            "date" to now))
    }

    // ---------- Product ----------
    private fun mapProduct(c: Cursor) = Product(c.l("id_product"), c.l("id_business"), c.s("sku"), c.sN("barcode"),
        c.s("name"), c.sN("type"), c.d("price"), c.sN("details"), c.sN("image"), c.sN("type_sell"))

    fun products(bid: Long, search: String = ""): List<Product> {
        val k = "%${search.trim()}%"
        return q("""SELECT * FROM Product WHERE id_business=? AND deleted_at IS NULL
                    AND (name LIKE ? OR sku LIKE ? OR IFNULL(barcode,'') LIKE ?) ORDER BY name""",
            bid, k, k, k, map = ::mapProduct)
    }

    fun product(id: Long): Product? = q("SELECT * FROM Product WHERE id_product=?", id, map = ::mapProduct).firstOrNull()

    fun productTypes(bid: Long): List<String> =
        q("SELECT DISTINCT type FROM Product WHERE id_business=? AND deleted_at IS NULL AND IFNULL(type,'')<>'' ORDER BY type", bid) { it.getString(0) }

    fun saveProduct(s: Session, p: Product): Long = tx {
        val now = Time.now()
        require(one("SELECT COUNT(*) FROM Product WHERE id_business=? AND sku=? AND id_product<>? AND deleted_at IS NULL",
            s.businessId, p.sku, p.id) == 0.0) { "SKU ${p.sku} ซ้ำกับสินค้าอื่น" }
        val values = cv("id_business" to s.businessId, "sku" to p.sku, "barcode" to p.barcode, "name" to p.name,
            "type" to p.type, "price" to p.price, "details" to p.details, "image" to p.image,
            "type_sell" to p.typeSell, "updated_at" to now)
        val old = if (p.id == 0L) null else product(p.id)
        val id = if (p.id == 0L) db.insertOrThrow("Product", null, values.apply { put("date", now) })
        else { db.update("Product", values, "id_product=?", arrayOf(p.id.toString())); p.id }
        val details = if (old == null) "เพิ่มสินค้า ${p.name} ราคา ${money(p.price)}" else buildList {
            if (old.name != p.name) add("ชื่อ ${old.name} → ${p.name}")
            if (old.price != p.price) add("ราคา ${money(old.price)} → ${money(p.price)}")
            if (old.sku != p.sku) add("SKU ${old.sku} → ${p.sku}")
            if (old.barcode != p.barcode) add("บาร์โค้ด ${old.barcode ?: "-"} → ${p.barcode ?: "-"}")
            if (old.type != p.type) add("ประเภท ${old.type ?: "-"} → ${p.type ?: "-"}")
            if (old.typeSell != p.typeSell) add("หน่วยขาย ${old.typeSell ?: "-"} → ${p.typeSell ?: "-"}")
            if (old.details != p.details) add("แก้รายละเอียด")
        }.joinToString(", ").ifEmpty { "บันทึกโดยไม่มีการเปลี่ยนแปลง" }
        db.insertOrThrow("History_Edit_Product", null, cv("id_edit_product" to Ids.new(s.branchCode, s.actorCode),
            "id_business" to s.businessId, "id_product" to id, "details" to "$details by ${s.name}", "date" to now))
        id
    }

    fun deleteProduct(s: Session, id: Long) = tx {
        val now = Time.now()
        db.update("Product", cv("deleted_at" to now, "updated_at" to now), "id_product=?", arrayOf(id.toString()))
        db.insertOrThrow("History_Edit_Product", null, cv("id_edit_product" to Ids.new(s.branchCode, s.actorCode),
            "id_business" to s.businessId, "id_product" to id, "details" to "ลบสินค้า by ${s.name}", "date" to now))
    }

    fun productEditHistory(id: Long): List<Pair<String, String>> =
        q("SELECT details, date FROM History_Edit_Product WHERE id_product=? ORDER BY date DESC LIMIT 30", id) {
            it.s("details") to it.s("date")
        }

    // ---------- Equipment ----------
    private fun mapEquip(c: Cursor) = Equipment(c.l("id_equipment"), c.l("id_business"), c.s("sku"), c.s("name"),
        c.sN("type"), c.sN("details"), c.sN("image"))

    fun equipments(bid: Long, search: String = ""): List<Equipment> {
        val k = "%${search.trim()}%"
        return q("""SELECT * FROM Equipment WHERE id_business=? AND deleted_at IS NULL
                    AND (name LIKE ? OR sku LIKE ?) ORDER BY name""", bid, k, k, map = ::mapEquip)
    }

    fun equipment(id: Long): Equipment? = q("SELECT * FROM Equipment WHERE id_equipment=?", id, map = ::mapEquip).firstOrNull()

    fun saveEquipment(s: Session, e: Equipment): Long = tx {
        val now = Time.now()
        val values = cv("id_business" to s.businessId, "sku" to e.sku, "name" to e.name, "type" to e.type,
            "details" to e.details, "image" to e.image, "updated_at" to now)
        val old = if (e.id == 0L) null else equipment(e.id)
        val id = if (e.id == 0L) db.insertOrThrow("Equipment", null, values.apply { put("date", now) })
        else { db.update("Equipment", values, "id_equipment=?", arrayOf(e.id.toString())); e.id }
        val details = if (old == null) "เพิ่มอุปกรณ์ ${e.name}"
        else "แก้ไขอุปกรณ์ ${old.name}" + if (old.name != e.name) " → ${e.name}" else ""
        db.insertOrThrow("History_Edit_Equipment", null, cv("id_edit_equipment" to Ids.new(s.branchCode, s.actorCode),
            "id_business" to s.businessId, "id_equipment" to id, "details" to "$details by ${s.name}", "date" to now))
        id
    }

    fun deleteEquipment(s: Session, id: Long) = tx {
        val now = Time.now()
        db.update("Equipment", cv("deleted_at" to now, "updated_at" to now), "id_equipment=?", arrayOf(id.toString()))
        db.insertOrThrow("History_Edit_Equipment", null, cv("id_edit_equipment" to Ids.new(s.branchCode, s.actorCode),
            "id_business" to s.businessId, "id_equipment" to id, "details" to "ลบอุปกรณ์ by ${s.name}", "date" to now))
    }

    // ---------- Stock ----------
    fun stock(kind: Kind, s: Session, search: String = ""): List<StockSummary> {
        val k = "%${search.trim()}%"
        val price = if (kind == Kind.PRODUCT) "i.price" else "0"
        return q("""SELECT i.${kind.itemCol} AS iid, i.name, i.sku, i.type, $price AS price,
                       IFNULL(SUM(t.quantity),0) AS qty,
                       MIN(CASE WHEN t.quantity>0 THEN t.exp_date END) AS exp
                    FROM ${kind.itemTable} i
                    LEFT JOIN ${kind.store} t ON t.${kind.itemCol}=i.${kind.itemCol}
                         AND t.id_branch=? AND t.deleted_at IS NULL
                    WHERE i.id_business=? AND i.deleted_at IS NULL AND (i.name LIKE ? OR i.sku LIKE ?)
                    GROUP BY i.${kind.itemCol} ORDER BY i.name""", s.branchId, s.businessId, k, k) {
            StockSummary(it.l("iid"), it.s("name"), it.s("sku"), it.sN("type"), it.d("qty"), it.sN("exp"), it.d("price"))
        }
    }

    fun productQtyMap(branchId: Long): Map<Long, Double> =
        q("SELECT id_product, SUM(quantity) AS q FROM Store_Product WHERE id_branch=? AND deleted_at IS NULL GROUP BY id_product",
            branchId) { it.l("id_product") to it.d("q") }.toMap()

    fun lots(kind: Kind, branchId: Long, itemId: Long): List<Lot> =
        q("""SELECT * FROM ${kind.store} WHERE id_branch=? AND ${kind.itemCol}=? AND deleted_at IS NULL
             ORDER BY exp_date IS NULL, exp_date, ${kind.storePk}""", branchId, itemId) {
            Lot(it.l(kind.storePk), it.d("quantity"), it.sN("mfg_date"), it.sN("exp_date"), it.sN("location"))
        }

    fun stockHistory(kind: Kind, branchId: Long, itemId: Long): List<StockHistory> =
        q("""SELECT * FROM ${kind.hist} WHERE id_branch=? AND ${kind.itemCol}=? ORDER BY date DESC LIMIT 50""",
            branchId, itemId) {
            StockHistory(it.s(kind.histPk), it.sN("type"), it.dN("before") ?: 0.0, it.dN("after") ?: 0.0,
                it.s("edit_name"), it.sN("details"), it.s("date"))
        }

    private fun total(kind: Kind, branchId: Long, itemId: Long) =
        one("SELECT SUM(quantity) FROM ${kind.store} WHERE id_branch=? AND ${kind.itemCol}=? AND deleted_at IS NULL",
            branchId, itemId)

    private fun setLotQty(kind: Kind, lotId: Long, qty: Double) =
        db.update(kind.store, cv("quantity" to qty, "updated_at" to Time.now()), "${kind.storePk}=?", arrayOf(lotId.toString()))

    private fun newLot(kind: Kind, branchId: Long, itemId: Long, qty: Double, mfg: String?, exp: String?, loc: String?): Long {
        val now = Time.now()
        return db.insertOrThrow(kind.store, null, cv("id_branch" to branchId, kind.itemCol to itemId, "quantity" to qty,
            "mfg_date" to mfg, "exp_date" to exp, "location" to loc, "date" to now, "updated_at" to now))
    }

    /** ตัดสต็อกแบบ FEFO (หมดอายุก่อนออกก่อน) */
    private fun deductFefo(kind: Kind, branchId: Long, itemId: Long, qty: Double) {
        var left = qty
        val all = lots(kind, branchId, itemId)
        for (lot in all.filter { it.qty > 0 }) {
            if (left <= 0) break
            val take = minOf(lot.qty, left)
            setLotQty(kind, lot.id, lot.qty - take); left -= take
        }
        if (left > 0) { // ขายเกินสต็อก: บันทึกติดลบไว้ที่ล็อตแรก
            val first = all.firstOrNull()
            if (first != null) setLotQty(kind, first.id, (lots(kind, branchId, itemId).first { it.id == first.id }.qty) - left)
            else newLot(kind, branchId, itemId, -left, null, null, null)
        }
    }

    private fun writeHistory(kind: Kind, s: Session, itemId: Long, type: String, before: Double, after: Double, details: String?) {
        db.execSQL("""INSERT INTO ${kind.hist}(${kind.histPk}, id_branch, ${kind.itemCol}, type, "before", "after",
                      edit_name, details, date, sync_status) VALUES(?,?,?,?,?,?,?,?,?,0)""",
            arrayOf<Any?>(Ids.new(s.branchCode, s.actorCode), s.branchId, itemId, type, before, after, s.name, details, Time.now()))
    }

    /** type: in = รับเข้า, out = เบิก/ตัดออก, adjust = ตั้งยอดล็อตใหม่ */
    fun moveStock(
        s: Session, kind: Kind, itemId: Long, type: String, qty: Double, lotId: Long?,
        mfg: String?, exp: String?, location: String?, details: String?
    ) = tx {
        require(qty >= 0) { "จำนวนต้องไม่ติดลบ" }
        val before = total(kind, s.branchId, itemId)
        when (type) {
            "in" -> {
                val same = lots(kind, s.branchId, itemId).firstOrNull { it.exp == exp && it.location == location && it.mfg == mfg }
                if (same != null) setLotQty(kind, same.id, same.qty + qty)
                else newLot(kind, s.branchId, itemId, qty, mfg, exp, location)
            }
            "out" -> if (lotId != null) {
                val lot = lots(kind, s.branchId, itemId).first { it.id == lotId }
                setLotQty(kind, lot.id, lot.qty - qty)
            } else deductFefo(kind, s.branchId, itemId, qty)
            "adjust" -> if (lotId != null) setLotQty(kind, lotId, qty)
            else newLot(kind, s.branchId, itemId, qty, mfg, exp, location)
        }
        writeHistory(kind, s, itemId, type, before, total(kind, s.branchId, itemId), details)
    }

    // ---------- Promotion ----------
    private fun mapPromo(c: Cursor) = Promotion(c.l("id_promotions"), c.l("id_business"), c.s("name"),
        c.sN("discount_type") ?: "percent", c.dN("discount_value") ?: 0.0, c.sN("start_date"), c.sN("end_date"),
        c.sN("status") ?: "active")

    fun promotions(bid: Long): List<Promotion> =
        q("SELECT * FROM Promotion WHERE id_business=? AND deleted_at IS NULL ORDER BY id_promotions DESC", bid, map = ::mapPromo)

    fun activePromotions(bid: Long): List<Promotion> {
        val now = Time.now()
        return q("""SELECT * FROM Promotion WHERE id_business=? AND deleted_at IS NULL AND status='active'
                    AND (start_date IS NULL OR start_date<=?) AND (end_date IS NULL OR end_date>=?)
                    ORDER BY name""", bid, now, now, map = ::mapPromo)
    }

    fun savePromotion(s: Session, p: Promotion): Long {
        val now = Time.now()
        val values = cv("id_business" to s.businessId, "name" to p.name, "discount_type" to p.discountType,
            "discount_value" to p.value, "start_date" to p.start, "end_date" to p.end, "status" to p.status,
            "updated_at" to now)
        return if (p.id == 0L) db.insertOrThrow("Promotion", null, values.apply { put("date", now) })
        else { db.update("Promotion", values, "id_promotions=?", arrayOf(p.id.toString())); p.id }
    }

    fun deletePromotion(id: Long) {
        val now = Time.now()
        db.update("Promotion", cv("deleted_at" to now, "updated_at" to now), "id_promotions=?", arrayOf(id.toString()))
    }

    // ---------- Sale / Bill ----------
    /** @return (id_bill, ยอดสุทธิ) */
    fun checkout(s: Session, cart: List<CartLine>, promo: Promotion?, typePay: String): Pair<String, Double> = tx {
        require(cart.isNotEmpty()) { "ยังไม่มีสินค้าในตะกร้า" }
        val subtotal = cart.sumOf { it.product.price * it.qty }
        val discount = promo?.discountFor(subtotal) ?: 0.0
        val net = subtotal - discount
        val used = HashSet<String>()
        fun uid(): String { var id: String; do { id = Ids.new(s.branchCode, s.actorCode) } while (!used.add(id)); return id }
        val billId = uid()
        db.insertOrThrow("Bill", null, cv("id_bill" to billId, "id_branch" to s.branchId, "id_employee" to s.employeePk,
            "id_user" to s.userId, "total_price" to net, "discount" to discount, "id_promotions" to promo?.id,
            "type_pay" to typePay, "date" to Time.now()))
        cart.forEach { line ->
            db.insertOrThrow("Basket", null, cv("id_basket" to uid(), "id_branch" to s.branchId, "id_bill" to billId,
                "id_product" to line.product.id, "volume" to line.qty, "price" to line.product.price))
            val before = total(Kind.PRODUCT, s.branchId, line.product.id)
            deductFefo(Kind.PRODUCT, s.branchId, line.product.id, line.qty)
            writeHistory(Kind.PRODUCT, s, line.product.id, "out", before,
                total(Kind.PRODUCT, s.branchId, line.product.id), "ขาย บิล $billId")
        }
        billId to net
    }

    fun bills(branchId: Long, search: String = ""): List<Bill> =
        q("""SELECT b.*, pr.name AS promo, (SELECT COUNT(*) FROM Basket k WHERE k.id_bill=b.id_bill) AS n,
                COALESCE(e.firstname, u.firstname, '-') AS byname
             FROM Bill b LEFT JOIN Promotion pr ON pr.id_promotions=b.id_promotions
             LEFT JOIN Employee e ON e.id_employee=b.id_employee LEFT JOIN User u ON u.id_user=b.id_user
             WHERE b.id_branch=? AND b.id_bill LIKE ? ORDER BY b.date DESC LIMIT 300""", branchId, "%${search.trim()}%") {
            Bill(it.s("id_bill"), it.d("total_price"), it.dN("discount") ?: 0.0, it.sN("type_pay"), it.s("date"),
                it.s("byname"), it.sN("promo"), it.getInt(it.getColumnIndexOrThrow("n")))
        }

    fun billLines(billId: String): List<BillLine> =
        q("""SELECT p.name, k.volume, k.price FROM Basket k JOIN Product p ON p.id_product=k.id_product
             WHERE k.id_bill=?""", billId) { BillLine(it.s("name"), it.d("volume"), it.d("price")) }

    // ---------- Dashboard / Reports ----------
    fun salesSince(branchId: Long, fromDate: String): Pair<Double, Int> =
        q("SELECT IFNULL(SUM(total_price),0) AS t, COUNT(*) AS n FROM Bill WHERE id_branch=? AND date>=?",
            branchId, fromDate) { it.d("t") to it.getInt(1) }.first()

    fun topProducts(branchId: Long, days: Int, limit: Int = 5): List<Pair<String, Double>> =
        q("""SELECT p.name, SUM(k.volume) AS v FROM Basket k JOIN Bill b ON b.id_bill=k.id_bill
             JOIN Product p ON p.id_product=k.id_product
             WHERE b.id_branch=? AND b.date>=? GROUP BY p.id_product ORDER BY v DESC LIMIT ?""",
            branchId, Time.dayOffset(-days + 1), limit) { it.s("name") to it.d("v") }

    fun dashboard(s: Session): Dashboard {
        val today = Time.today()
        val (todaySales, todayBills) = salesSince(s.branchId, today)
        val month = salesSince(s.branchId, today.substring(0, 8) + "01").first
        val last7 = (6 downTo 0).map { i ->
            val day = Time.dayOffset(-i)
            day to one("SELECT SUM(total_price) FROM Bill WHERE id_branch=? AND date LIKE ?", s.branchId, "$day%")
        }
        val stock = stock(Kind.PRODUCT, s)
        val soon = Time.dayOffset(30)
        return Dashboard(
            todaySales, todayBills, month,
            stock.filter { it.qty <= LOW_STOCK }.sortedBy { it.qty },
            stock.filter { it.nearestExp != null && it.nearestExp <= soon }.sortedBy { it.nearestExp },
            last7, topProducts(s.branchId, 30)
        )
    }

    // ---------- AI chat ----------
    fun chatBoxes(bid: Long): List<ChatBox> =
        q("""SELECT m.id_message_box, m.date,
                (SELECT content FROM Message_AI a WHERE a.id_message_box=m.id_message_box AND a.role='user'
                 ORDER BY a.id_message_ai LIMIT 1) AS first
             FROM Message_Box m WHERE m.id_business=? ORDER BY m.id_message_box DESC LIMIT 50""", bid) {
            ChatBox(it.l("id_message_box"), it.s("date"), it.sN("first") ?: "แชทใหม่")
        }

    fun newChatBox(bid: Long): Long = db.insertOrThrow("Message_Box", null, cv("id_business" to bid, "date" to Time.now()))

    fun messages(boxId: Long): List<ChatMsg> =
        q("""SELECT a.*, (SELECT GROUP_CONCAT(tool_name) FROM Ai_Active x WHERE x.id_message_ai=a.id_message_ai) AS tools
             FROM Message_AI a WHERE a.id_message_box=? AND a.role<>'system' ORDER BY a.id_message_ai""", boxId) {
            ChatMsg(it.l("id_message_ai"), it.s("role"), it.s("content"), it.sN("ai_model"), it.s("date"),
                it.sN("tools")?.split(",") ?: emptyList())
        }

    fun addMessage(boxId: Long, role: String, content: String, model: String?): Long =
        db.insertOrThrow("Message_AI", null, cv("id_message_box" to boxId, "role" to role, "content" to content,
            "ai_model" to model, "date" to Time.now()))

    fun addAiActive(msgId: Long, tool: String, args: JSONObject, result: JSONObject?, error: String?,
                    targetId: Long?, targetTable: String?) {
        db.insertOrThrow("Ai_Active", null, cv("id_message_ai" to msgId, "tool_name" to tool,
            "arguments" to args.toString(), "result" to result?.toString(), "error_message" to error,
            "target_id" to targetId, "target_table" to targetTable,
            "status" to if (error == null) "success" else "error", "date" to Time.now()))
    }

    // ---------- Sync ----------
    fun pendingCount(): Int = AppDb.SYNC_TABLES.keys.sumOf {
        one("SELECT COUNT(*) FROM $it WHERE sync_status=0").toInt()
    }

    /** แถวที่ยังไม่ซิงค์ทั้งหมด: { "Product": [ {...}, ... ], ... } */
    fun collectPending(): JSONObject {
        val out = JSONObject()
        AppDb.SYNC_TABLES.keys.forEach { table ->
            val arr = JSONArray()
            db.rawQuery("SELECT * FROM $table WHERE sync_status=0", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        if (name == "sync_status") continue
                        o.put(name, when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            else -> c.getString(i)
                        })
                    }
                    arr.put(o)
                }
            }
            if (arr.length() > 0) out.put(table, arr)
        }
        return out
    }

    fun markSynced(sent: JSONObject) = tx {
        sent.keys().forEach { table ->
            val pk = AppDb.SYNC_TABLES[table] ?: return@forEach
            val arr = sent.getJSONArray(table)
            for (i in 0 until arr.length()) {
                db.execSQL("UPDATE $table SET sync_status=1 WHERE $pk=?", arrayOf<Any?>(arr.getJSONObject(i).get(pk)))
            }
        }
    }

    /** รับข้อมูลจากเซิร์ฟเวอร์ (รูปแบบเดียวกับ collectPending) แล้ว upsert */
    fun applyPulled(tables: JSONObject) = tx {
        AppDb.SYNC_TABLES.keys.filter { tables.has(it) }.forEach { table ->
            val arr = tables.getJSONArray(table)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val values = ContentValues()
                o.keys().forEach { k ->
                    when (val v = o.get(k)) {
                        JSONObject.NULL -> values.putNull(k)
                        is Int -> values.put(k, v.toLong()); is Long -> values.put(k, v)
                        is Double -> values.put(k, v); is Boolean -> values.put(k, if (v) 1 else 0)
                        else -> values.put(k, v.toString())
                    }
                }
                values.put("sync_status", 1)
                db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }
}

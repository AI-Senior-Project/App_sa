package com.example.sa_app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * ฐานข้อมูลในเครื่อง (SQLite) — โครงสร้างเหมือน Data_sever_app_ai_v2.sql
 * เพิ่มคอลัมน์ sync_status (0 = รอส่งขึ้นเซิร์ฟเวอร์, 1 = ซิงค์แล้ว) ในทุกตาราง
 * เพื่อให้แอปทำงาน offline แล้วค่อยซิงค์ภายหลัง
 */
class AppDb(context: Context) : SQLiteOpenHelper(context, "shop_ai.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.forEach { db.execSQL(it) }
        INDEXES.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // เวอร์ชันถัดไป: เพิ่ม migration ตรงนี้
    }

    companion object {
        private const val S = "sync_status INTEGER NOT NULL DEFAULT 0"
        private const val TS = "updated_at TEXT, deleted_at TEXT"

        val SCHEMA = listOf(
            """CREATE TABLE User(
                id_user INTEGER PRIMARY KEY AUTOINCREMENT,
                firstname TEXT NOT NULL, lastname TEXT NOT NULL, image TEXT, age INTEGER,
                login_id TEXT NOT NULL UNIQUE, password TEXT NOT NULL,
                date TEXT, $TS, $S)""",
            """CREATE TABLE Business(
                id_business INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL REFERENCES User(id_user),
                name_business TEXT NOT NULL, logo TEXT, date TEXT, $TS, $S)""",
            """CREATE TABLE History_Business(
                id_history_business TEXT PRIMARY KEY,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Product(
                id_product INTEGER PRIMARY KEY AUTOINCREMENT,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                sku TEXT NOT NULL, barcode TEXT, name TEXT NOT NULL, type TEXT,
                price REAL NOT NULL DEFAULT 0, details TEXT, image TEXT, type_sell TEXT,
                date TEXT, $TS, $S)""",
            """CREATE TABLE History_Edit_Product(
                id_edit_product TEXT PRIMARY KEY,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                id_product INTEGER NOT NULL REFERENCES Product(id_product),
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Equipment(
                id_equipment INTEGER PRIMARY KEY AUTOINCREMENT,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                sku TEXT NOT NULL, name TEXT NOT NULL, type TEXT, details TEXT, image TEXT,
                date TEXT, $TS, $S)""",
            """CREATE TABLE History_Edit_Equipment(
                id_edit_equipment TEXT PRIMARY KEY,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                id_equipment INTEGER NOT NULL REFERENCES Equipment(id_equipment),
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Branch(
                id_branch INTEGER PRIMARY KEY AUTOINCREMENT,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                branch_id TEXT NOT NULL, name TEXT NOT NULL, location TEXT,
                date TEXT, $TS, $S)""",
            """CREATE TABLE History_Branch(
                id_history_branch TEXT PRIMARY KEY,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Employee(
                id_employee INTEGER PRIMARY KEY AUTOINCREMENT,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                employee_id TEXT NOT NULL, firstname TEXT NOT NULL, lastname TEXT NOT NULL,
                position TEXT, salary REAL, image TEXT,
                login_id TEXT NOT NULL UNIQUE, password TEXT NOT NULL,
                date TEXT, $TS, $S)""",
            """CREATE TABLE History_Employee(
                id_history_employee TEXT PRIMARY KEY,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_employee INTEGER NOT NULL REFERENCES Employee(id_employee),
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Store_Product(
                id_store_product INTEGER PRIMARY KEY AUTOINCREMENT,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_product INTEGER NOT NULL REFERENCES Product(id_product),
                quantity REAL NOT NULL DEFAULT 0, mfg_date TEXT, exp_date TEXT, location TEXT,
                date TEXT, $TS, $S)""",
            """CREATE TABLE History_Store_Product(
                id_history_store_product TEXT PRIMARY KEY,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_product INTEGER NOT NULL REFERENCES Product(id_product),
                type TEXT, "before" REAL, "after" REAL, edit_name TEXT NOT NULL,
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Store_Equipment(
                id_store_equipment INTEGER PRIMARY KEY AUTOINCREMENT,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_equipment INTEGER NOT NULL REFERENCES Equipment(id_equipment),
                quantity REAL NOT NULL DEFAULT 0, mfg_date TEXT, exp_date TEXT, location TEXT,
                date TEXT, $TS, $S)""",
            """CREATE TABLE History_Store_Equipment(
                id_history_store_equipment TEXT PRIMARY KEY,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_equipment INTEGER NOT NULL REFERENCES Equipment(id_equipment),
                type TEXT, "before" REAL, "after" REAL, edit_name TEXT NOT NULL,
                details TEXT, date TEXT, $S)""",
            """CREATE TABLE Promotion(
                id_promotions INTEGER PRIMARY KEY AUTOINCREMENT,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                name TEXT NOT NULL, discount_type TEXT, discount_value REAL,
                start_date TEXT, end_date TEXT, status TEXT,
                date TEXT, $TS, $S)""",
            """CREATE TABLE Bill(
                id_bill TEXT PRIMARY KEY,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_employee INTEGER REFERENCES Employee(id_employee),
                id_user INTEGER REFERENCES User(id_user),
                total_price REAL NOT NULL DEFAULT 0, discount REAL DEFAULT 0,
                id_promotions INTEGER REFERENCES Promotion(id_promotions),
                type_pay TEXT, date TEXT, $S)""",
            """CREATE TABLE Basket(
                id_basket TEXT PRIMARY KEY,
                id_branch INTEGER NOT NULL REFERENCES Branch(id_branch),
                id_bill TEXT NOT NULL REFERENCES Bill(id_bill),
                id_product INTEGER NOT NULL REFERENCES Product(id_product),
                volume REAL NOT NULL DEFAULT 1, price REAL NOT NULL DEFAULT 0, $S)""",
            """CREATE TABLE Message_Box(
                id_message_box INTEGER PRIMARY KEY AUTOINCREMENT,
                id_business INTEGER NOT NULL REFERENCES Business(id_business),
                date TEXT, $S)""",
            """CREATE TABLE Message_AI(
                id_message_ai INTEGER PRIMARY KEY AUTOINCREMENT,
                id_message_box INTEGER NOT NULL REFERENCES Message_Box(id_message_box),
                role TEXT NOT NULL, content TEXT, ai_model TEXT, date TEXT, $S)""",
            """CREATE TABLE Ai_Active(
                id_ai_active INTEGER PRIMARY KEY AUTOINCREMENT,
                id_message_ai INTEGER NOT NULL REFERENCES Message_AI(id_message_ai),
                tool_name TEXT, arguments TEXT, result TEXT, error_message TEXT,
                target_id INTEGER, target_table TEXT, status TEXT, date TEXT, $S)"""
        )

        val INDEXES = listOf(
            "CREATE INDEX ix_sp ON Store_Product(id_branch, id_product)",
            "CREATE INDEX ix_se ON Store_Equipment(id_branch, id_equipment)",
            "CREATE INDEX ix_bill ON Bill(id_branch, date)",
            "CREATE INDEX ix_basket ON Basket(id_bill)",
            "CREATE INDEX ix_hsp ON History_Store_Product(id_branch, id_product, date)",
            "CREATE INDEX ix_hse ON History_Store_Equipment(id_branch, id_equipment, date)",
            "CREATE INDEX ix_msg ON Message_AI(id_message_box)"
        )

        /** ตาราง -> ชื่อ primary key (ใช้ตอนซิงค์) เรียงตามลำดับ foreign key */
        val SYNC_TABLES = linkedMapOf(
            "User" to "id_user", "Business" to "id_business", "Branch" to "id_branch",
            "Employee" to "id_employee", "Product" to "id_product", "Equipment" to "id_equipment",
            "Promotion" to "id_promotions", "Store_Product" to "id_store_product",
            "Store_Equipment" to "id_store_equipment", "History_Business" to "id_history_business",
            "History_Edit_Product" to "id_edit_product", "History_Edit_Equipment" to "id_edit_equipment",
            "History_Branch" to "id_history_branch", "History_Employee" to "id_history_employee",
            "History_Store_Product" to "id_history_store_product",
            "History_Store_Equipment" to "id_history_store_equipment",
            "Bill" to "id_bill", "Basket" to "id_basket", "Message_Box" to "id_message_box",
            "Message_AI" to "id_message_ai", "Ai_Active" to "id_ai_active"
        )
    }
}

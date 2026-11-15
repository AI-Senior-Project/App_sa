package com.example.sa_app.sync

import android.content.Context
import com.example.sa_app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * ซิงค์ข้อมูล offline → online
 *
 * สัญญา API ที่เซิร์ฟเวอร์ต้องรองรับ (ทำฝั่ง PHP/Node ก็ได้):
 *   POST {baseUrl}/sync/push   body: { "device": "...", "tables": { "Bill": [ {...} ], ... } }
 *        ตอบ 2xx = รับแล้ว (ใช้ INSERT ... ON DUPLICATE KEY UPDATE ตาม primary key)
 *   GET  {baseUrl}/sync/pull?since=YYYY-MM-DD HH:MM:SS
 *        ตอบ { "server_time": "...", "tables": { "Product": [ {...} ], ... } }
 *        (แถวที่ updated_at / date > since, รวมแถวที่ deleted_at ไม่ว่าง)
 */
class SyncManager(context: Context, private val repo: Repo) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(v) = prefs.edit().putString("base_url", v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    val lastSync: String get() = prefs.getString("last_pull", "") ?: ""

    suspend fun syncNow(): String = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("http")) { "กรุณาตั้งค่าที่อยู่เซิร์ฟเวอร์ก่อน" }
        // 1) push
        val pending = repo.collectPending()
        var pushed = 0
        if (pending.length() > 0) {
            val body = JSONObject().put("device", android.os.Build.MODEL).put("tables", pending)
            http("POST", "$baseUrl/sync/push", body.toString())
            repo.markSynced(pending)
            pending.keys().forEach { pushed += pending.getJSONArray(it).length() }
        }
        // 2) pull
        val since = URLEncoder.encode(lastSync, "UTF-8")
        val res = JSONObject(http("GET", "$baseUrl/sync/pull?since=$since", null))
        var pulled = 0
        res.optJSONObject("tables")?.let { t ->
            repo.applyPulled(t)
            t.keys().forEach { pulled += t.getJSONArray(it).length() }
        }
        prefs.edit().putString("last_pull", res.optString("server_time", lastSync)).apply()
        "ส่งขึ้น $pushed แถว • รับลง $pulled แถว"
    }

    private fun http(method: String, url: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("เซิร์ฟเวอร์ตอบ $code: ${text.take(200)}")
            return text
        } finally { c.disconnect() }
    }
}

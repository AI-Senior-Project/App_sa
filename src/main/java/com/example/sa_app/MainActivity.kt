package com.example.sa_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.sa_app.ai.ShopAssistant
import com.example.sa_app.data.Repo
import com.example.sa_app.sync.SyncManager
import com.example.sa_app.ui.ShopApp
import com.example.sa_app.ui.theme.Sa_appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Graph.repo(applicationContext)
        val ai = ShopAssistant(repo)
        val sync = SyncManager(applicationContext, repo)
        // แอปรวม: เข้าสู่ระบบ → หน้าหลัก / ขาย / คลัง / ผู้ช่วย AI / เพิ่มเติม (พนักงาน, สาขา ฯลฯ)
        setContent { ShopApp(repo, ai, sync) }
    }
}

/** เก็บ Repo (ฐานข้อมูล) ไว้ตัวเดียวทั้งแอป */
object Graph {
    @Volatile private var instance: Repo? = null
    fun repo(ctx: android.content.Context): Repo =
        instance ?: synchronized(this) { instance ?: Repo(ctx).also { instance = it } }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Sa_appTheme { Greeting("Android") }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sa_app.data.Repo
import com.example.sa_app.data.Session
import com.example.sa_app.ui.Field
import com.example.sa_app.ui.Jade
import com.example.sa_app.ui.JadeDeep
import com.example.sa_app.ui.Saffron
import com.example.sa_app.ui.io
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(repo: Repo, onLogin: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { register = !withContext(Dispatchers.IO) { repo.hasOwner() } }

    var login by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var biz by remember { mutableStateOf("") }
    var brCode by remember { mutableStateOf("B001") }
    var brName by remember { mutableStateOf("สาขาหลัก") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(JadeDeep, Jade, MaterialTheme.colorScheme.background), endY = 1400f))
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Spacer(Modifier.height(36.dp))
            Text("ร้านฉัน", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("ขายหน้าร้าน คุมสต็อก และถามยอดขายกับผู้ช่วย AI ได้ แม้ไม่มีเน็ต",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))

            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp, shadowElevation = 6.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (register) {
                        null -> Box(Modifier.fillMaxWidth().height(120.dp)) { CircularProgressIndicator(Modifier.padding(40.dp)) }
                        true -> {
                            Text("เปิดร้านใหม่", style = MaterialTheme.typography.titleLarge)
                            Text("สร้างบัญชีเจ้าของร้าน ธุรกิจ และสาขาแรก", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("ชื่อ", first, { first = it }, Modifier.weight(1f))
                                Field("นามสกุล", last, { last = it }, Modifier.weight(1f))
                            }
                            Field("ชื่อร้าน / ธุรกิจ", biz, { biz = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("รหัสสาขา", brCode, { brCode = it }, Modifier.weight(0.8f))
                                Field("ชื่อสาขา", brName, { brName = it }, Modifier.weight(1.2f))
                            }
                            Field("ชื่อผู้ใช้", login, { login = it.trim() }, leading = Icons.Filled.Person)
                            Field("รหัสผ่าน (อย่างน้อย 6 ตัว)", pw, { pw = it }, password = true, leading = Icons.Filled.Lock)
                        }
                        false -> {
                            Text("เข้าสู่ระบบ", style = MaterialTheme.typography.titleLarge)
                            Text("เจ้าของร้านและพนักงานใช้หน้าเดียวกัน", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Field("ชื่อผู้ใช้", login, { login = it.trim() }, leading = Icons.Filled.Person)
                            Field("รหัสผ่าน", pw, { pw = it }, password = true, leading = Icons.Filled.Lock)
                        }
                    }
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    if (register != null) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                err = null
                                val isReg = register == true
                                when {
                                    login.isBlank() || pw.isBlank() -> { err = "กรอกชื่อผู้ใช้และรหัสผ่าน"; return@Button }
                                    isReg && (first.isBlank() || last.isBlank() || biz.isBlank() || brName.isBlank()) ->
                                        { err = "กรอกข้อมูลให้ครบทุกช่อง"; return@Button }
                                    isReg && pw.length < 6 -> { err = "รหัสผ่านต้องมีอย่างน้อย 6 ตัว"; return@Button }
                                }
                                busy = true
                                scope.io({
                                    if (isReg) repo.registerOwner(first.trim(), last.trim(), login, pw, biz.trim(), brCode, brName.trim())
                                    else repo.login(login, pw)
                                }, onError = { busy = false; err = it.message }) { s ->
                                    busy = false
                                    if (s == null) err = "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง" else onLogin(s)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Saffron, contentColor = Color(0xFF15211E)),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (register == true) "เปิดร้าน" else "เข้าสู่ระบบ", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { register = register != true; err = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (register == true) "มีบัญชีอยู่แล้ว? เข้าสู่ระบบ" else "เปิดร้านใหม่")
                        }
                    }
                }
            }
        }
    }
}

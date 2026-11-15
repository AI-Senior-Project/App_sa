@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.sa_app.ai.ShopAssistant
import com.example.sa_app.data.ChatMsg
import com.example.sa_app.data.Time
import com.example.sa_app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AiScreen(c: Ctx) {
    val scope = rememberCoroutineScope()
    var boxId by remember { mutableStateOf<Long?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showBoxes by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        boxId = withContext(Dispatchers.IO) {
            c.repo.chatBoxes(c.session.businessId).firstOrNull()?.id ?: c.repo.newChatBox(c.session.businessId)
        }
    }
    val messages by load(boxId, tick) { boxId?.let { c.repo.messages(it) } ?: emptyList() }
    val boxes by load(showBoxes) { if (showBoxes) c.repo.chatBoxes(c.session.businessId) else emptyList() }

    LaunchedEffect(messages?.size) {
        val n = messages?.size ?: 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    fun send(text: String) {
        val box = boxId ?: return
        if (text.isBlank() || busy) return
        busy = true; input = ""
        scope.io({ c.ai.ask(c.session, box, text.trim()) }, onError = { busy = false; c.toast(it.message ?: "ผิดพลาด") }) {
            busy = false; tick++
        }
    }

    ScreenFrame("ผู้ช่วย AI", "ตอบจากข้อมูลจริงของ ${c.session.branchName}", actions = {
        IconButton(onClick = { showBoxes = true }) { Icon(Icons.Filled.DateRange, "แชทก่อนหน้า") }
        IconButton(onClick = {
            scope.io({ c.repo.newChatBox(c.session.businessId) }) { boxId = it; tick++ }
        }) { Icon(Icons.Filled.Add, "แชทใหม่") }
    }) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (messages?.isEmpty() == true) item {
                    Column(Modifier.padding(vertical = 24.dp)) {
                        Text("ถามเรื่องร้านได้เลย", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("ผู้ช่วยดึงยอดขาย สต็อก วันหมดอายุ และโปรโมชันจากฐานข้อมูลในเครื่อง ใช้ได้แม้ออฟไลน์",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(messages.orEmpty(), key = { it.id }) { Bubble(it) }
                if (busy) item { Text("กำลังดึงข้อมูล…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ChipRow(ShopAssistant.SUGGESTIONS.map { it to it }, "", { send(it) },
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("พิมพ์คำถาม", input, { input = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.size(52.dp)) { Icon(Icons.AutoMirrored.Filled.Send, "ส่ง") }
            }
        }
    }

    if (showBoxes) AlertDialog(
        onDismissRequest = { showBoxes = false }, title = { Text("แชทก่อนหน้า") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(boxes.orEmpty(), key = { it.id }) { b ->
                    RowItem(b.preview, Time.short(b.date)) { boxId = b.id; showBoxes = false }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showBoxes = false }) { Text("ปิด") } }
    )
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Box(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(m.content, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        if (!mine && m.tools.isNotEmpty()) Text(
            "ใช้ข้อมูล: " + m.tools.joinToString(", "), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 2.dp)
        )
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sa_app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sa_app.ai.ShopAssistant
import com.example.sa_app.data.CartLine
import com.example.sa_app.data.Kind
import com.example.sa_app.data.Repo
import com.example.sa_app.data.Session
import com.example.sa_app.sync.SyncManager
import com.example.sa_app.ui.screens.*
import kotlinx.coroutines.launch

/** ปลายทางของการนำทาง */
sealed interface Dest {
    data object Home : Dest
    data object Pos : Dest
    data object Stock : Dest
    data object Ai : Dest
    data object More : Dest
    data object Products : Dest
    data class ProductEdit(val id: Long) : Dest
    data object Equipments : Dest
    data class EquipmentEdit(val id: Long) : Dest
    data class StockDetail(val kind: Kind, val id: Long) : Dest
    data object Branches : Dest
    data object Employees : Dest
    data class EmployeeEdit(val id: Long) : Dest
    data object Promotions : Dest
    data class PromotionEdit(val id: Long) : Dest
    data object Bills : Dest
    data class BillDetail(val id: String) : Dest
    data object BusinessInfo : Dest
    data object Sync : Dest
}

/** สิ่งที่ทุกหน้าจอใช้ร่วมกัน */
class Ctx(
    val repo: Repo, val ai: ShopAssistant, val sync: SyncManager, val session: Session,
    val go: (Dest) -> Unit, val back: () -> Unit, val toast: (String) -> Unit,
    val setSession: (Session) -> Unit, val logout: () -> Unit
)

private data class Tab(val dest: Dest, val label: String, val icon: ImageVector)

@Composable
fun ShopApp(repo: Repo, ai: ShopAssistant, sync: SyncManager) {
    var session by remember { mutableStateOf<Session?>(null) }
    ShopTheme {
        val s = session
        if (s == null) AuthScreen(repo) { session = it }
        else MainShell(repo, ai, sync, s, onSession = { session = it }, onLogout = { session = null })
    }
}

@Composable
private fun MainShell(
    repo: Repo, ai: ShopAssistant, sync: SyncManager, session: Session,
    onSession: (Session) -> Unit, onLogout: () -> Unit
) {
    val tabs = listOf(
        Tab(Dest.Home, "หน้าหลัก", Icons.Filled.Home),
        Tab(Dest.Pos, "ขาย", Icons.Filled.ShoppingCart),
        Tab(Dest.Stock, "คลัง", Icons.AutoMirrored.Filled.List),
        Tab(Dest.Ai, "ผู้ช่วย AI", Icons.Filled.Star),
        Tab(Dest.More, "เพิ่มเติม", Icons.Filled.Menu)
    )
    var tab by remember { mutableStateOf<Dest>(Dest.Home) }
    val stack = remember { mutableStateListOf<Dest>() }
    val cart = remember { mutableStateListOf<CartLine>() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = stack.lastOrNull() ?: tab

    BackHandler(enabled = stack.isNotEmpty() || tab != Dest.Home) {
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) } else { tab = Dest.Home }
    }

    val ctx = Ctx(
        repo, ai, sync, session,
        go = { d -> if (tabs.any { it.dest == d }) { stack.clear(); tab = d } else stack.add(d) },
        back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
        toast = { msg -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } },
        setSession = onSession, logout = { cart.clear(); onLogout() }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (stack.isEmpty()) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.dest, onClick = { tab = t.dest },
                        icon = {
                            if (t.dest == Dest.Pos && cart.isNotEmpty())
                                BadgedBox(badge = { Badge { Text("${cart.size}") } }) { Icon(t.icon, t.label) }
                            else Icon(t.icon, t.label)
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (val d = current) {
                Dest.Home -> HomeScreen(ctx)
                Dest.Pos -> PosScreen(ctx, cart)
                Dest.Stock -> StockScreen(ctx)
                Dest.Ai -> AiScreen(ctx)
                Dest.More -> MoreScreen(ctx)
                Dest.Products -> ProductsScreen(ctx)
                is Dest.ProductEdit -> ProductEditScreen(ctx, d.id)
                Dest.Equipments -> EquipmentsScreen(ctx)
                is Dest.EquipmentEdit -> EquipmentEditScreen(ctx, d.id)
                is Dest.StockDetail -> StockDetailScreen(ctx, d.kind, d.id)
                Dest.Branches -> BranchesScreen(ctx)
                Dest.Employees -> EmployeesScreen(ctx)
                is Dest.EmployeeEdit -> EmployeeEditScreen(ctx, d.id)
                Dest.Promotions -> PromotionsScreen(ctx)
                is Dest.PromotionEdit -> PromotionEditScreen(ctx, d.id)
                Dest.Bills -> BillsScreen(ctx)
                is Dest.BillDetail -> BillDetailScreen(ctx, d.id)
                Dest.BusinessInfo -> BusinessScreen(ctx)
                Dest.Sync -> SyncScreen(ctx)
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.sa_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- Theme: หยก (jade) + หญ้าฝรั่น (saffron) ----------
val Jade = Color(0xFF0E6B5A)
val JadeDeep = Color(0xFF09473C)
val Saffron = Color(0xFFF2A007)
val Ink = Color(0xFF15211E)
val Mist = Color(0xFFF2F5F4)
val Brick = Color(0xFFB4361C)

private val LightColors = lightColorScheme(
    primary = Jade, onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EDE3), onPrimaryContainer = JadeDeep,
    secondary = Saffron, onSecondary = Ink,
    secondaryContainer = Color(0xFFFFE6B3), onSecondaryContainer = Color(0xFF3D2800),
    tertiary = Color(0xFF4A5C9A), tertiaryContainer = Color(0xFFDDE3FF), onTertiaryContainer = Color(0xFF14204F),
    background = Mist, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE2E9E6), onSurfaceVariant = Color(0xFF4A5753),
    outline = Color(0xFF7C8985), outlineVariant = Color(0xFFD3DCD8),
    error = Brick, errorContainer = Color(0xFFFFDAD2), onErrorContainer = Color(0xFF3F0A00)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6C0), onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0B5245), onPrimaryContainer = Color(0xFFC9EDE3),
    secondary = Saffron, onSecondary = Ink,
    secondaryContainer = Color(0xFF5C3F00), onSecondaryContainer = Color(0xFFFFE6B3),
    tertiary = Color(0xFFB8C4FF), tertiaryContainer = Color(0xFF323F75), onTertiaryContainer = Color(0xFFDDE3FF),
    background = Color(0xFF0F1714), onBackground = Color(0xFFDCE5E1),
    surface = Color(0xFF16201D), onSurface = Color(0xFFDCE5E1),
    surfaceVariant = Color(0xFF26322E), onSurfaceVariant = Color(0xFFB9C7C2),
    outline = Color(0xFF84918D), outlineVariant = Color(0xFF34413D),
    error = Color(0xFFFFB4A3), errorContainer = Color(0xFF7A2410), onErrorContainer = Color(0xFFFFDAD2)
)

@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val type = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = type, content = content)
}

// ---------- coroutine helpers ----------
@Composable
fun <T> load(vararg keys: Any?, block: suspend () -> T): State<T?> =
    produceState<T?>(initialValue = null, *keys) { value = withContext(Dispatchers.IO) { block() } }

fun <T> CoroutineScope.io(work: () -> T, onError: (Throwable) -> Unit = {}, done: (T) -> Unit = {}) = launch {
    try { done(withContext(Dispatchers.IO) { work() }) } catch (e: Throwable) { onError(e) }
}

// ---------- Layout ----------
@Composable
fun ScreenFrame(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "ย้อนกลับ") }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (fab != null) Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) { fab() }
        }
    }
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false, password: Boolean = false, singleLine: Boolean = true,
    placeholder: String? = null, leading: ImageVector? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leading?.let { { Icon(it, null) } },
        keyboardOptions = KeyboardOptions(keyboardType = when {
            password -> KeyboardType.Password; number -> KeyboardType.Decimal; else -> KeyboardType.Text
        }),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}

@Composable
fun Initials(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(modifier.size(42.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(text.trim().take(2).ifEmpty { "?" }, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun RowItem(
    title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
fun Section(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun Empty(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(12.dp)); FilledTonalButton(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun Pill(text: String, warn: Boolean = false) {
    val bg = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val fg = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content
    )
}

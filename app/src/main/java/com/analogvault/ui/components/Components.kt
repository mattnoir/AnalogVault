package com.analogvault.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.analogvault.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun formatDate(iso: String): String {
    if (iso.isBlank()) return "—"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(iso) ?: return iso
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
    } catch (e: Exception) { iso }
}

fun expiryStatus(iso: String): Triple<String, Color, Boolean> {
    if (iso.isBlank()) return Triple("", Color.Transparent, false)
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(iso) ?: return Triple(iso, TextSecondary, false)
        val days = ((date.time - Date().time) / 86400000).toInt()
        when {
            days < 0  -> Triple("Expired", RedErr, true)
            days < 90 -> Triple("Exp. in ${days}d", OrangeWarn, false)
            else      -> Triple(formatDate(iso), GreenOk, false)
        }
    } catch (e: Exception) { Triple(iso, TextSecondary, false) }
}

// ─── Amber chip / tag ─────────────────────────────────────────────────────────

@Composable
fun VaultTag(
    text: String,
    color: Color = Border,
    textColor: Color = TextSecondary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = textColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─── Section title ────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(text: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text, color = Amber, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (badge != null) Text(badge, color = TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─── Card ─────────────────────────────────────────────────────────────────────

@Composable
fun VaultCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        content = content
    )
}

// ─── OutlinedTextField styled ─────────────────────────────────────────────────

@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextTertiary, fontSize = 11.sp) },
        placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder, color = TextTertiary, fontSize = 12.sp) }} else null,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = Border,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Amber,
            focusedLabelColor = Amber,
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ─── Dropdown ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = TextTertiary, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Amber, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                focusedLabelColor = Amber
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = Bg3) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = TextPrimary, fontSize = 13.sp) },
                    onClick = { onSelected(opt); expanded = false },
                    colors = MenuDefaults.itemColors(textColor = TextPrimary)
                )
            }
        }
    }
}

// ─── AutoComplete field ───────────────────────────────────────────────────────

@Composable
fun AutoCompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    // Track the last value the user confirmed from the list — hide suggestions after pick
    var lastConfirmed by remember { mutableStateOf("") }
    val filtered = remember(value, lastConfirmed, suggestions) {
        if (value.isBlank() || value == lastConfirmed) emptyList()
        else suggestions.filter { it.contains(value, ignoreCase = true) }.take(5)
    }
    Column(modifier = modifier) {
        VaultTextField(
            value = value,
            onValueChange = { lastConfirmed = ""; onValueChange(it) },
            label = label,
            placeholder = placeholder
        )
        if (filtered.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Bg3)
                    .border(1.dp, Border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            ) {
                filtered.forEach { s ->
                    Text(
                        s, color = TextPrimary, fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { lastConfirmed = s; onValueChange(s) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ─── Modal bottom sheet wrapper ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg2,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
        ) {
            Text(title, color = AmberBright, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
            content()
        }
    }
}

// ─── Confirm dialog ───────────────────────────────────────────────────────────

@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg3,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Confirm", color = AmberBright) },
        text = { Text(message, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = RedErr) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ─── Amber button ─────────────────────────────────────────────────────────────

@Composable
fun VaultButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    danger: Boolean = false,
    small: Boolean = false
) {
    val bg = when {
        danger -> RedErr.copy(alpha = 0.15f)
        ghost  -> Bg3
        else   -> AmberDark
    }
    val fg = when {
        danger -> RedErr
        ghost  -> TextSecondary
        else   -> TextPrimary
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(if (small) 34.dp else 44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = if (small) 10.dp else 14.dp, vertical = 0.dp)
    ) {
        Text(text, fontSize = if (small) 11.sp else 13.sp)
    }
}

// ─── Progress bar ─────────────────────────────────────────────────────────────

@Composable
fun VaultProgressBar(fraction: Float, color: Color = Amber) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Bg4)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextTertiary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

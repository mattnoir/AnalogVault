package com.analogvault.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

fun expiryStatus(rawDate: String): Triple<String, Color, Boolean> {
    if (rawDate.isBlank()) return Triple("", Color.Transparent, false)
    // Support both "yyyy-MM" (month-only) and "yyyy-MM-dd" formats
    val isMonthOnly = rawDate.length == 7
    val dateStr = if (isMonthOnly) "$rawDate-01" else rawDate
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return Triple(rawDate, TextSecondary, false)
        val days = ((date.time - Date().time) / 86400000).toInt()
        val displayLabel = if (isMonthOnly) {
            SimpleDateFormat("MMM yyyy", Locale.US).format(date)
        } else {
            formatDate(dateStr)
        }
        when {
            days < 0  -> Triple("Expired", RedErr, true)
            days < 90 -> Triple("Exp. in ${days}d", OrangeWarn, false)
            else      -> Triple(displayLabel, GreenOk, false)
        }
    } catch (e: Exception) { Triple(rawDate, TextSecondary, false) }
}

// ─── Amber chip / tag ─────────────────────────────────────────────────────────

@Composable
fun VaultTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Border,
    textColor: Color = TextSecondary
) {
    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(color = color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = textColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─── Tag row (wrapping) ───────────────────────────────────────────────────────
// Use TagRow instead of a plain Row when displaying VaultTags — it wraps onto
// the next line automatically, preventing the overflow-into-tall-rectangle glitch.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

// ─── Full Date Picker (day + month + year, optional time) ─────────────────────

// Compact spinner: arrow up/down for quick steps, tap the value to jump directly —
// either via a dropdown of choices (pickerOptions) or a custom action (onValueClick,
// e.g. the analog clock). Shared by FullDatePickerDialog and MonthYearPickerDialog.
@Composable
fun SpinnerField(
    label: String, value: String,
    onDec: () -> Unit, onInc: () -> Unit,
    modifier: Modifier = Modifier,
    pickerOptions: List<Pair<String, Int>>? = null,
    onPick: ((Int) -> Unit)? = null,
    onValueClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val tappable = onValueClick != null || (pickerOptions != null && onPick != null)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        IconButton(onClick = onInc, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ExpandLess, null, tint = Amber, modifier = Modifier.size(20.dp))
        }
        Box {
            Box(
                Modifier.background(if (tappable) Bg3 else Bg4, RoundedCornerShape(6.dp))
                    .border(1.dp, if (tappable) Amber.copy(alpha = 0.45f) else Border, RoundedCornerShape(6.dp))
                    .then(if (tappable) Modifier.clickable {
                        if (onValueClick != null) onValueClick() else expanded = true
                    } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(value, color = TextPrimary, fontSize = 16.sp)
            }
            if (pickerOptions != null && onPick != null) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = Bg3,
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    pickerOptions.forEach { (lbl, v) ->
                        DropdownMenuItem(
                            text = { Text(lbl, color = if (lbl == value) Amber else TextPrimary, fontSize = 14.sp) },
                            onClick = { onPick(v); expanded = false }
                        )
                    }
                }
            }
        }
        IconButton(onClick = onDec, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ExpandMore, null, tint = Amber, modifier = Modifier.size(20.dp))
        }
    }
}

// Classic round analog clock for picking the time — opened by tapping the time value.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockTimeDialog(
    hour: Int, minute: Int,
    onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg3,
        title = { Text("Pick Time", color = AmberBright) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Bg4,
                        clockDialSelectedContentColor = Bg,
                        clockDialUnselectedContentColor = TextPrimary,
                        selectorColor = Amber,
                        timeSelectorSelectedContainerColor = AmberDark,
                        timeSelectorSelectedContentColor = AmberBright,
                        timeSelectorUnselectedContainerColor = Bg4,
                        timeSelectorUnselectedContentColor = TextSecondary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set", color = Amber) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullDatePickerDialog(
    initialDate: String,
    includeTime: Boolean = false,
    yearRange: IntRange? = null,   // null = wide default (1950 .. now+30); shot log passes a narrow one
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parsed = remember(initialDate) {
        try {
            val fmt = if (includeTime && initialDate.length >= 16) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd"
            java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                .parse(initialDate.take(16)) ?: java.util.Date()
        } catch (_: Exception) { java.util.Date() }
    }
    val cal = java.util.Calendar.getInstance().also { it.time = parsed }
    var selDay    by remember { mutableIntStateOf(cal.get(java.util.Calendar.DAY_OF_MONTH)) }
    var selMonth  by remember { mutableIntStateOf(cal.get(java.util.Calendar.MONTH) + 1) }
    var selYear   by remember { mutableIntStateOf(cal.get(java.util.Calendar.YEAR)) }
    var selHour   by remember { mutableIntStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY)) }
    var selMinute by remember { mutableIntStateOf(cal.get(java.util.Calendar.MINUTE)) }

    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val daysInMonth = remember(selYear, selMonth) {
        java.util.Calendar.getInstance().also { c -> c.set(selYear, selMonth - 1, 1) }
            .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }
    // Clamp day if month shrinks
    if (selDay > daysInMonth) selDay = daysInMonth

    // Tap-to-expand options for each field, and the analog clock for time.
    val nowYear      = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val years        = yearRange ?: (1950..(nowYear + 30))
    val dayOptions   = (1..daysInMonth).map { "%02d".format(it) to it }
    val monthOptions = months.mapIndexed { i, m -> m to (i + 1) }
    val yearOptions  = remember(years) { years.map { it.toString() to it } }
    var showClock    by remember { mutableStateOf(false) }

    if (showClock) {
        ClockTimeDialog(
            hour = selHour, minute = selMinute,
            onConfirm = { h, m -> selHour = h; selMinute = m; showClock = false },
            onDismiss = { showClock = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg3,
        title = { Text(if (includeTime) "Date & Time" else "Select Date", color = AmberBright) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Date row: Day | Month | Year
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpinnerField(
                        label = "Day",
                        value = "%02d".format(selDay),
                        onInc = { selDay = if (selDay >= daysInMonth) 1 else selDay + 1 },
                        onDec = { selDay = if (selDay <= 1) daysInMonth else selDay - 1 },
                        pickerOptions = dayOptions, onPick = { selDay = it },
                        modifier = Modifier.weight(1f)
                    )
                    SpinnerField(
                        label = "Month",
                        value = months[selMonth - 1],
                        onInc = { selMonth = if (selMonth >= 12) 1 else selMonth + 1 },
                        onDec = { selMonth = if (selMonth <= 1) 12 else selMonth - 1 },
                        pickerOptions = monthOptions, onPick = { selMonth = it },
                        modifier = Modifier.weight(1.3f)
                    )
                    SpinnerField(
                        label = "Year",
                        value = selYear.toString(),
                        onInc = { if (selYear < years.last) selYear++ },
                        onDec = { if (selYear > years.first) selYear-- },
                        pickerOptions = yearOptions, onPick = { selYear = it },
                        modifier = Modifier.weight(1.3f)
                    )
                }
                if (includeTime) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(12.dp))
                    // Time row: Hour | : | Minute — tap a value to open the round clock
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpinnerField(
                            label = "Hour",
                            value = "%02d".format(selHour),
                            onInc = { selHour = (selHour + 1) % 24 },
                            onDec = { selHour = (selHour - 1 + 24) % 24 },
                            onValueClick = { showClock = true },
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", color = Amber, fontSize = 24.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 32.dp))
                        SpinnerField(
                            label = "Min",
                            value = "%02d".format(selMinute),
                            onInc = { selMinute = (selMinute + 5) % 60 },
                            onDec = { selMinute = (selMinute - 5 + 60) % 60 },
                            onValueClick = { showClock = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Tap the time to open the clock", color = TextTertiary, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = if (includeTime)
                    "%04d-%02d-%02d %02d:%02d".format(selYear, selMonth, selDay, selHour, selMinute)
                else
                    "%04d-%02d-%02d".format(selYear, selMonth, selDay)
                onConfirm(result)
            }) { Text("Set", color = Amber) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
// ─── Section card ─────────────────────────────────────────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(title, color = Amber, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// ─── Section title ─────────────────────────────────────────────────────────────

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
    val bgColor = Bg2
    Column(
        modifier = modifier
            .fillMaxWidth()
            // graphicsLayer {} (no params) promotes the card to its own RenderNode.
            // During a scroll fling HWUI repositions RenderNodes on the GPU rather
            // than re-issuing every draw command for every visible card on every
            // frame.  This is especially impactful on Mali where draw-call submission
            // is more expensive than on Adreno.  Using empty {} avoids allocating an
            // off-screen buffer (that only happens when alpha/clip/renderEffect are set).
            .graphicsLayer {}
            .drawBehind {
                // drawBehind avoids clip() save/restore — much faster on Mali GPU
                drawRoundRect(
                    color = bgColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                )
                // Border drawn here (not via .border()) to stay inside the same
                // RenderNode and avoid an extra save/restore layer.
                drawRoundRect(
                    color = Border,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
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
    placeholder: String = "",
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
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

// ─── Quantity stepper ─────────────────────────────────────────────────────────
// Numeric field with −/+ arrows; the field itself stays directly editable.

@Composable
fun QuantityStepper(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val v = (value.toIntOrNull() ?: 0) - 1
                if (v >= 0) onValueChange(v.toString())
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Remove, "Decrease", tint = Amber, modifier = Modifier.size(18.dp))
        }
        VaultTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit)) },
            label = label,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onValueChange(((value.toIntOrNull() ?: 0) + 1).toString()) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Add, "Increase", tint = Amber, modifier = Modifier.size(18.dp))
        }
    }
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
    // confirmValueChange = { it != Hidden } prevents the sheet from closing via swipe —
    // the state transition to Hidden is rejected so the sheet snaps back to Expanded.
    // Dismiss is still possible by tapping the scrim or the explicit ✕ button.
    // This avoids accidental data loss on form sheets where a stray downward gesture
    // would silently discard unsaved input.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange    = { it != SheetValue.Hidden }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Bg2,
        tonalElevation   = 0.dp,
        // Remove the default drag handle — we show our own title row with X button
        dragHandle       = null
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
        ) {
            // Title row with explicit close button
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(title, color = AmberBright, fontSize = 18.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close",
                        tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
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
    small: Boolean = false,
    enabled: Boolean = true
) {
    val bg = when {
        !enabled -> Bg3
        danger -> RedErr.copy(alpha = 0.15f)
        ghost  -> Bg3
        else   -> AmberDark
    }
    val fg = when {
        !enabled -> TextTertiary
        danger -> RedErr
        ghost  -> TextSecondary
        else   -> TextPrimary
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(if (small) 34.dp else 44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = Bg3, disabledContentColor = TextTertiary
        ),
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
            .drawBehind {
                drawRoundRect(color = Bg4, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .drawBehind {
                    drawRoundRect(color = color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                }
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

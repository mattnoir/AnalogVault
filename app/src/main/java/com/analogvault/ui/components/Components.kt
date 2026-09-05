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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.analogvault.ui.theme.onAccent
import com.analogvault.ui.theme.*
import com.analogvault.ui.theme.FilmTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.analogvault.ui.film.DyeIcon
import com.analogvault.ui.film.FilmIcons
import com.analogvault.ui.film.FilmIconSpec
import androidx.compose.ui.unit.TextUnit

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun formatDate(iso: String): String {
    if (iso.isBlank()) return "—"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(iso) ?: return iso
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
    } catch (e: Exception) { iso }
}

/**
 * How close a stock is to its expiry date.
 *
 * Returns a level rather than a colour. Colour is a property of the theme and
 * this is pure date arithmetic — mixing them meant a non-composable function
 * reaching for a palette, which is also what stopped it following the safelight
 * swap. Callers map the level with [expiryColor].
 */
enum class ExpiryLevel { NONE, OK, SOON, EXPIRED }

/** Label, level, and whether it is already past. */
fun expiryStatus(rawDate: String): Triple<String, ExpiryLevel, Boolean> {
    if (rawDate.isBlank()) return Triple("", ExpiryLevel.NONE, false)
    // Support both "yyyy-MM" (month-only) and "yyyy-MM-dd" formats
    val isMonthOnly = rawDate.length == 7
    val dateStr = if (isMonthOnly) "$rawDate-01" else rawDate
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return Triple(rawDate, ExpiryLevel.NONE, false)
        val days = ((date.time - Date().time) / 86400000).toInt()
        val displayLabel = if (isMonthOnly) {
            SimpleDateFormat("MMM yyyy", Locale.US).format(date)
        } else {
            formatDate(dateStr)
        }
        when {
            days < 0  -> Triple("Expired", ExpiryLevel.EXPIRED, true)
            days < 90 -> Triple("Exp. in ${days}d", ExpiryLevel.SOON, false)
            else      -> Triple(displayLabel, ExpiryLevel.OK, false)
        }
    } catch (e: Exception) { Triple(rawDate, ExpiryLevel.NONE, false) }
}

/** Mask for gone, yellow for a decision to make, dim for fine. */
@Composable
fun expiryColor(level: ExpiryLevel): Color = when (level) {
    ExpiryLevel.EXPIRED -> FilmTheme.colors.mask
    ExpiryLevel.SOON    -> FilmTheme.colors.yellow
    else                -> FilmTheme.colors.dim
}

// ─── Data chip ────────────────────────────────────────────────────────────────

/**
 * A hairline data chip.
 *
 * Same name and signature the screens already call, drawn in the Dye Layer
 * language: one border, no radius, mono caps, no fill. [textColor] is the
 * semantic colour — it now drives the border as well as the text, since a chip
 * with a filled background and a differently coloured label was two signals for
 * one fact.
 */
@Composable
fun VaultTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    /** Drawn before the label, in the tag's own colour. See [VaultButton]. */
    icon: FilmIconSpec? = null,
) {
    val colors = FilmTheme.colors
    val accent = when {
        textColor != Color.Unspecified -> textColor
        color != Color.Unspecified     -> color
        else                           -> colors.dim
    }
    Row(
        modifier = modifier
            .border(1.dp, accent)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            DyeIcon(icon, null, size = 12.dp, tint = accent, accent = accent)
        }
        Text(text.uppercase(), style = FilmTheme.type.data, color = accent, maxLines = 1)
    }
}

/**
 * An icon and a line of text, for the places that used to prefix a string with
 * an emoji: a shot's location, the process a roll was developed in, a warning.
 *
 * The icon takes the text's colour rather than its own dye accent — at 12sp
 * these read as one phrase, and a second colour inside a phrase is noise.
 */
@Composable
fun IconLabel(
    icon: FilmIconSpec,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DyeIcon(icon, null, size = 13.dp, tint = color, accent = color)
        Text(text, color = color, fontSize = fontSize)
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
        Text(label, color = FilmTheme.colors.dim, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        IconButton(onClick = onInc, modifier = Modifier.size(32.dp)) {
            DyeIcon(FilmIcons.ChevronUp, null, size = 20.dp, tint = FilmTheme.colors.cyan)
        }
        Box {
            Box(
                Modifier.background(if (tappable) FilmTheme.colors.filmRaised else FilmTheme.colors.filmRaised)
                    .border(1.dp, if (tappable) FilmTheme.colors.cyan.copy(alpha = 0.45f) else FilmTheme.colors.edge)
                    .then(if (tappable) Modifier.clickable {
                        if (onValueClick != null) onValueClick() else expanded = true
                    } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(value, color = FilmTheme.colors.halide, fontSize = 16.sp)
            }
            if (pickerOptions != null && onPick != null) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = FilmTheme.colors.filmRaised,
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    pickerOptions.forEach { (lbl, v) ->
                        DropdownMenuItem(
                            text = { Text(lbl, color = if (lbl == value) FilmTheme.colors.cyan else FilmTheme.colors.halide, fontSize = 14.sp) },
                            onClick = { onPick(v); expanded = false }
                        )
                    }
                }
            }
        }
        IconButton(onClick = onDec, modifier = Modifier.size(32.dp)) {
            DyeIcon(FilmIcons.ChevronDown, null, size = 20.dp, tint = FilmTheme.colors.cyan)
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
        containerColor = FilmTheme.colors.filmRaised,
        title = { Text("Pick Time", color = FilmTheme.colors.yellow) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = FilmTheme.colors.filmRaised,
                        clockDialSelectedContentColor = FilmTheme.colors.void,
                        clockDialUnselectedContentColor = FilmTheme.colors.halide,
                        selectorColor = FilmTheme.colors.cyan,
                        timeSelectorSelectedContainerColor = FilmTheme.colors.violet,
                        timeSelectorSelectedContentColor = FilmTheme.colors.yellow,
                        timeSelectorUnselectedContainerColor = FilmTheme.colors.filmRaised,
                        timeSelectorUnselectedContentColor = FilmTheme.colors.dim
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set", color = FilmTheme.colors.cyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FilmTheme.colors.dim) } }
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
        containerColor = FilmTheme.colors.filmRaised,
        title = { Text(if (includeTime) "Date & Time" else "Select Date", color = FilmTheme.colors.yellow) },
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
                    HorizontalDivider(color = FilmTheme.colors.edge)
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
                        Text(":", color = FilmTheme.colors.cyan, fontSize = 24.sp,
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
                    Text("Tap the time to open the clock", color = FilmTheme.colors.dim, fontSize = 10.sp)
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
            }) { Text("Set", color = FilmTheme.colors.cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FilmTheme.colors.dim) }
        }
    )
}
// ─── Section card ─────────────────────────────────────────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = FilmTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.film)
            .border(1.dp, colors.edge)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = FilmTheme.type.eyebrow, color = colors.dim)
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(color = colors.edge)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

// ─── Section title ─────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(text: String, badge: String? = null) {
    val colors = FilmTheme.colors
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        Text(text.uppercase(), style = FilmTheme.type.display.copy(fontSize = 30.sp),
            color = colors.halide)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(badge.uppercase(), style = FilmTheme.type.rebate, color = colors.dim,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

// ─── Card ─────────────────────────────────────────────────────────────────────

@Composable
fun VaultCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FilmTheme.colors
    val bgColor = colors.film
    val borderColor = colors.edge
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
                // Square, not rounded: everything in this design is a rectangle
                // except the shutter. drawBehind still avoids the clip()
                // save/restore, which is the reason this was hand-drawn.
                drawRect(color = bgColor)
                // FilmTheme.colors.edge drawn here (not via .border()) to stay inside the same
                // RenderNode and avoid an extra save/restore layer.
                drawRect(
                    color = borderColor,
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
    val colors = FilmTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        shape = RectangleShape,
        label = { Text(label.uppercase(), style = FilmTheme.type.rebate, color = colors.dim) },
        placeholder = if (placeholder.isNotBlank()) {{
            Text(placeholder, style = FilmTheme.type.data, color = colors.dead)
        }} else null,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.cyan,
            unfocusedBorderColor = colors.edge,
            focusedTextColor = colors.halide,
            unfocusedTextColor = colors.halide,
            cursorColor = colors.cyan,
            focusedLabelColor = colors.cyan,
            focusedContainerColor = colors.film,
            unfocusedContainerColor = colors.film,
            disabledContainerColor = colors.film,
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
    val colors = FilmTheme.colors
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            // The field is never taller than one line. Without this a value that
            // does not fit the column — "1/125" in the meter's shutter dropdown —
            // wraps mid-token to "1/1 / 25" instead of staying on one row.
            singleLine = true,
            // Slightly smaller than body text so the longest values these fields
            // hold — "135 (35mm)", "Color Negative (C-41)" — fit a half-width
            // column instead of being clipped by the single-line constraint.
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            shape = RectangleShape,
            label = { Text(label.uppercase(), style = FilmTheme.type.rebate, color = colors.dim, maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.cyan, unfocusedBorderColor = colors.edge,
                focusedTextColor = colors.halide, unfocusedTextColor = colors.halide,
                focusedLabelColor = colors.cyan,
                focusedContainerColor = colors.film, unfocusedContainerColor = colors.film,
                focusedTrailingIconColor = colors.cyan, unfocusedTrailingIconColor = colors.dim,
            ),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = colors.filmRaised, shape = RectangleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.edge)) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, style = FilmTheme.type.data, color = colors.halide) },
                    onClick = { onSelected(opt); expanded = false },
                    colors = MenuDefaults.itemColors(textColor = colors.halide)
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
            val colors = FilmTheme.colors
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.filmRaised)
                    .border(1.dp, colors.edge)
            ) {
                filtered.forEach { s ->
                    Text(
                        s, style = FilmTheme.type.data, color = colors.halide,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { lastConfirmed = s; onValueChange(s) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

// ─── Modal bottom sheet wrapper ───────────────────────────────────────────────

/**
 * Paint a dialog window's system bars void black with light icons.
 *
 * Sheets and dialogs live in their own window. That window inherits neither the
 * activity's `enableEdgeToEdge` treatment nor the `android:statusBarColor` /
 * `android:navigationBarColor` declared on the activity theme — it comes up with
 * the platform defaults instead, which on a light-themed device means a white
 * plate under the gesture bar and a see-through status bar that a full-height
 * sheet's content scrolls beneath. Call this from inside the sheet's content,
 * where the dialog window exists.
 */
@Composable
@Suppress("DEPRECATION") // statusBarColor/navigationBarColor: no replacement below API 35
private fun VoidSystemBars() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

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
    val colors = FilmTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = colors.film,
        shape            = RectangleShape,
        tonalElevation   = 0.dp,
        // Remove the default drag handle — we show our own title row with X button
        dragHandle       = null
    ) {
        VoidSystemBars()
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
                Text(title.uppercase(), style = FilmTheme.type.stock.copy(fontSize = 22.sp),
                    color = colors.halide)
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    DyeIcon(FilmIcons.Close, contentDescription = "Close",
                        size = 20.dp, tint = colors.dim)
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
    val colors = FilmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.filmRaised,
        shape = RectangleShape,
        titleContentColor = colors.halide,
        textContentColor = colors.dim,
        title = {
            Text("CONFIRM", style = FilmTheme.type.stock.copy(fontSize = 20.sp), color = colors.halide)
        },
        text = { Text(message, color = colors.halide) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel.uppercase(), style = FilmTheme.type.data, color = colors.mask)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", style = FilmTheme.type.data, color = colors.dim)
            }
        }
    )
}

// ─── FilmTheme.colors.cyan button ─────────────────────────────────────────────────────────────

@Composable
fun VaultButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    danger: Boolean = false,
    small: Boolean = false,
    enabled: Boolean = true,
    /**
     * Drawn before the label, in the label's own colour.
     *
     * This exists because the labels used to carry emoji — "🧪 Open Darkroom",
     * "⬆ Export Backup" — which render in the system font's colours, at the
     * system's idea of the size, and stay bright green or blue when the app
     * goes red. An icon from the set is none of those things.
     */
    icon: FilmIconSpec? = null,
) {
    // Filled magenta for the action that commits, hairline for everything else.
    // A screen where every button is filled has no primary action, which is the
    // state the amber build was in.
    val colors = FilmTheme.colors
    val accent = when {
        !enabled -> colors.dead
        danger   -> colors.mask
        ghost    -> colors.dim
        else     -> colors.magenta
    }
    val filled = enabled && !ghost && !danger
    Box(
        modifier
            .height(if (small) 36.dp else 46.dp)
            .background(if (filled) accent else colors.film)
            .border(1.dp, accent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (small) 10.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A filled button is the accent, so its label has to be legible on
        // whatever the accent currently is — see FilmColors.onAccent.
        val labelColor = if (filled) colors.onAccent(accent, preferred = colors.void) else accent
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                // One tone, not the dye accent: on a filled button the accent
                // would be the only thing not punched out of the fill.
                DyeIcon(icon, null, size = if (small) 14.dp else 16.dp,
                    tint = labelColor, accent = labelColor)
            }
            Text(
                text.uppercase(),
                style = FilmTheme.type.data.copy(fontSize = if (small) 11.sp else 13.sp),
                color = labelColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Progress bar ─────────────────────────────────────────────────────────────

@Composable
fun VaultProgressBar(fraction: Float, color: Color = Color.Unspecified) {
    val colors = FilmTheme.colors
    val fill = if (color != Color.Unspecified) color else colors.cyan
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind { drawRect(color = colors.filmRaised) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .drawBehind { drawRect(color = fill) }
        )
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

/**
 * Kept as the name every screen already calls; the drawing lives in
 * [com.analogvault.ui.film.UnexposedFrames] so the empty state and the film
 * language stay one thing.
 */
@Composable
fun EmptyState(text: String, verb: String? = null, onVerb: (() -> Unit)? = null) {
    com.analogvault.ui.film.UnexposedFrames(text = text, verb = verb, onVerb = onVerb)
}

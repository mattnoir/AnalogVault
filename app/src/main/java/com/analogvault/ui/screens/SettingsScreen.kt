@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.analogvault.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.Palette
import com.analogvault.util.EdgePrint
import com.analogvault.util.FrameNumberStyle
import com.analogvault.util.GrainStock
import com.analogvault.util.IrisStyle
import com.analogvault.ui.theme.*
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.util.Constants
import com.analogvault.ui.film.DyeIcon
import com.analogvault.ui.film.FilmIcons
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val owmKey    by vm.owmKey.collectAsState()
    val currency  by vm.currency.collectAsState()
    val isMetric  by vm.isMetric.collectAsState()
    val customIsos by vm.customIsos.collectAsState()
    val highRefresh by vm.highRefresh.collectAsState()
    val safelight by vm.safelight.collectAsState()
    val saturation by vm.saturation.collectAsState()
    val palette by vm.palette.collectAsState()
    val grain by vm.grain.collectAsState()
    val iris by vm.iris.collectAsState()
    val edgePrint by vm.edgePrint.collectAsState()
    val achRecords by vm.achievementRecords.collectAsState()
    val remindersEnabled  by vm.remindersEnabled.collectAsState()
    val remindExpiry      by vm.remindExpiry.collectAsState()
    val remindUndeveloped by vm.remindUndeveloped.collectAsState()
    val remindChemicals   by vm.remindChemicals.collectAsState()
    val achNotify         by vm.achNotify.collectAsState()
    val zoomLevels        by vm.zoomLevels.collectAsState()
    val cameraOptics      by vm.cameraOptics.collectAsState()
    val focalsUnverified  by vm.zoomFocalsUnverified.collectAsState()
    var showZoomEdit      by remember { mutableStateOf(false) }

    // Enabling reminders needs POST_NOTIFICATIONS on API 33+; the worker also
    // re-checks before posting, so a denied permission just means silence.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.saveRemindersEnabled(true) }
    // Same for achievement notifications: turning them on asks once.
    val achPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.saveAchNotify(true) }

    var owmDraft      by remember(owmKey)   { mutableStateOf(owmKey) }
    var currencyDraft by remember(currency) { mutableStateOf(currency) }
    var customIsoInput by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Settings")

        // Ordered by how often a setting is touched, not by how it is stored:
        // units and display are read at a glance, ISO is occasional, reminders
        // are set once, and the API key is pasted once in the app's lifetime —
        // so it sits at the bottom rather than greeting you at the top.

        // ── Units ────────────────────────────────────────────────────────────
        SectionCard("Units") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Currency symbol", color = FilmTheme.colors.dim, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                VaultTextField(
                    currencyDraft, { currencyDraft = it.take(3) }, "",
                    modifier = Modifier.width(60.dp)
                )
                VaultButton("Save", small = true, onClick = { vm.saveCurrency(currencyDraft) })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Measurements", color = FilmTheme.colors.dim, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Text(if (isMetric) "Metric" else "Imperial",
                    color = FilmTheme.colors.halide, fontSize = 13.sp)
                Switch(
                    checked = isMetric,
                    onCheckedChange = { vm.saveMetric(it) },
                    colors = settingsSwitchColors(),
                )
            }
        }

        // ── Display ──────────────────────────────────────────────────────────
        // How the app looks, which is a different question from what its
        // numbers are measured in.
        SectionCard("Display") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Prefer 120 Hz display", color = FilmTheme.colors.dim, fontSize = 13.sp)
                    Text("Smoother scrolling on LTPO panels; uses more battery",
                        color = FilmTheme.colors.dim, fontSize = 11.sp)
                }
                Switch(
                    checked = highRefresh,
                    onCheckedChange = { vm.saveHighRefresh(it) },
                    colors = settingsSwitchColors(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Palette", color = FilmTheme.colors.dim, fontSize = 13.sp)
            Text("Same layouts, film strips and type; only the colours change. " +
                "Safelight overrides it." +
                if (safelight) " Turn Safelight off to see the palette you pick." else "",
                color = FilmTheme.colors.dim, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            // Only what is unlocked. A secret's palette is part of the secret:
            // listing it locked would give away that there is one to find.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Palette.entries.filter { it.unlocked(achRecords) }.forEach { p ->
                    PaletteRow(p, selected = p == palette) { vm.setPalette(p) }
                }
            }

            // The rest of the look, each shown once a badge has unlocked
            // something to choose between; until then there is no choice.
            val grains = GrainStock.entries.filter { it.unlocked(achRecords) }
            if (grains.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Grain", color = FilmTheme.colors.dim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grains.forEach { g ->
                        LookOptionRow(g.label, g.blurb, selected = g == grain) { vm.setGrain(g) }
                    }
                }
            }
            val irises = IrisStyle.entries.filter { it.unlocked(achRecords) }
            if (irises.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Shutter iris", color = FilmTheme.colors.dim, fontSize = 13.sp)
                Text("The blades that close when you press the shutter button.",
                    color = FilmTheme.colors.dim, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    irises.forEach { i ->
                        LookOptionRow(i.label, i.blurb, selected = i == iris) { vm.setIris(i) }
                    }
                }
            }
            if (com.analogvault.util.EdgePrint.UNLOCK.isMet(achRecords)) {
                Spacer(Modifier.height(16.dp))
                EdgePrintSettings(edgePrint, onText = { vm.setEdgeText(it) },
                    onStyle = { vm.setEdgeFrameStyle(it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Safelight", color = FilmTheme.colors.dim, fontSize = 13.sp)
                    Text("Swaps the whole app to a red scheme for darkroom work, " +
                        "and stops every glow and animation. Long-press the shutter " +
                        "to toggle it without coming here.",
                        color = FilmTheme.colors.dim, fontSize = 11.sp)
                }
                Switch(
                    checked = safelight,
                    onCheckedChange = { vm.setSafelight(it) },
                    colors = settingsSwitchColors(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Column {
                // Null except while a drag is in flight.
                var draggedSaturation by remember { mutableStateOf<Float?>(null) }
                val shownSaturation = draggedSaturation ?: saturation
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Accent saturation", color = FilmTheme.colors.dim, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text("${(shownSaturation * 100).roundToInt()}%",
                        color = FilmTheme.colors.halide, fontSize = 13.sp)
                }
                Text("Mutes the cyan/magenta/yellow/orange/violet accents for eye comfort. " +
                    "No effect while Safelight is on.",
                    color = FilmTheme.colors.dim, fontSize = 11.sp)
                Slider(
                    // Dragging is local; the write happens when the finger
                    // lifts. Persisting on every value change wrote to Room on
                    // every frame of the drag, which showed up in logcat as a
                    // stream of SQLITE_IOERR_LOCK from the settings table.
                    value = shownSaturation,
                    onValueChange = { draggedSaturation = it },
                    onValueChangeFinished = {
                        draggedSaturation?.let { vm.setSaturation(it) }
                        draggedSaturation = null
                    },
                    valueRange = 0f..1f,
                    enabled = !safelight,
                    colors = SliderDefaults.colors(
                        thumbColor = FilmTheme.colors.cyan,
                        activeTrackColor = FilmTheme.colors.cyan,
                        // edge, not film: the track has to be visible against
                        // the card it sits on, and at low saturation the thumb
                        // was the only part of the control you could find.
                        inactiveTrackColor = FilmTheme.colors.edge,
                    ),
                )
            }
        }

        // ── ISO ──────────────────────────────────────────────────────────────
        // One section, because they are one subject: what the range is, then
        // what you can add to it. The built-in range comes first — it answers
        // "do I even need a custom value" before offering the field.
        SectionCard("ISO") {
            Text(
                "Built-in range: ${Constants.ISOS.first()}–${Constants.ISOS.last()}. " +
                "Add anything outside it below — non-standard values like 1000 or " +
                "3400 for unusual films. Custom values appear alongside the " +
                "built-in ones in every shot and film ISO picker.",
                color = FilmTheme.colors.dim, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VaultTextField(
                    customIsoInput,
                    { customIsoInput = it.filter(Char::isDigit) },
                    "Custom ISO (e.g. 1000)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                VaultButton("Add", small = true, onClick = {
                    customIsoInput.toIntOrNull()?.let { vm.addCustomIso(it) }
                    customIsoInput = ""
                })
            }
            if (customIsos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TagRow {
                    customIsos.forEach { iso ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeCustomIso(iso) },
                            label = { Text("ISO $iso", fontSize = 12.sp) },
                            trailingIcon = {
                                DyeIcon(FilmIcons.Trash, null, size = 14.dp, tint = FilmTheme.colors.dim)
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = FilmTheme.colors.filmRaised,
                                labelColor = FilmTheme.colors.halide
                            )
                        )
                    }
                }
                Text("Tap chip to remove", color = FilmTheme.colors.dim, fontSize = 11.sp)
            }
        }

        // ── Light meter ──────────────────────────────────────────────────────
        SectionCard("Light meter") {
            val equiv = cameraOptics?.equivMm
            Text(
                when {
                    focalsUnverified ->
                        "This phone doesn't report its lens data, so the meter's focal " +
                            "lengths are a guess. Set each zoom level for your phone."
                    equiv != null ->
                        "Focal presets are worked out from this phone's lens data " +
                            "(main camera ≈ ${String.format(java.util.Locale.US, "%.1f", equiv)} mm " +
                            "equivalent). Adjust any zoom level by hand if it doesn't match."
                    else ->
                        "Each zoom level's focal length is set by hand."
                },
                color = if (focalsUnverified) FilmTheme.colors.yellow else FilmTheme.colors.dim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            VaultButton("Zoom levels (${zoomLevels.size})", small = true,
                onClick = { showZoomEdit = true })
        }

        // ── Reminders ────────────────────────────────────────────────────────
        SectionCard("Reminders") {
            Text(
                "A daily check that notifies you about film nearing expiry, finished " +
                "rolls sitting undeveloped, and ageing mixed chemistry.",
                color = FilmTheme.colors.dim, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enable reminders", color = FilmTheme.colors.dim, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.saveRemindersEnabled(on)
                        }
                    },
                    colors = settingsSwitchColors(),
                )
            }
            if (remindersEnabled) {
                // The three below belong to the switch above, so they are
                // indented under it and spaced by the column rather than by a
                // leading spacer inside each row — which is what left them
                // bunched against each other and against the enable row.
                Column(
                    Modifier.padding(top = 14.dp, start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ReminderToggle("Film expiry (60-day warning)", remindExpiry) { vm.saveRemindExpiry(it) }
                    ReminderToggle("Undeveloped rolls (3+ weeks)", remindUndeveloped) { vm.saveRemindUndeveloped(it) }
                    ReminderToggle("Chemistry age (60+ days mixed)", remindChemicals) { vm.saveRemindChemicals(it) }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = FilmTheme.colors.edge)
            Spacer(Modifier.height(12.dp))
            // Not under "Enable reminders": these are not the daily check, and
            // they only come when a badge is earned with the app closed (a
            // timer finishing in a pocket). With the app open, a banner shows.
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Achievement notifications", color = FilmTheme.colors.dim, fontSize = 13.sp)
                    Text("When a badge is earned with the app in the background",
                        color = FilmTheme.colors.dim, fontSize = 11.sp)
                }
                Switch(
                    checked = achNotify,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            achPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.saveAchNotify(on)
                        }
                    },
                    colors = settingsSwitchColors(),
                )
            }
        }

        // ── Weather ──────────────────────────────────────────────────────────
        // Last: pasted once, then never touched again.
        SectionCard("Weather (OpenWeatherMap)") {
            Text(
                "Get a free API key at openweathermap.org/api — paste it here to enable the weather screen and auto weather in shot logs.",
                color = FilmTheme.colors.dim, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VaultTextField(
                    owmDraft, { owmDraft = it }, "OWM API Key",
                    modifier = Modifier.weight(1f)
                )
                VaultButton("Save", small = true, onClick = { vm.saveOwmKey(owmDraft) })
            }
        }
    }

    if (showZoomEdit) {
        ZoomEditSheet(zoomLevels.sortedBy { it.zoomRatio }, vm) { showZoomEdit = false }
    }
}

/** One switch palette, so a new toggle cannot drift from the rest. */
@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = FilmTheme.colors.void,
    checkedTrackColor = FilmTheme.colors.cyan,
    uncheckedThumbColor = FilmTheme.colors.dim,
    uncheckedTrackColor = FilmTheme.colors.film,
)

@Composable
private fun ReminderToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = FilmTheme.colors.dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = settingsSwitchColors(),
        )
    }
}

/**
 * One palette in the picker, drawn in its own colours rather than the app's:
 * a strip of its base with the five accents across it, so choosing is
 * seeing, not reading a name.
 */
@Composable
private fun PaletteRow(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    val colors = FilmTheme.colors
    val own = palette.colors
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) colors.cyan else colors.edge)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = palette.label + if (selected) ", selected" else ""
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.background(own.film).border(1.dp, own.edge).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(own.cyan, own.magenta, own.yellow, own.mask, own.violet).forEach { c ->
                Box(Modifier.size(width = 10.dp, height = 22.dp).background(c))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(palette.label, color = if (selected) colors.halide else colors.dim, fontSize = 13.sp)
            Text(palette.blurb, color = colors.dim, fontSize = 11.sp)
        }
        if (selected) Text("✓", color = colors.cyan, fontSize = 15.sp)
    }
}

/** One choice in a look picker: a name, a line about it, and a tick when chosen. */
@Composable
private fun LookOptionRow(label: String, blurb: String, selected: Boolean, onClick: () -> Unit) {
    val colors = FilmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) colors.cyan else colors.edge)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label + if (selected) ", selected" else ""
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (selected) colors.halide else colors.dim, fontSize = 13.sp)
            Text(blurb, color = colors.dim, fontSize = 11.sp)
        }
        if (selected) Text("✓", color = colors.cyan, fontSize = 15.sp)
    }
}

/**
 * Your own edge printing: a line of text that leads the rebate of every roll
 * and stock, and the style of the shot log's frame numbers. Saved when Save
 * is pressed, not on every key, so a half-typed word never reaches the rolls.
 */
@Composable
private fun EdgePrintSettings(
    edgePrint: EdgePrint,
    onText: (String) -> Unit,
    onStyle: (FrameNumberStyle) -> Unit,
) {
    val colors = FilmTheme.colors
    var draft by remember(edgePrint.text) { mutableStateOf(edgePrint.text) }
    Text("Edge print", color = colors.dim, fontSize = 13.sp)
    Text("Printed along the edge of every roll and stock, before the camera and dates. " +
        "Up to ${EdgePrint.MAX_LENGTH} characters; blank prints nothing.",
        color = colors.dim, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VaultTextField(
            draft, { draft = EdgePrint.clean(it) }, "Edge text",
            modifier = Modifier.weight(1f), placeholder = "SAFETY FILM",
        )
        VaultButton("Save", small = true, onClick = { onText(draft) },
            enabled = draft.trim() != edgePrint.text)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        (if (draft.isBlank()) "▸  ZORKI 4  ▸  18 OCT 2026" else "▸  ${draft.trim()}  ▸  ZORKI 4  ▸  18 OCT 2026"),
        style = FilmTheme.type.rebate, color = colors.dim, maxLines = 1,
    )
    Spacer(Modifier.height(12.dp))
    Text("Frame numbers", color = colors.dim, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FrameNumberStyle.entries.forEach { s ->
            val selected = s == edgePrint.frameStyle
            Text(
                s.label,
                style = FilmTheme.type.data,
                color = if (selected) colors.void else colors.dim,
                modifier = Modifier
                    .background(if (selected) colors.cyan else colors.film)
                    .border(1.dp, if (selected) colors.cyan else colors.edge)
                    .clickable { onStyle(s) }
                    .semantics { contentDescription = "Frame numbers like ${s.label}" + if (selected) ", selected" else "" }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

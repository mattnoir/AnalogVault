package com.analogvault.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.Chemical
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import kotlinx.coroutines.delay

// ─── Entry ────────────────────────────────────────────────────────────────────

@Composable
fun DarkroomScreen(vm: MainViewModel) {
    val tabs = listOf("Chemistry", "Timers")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Bg2,
            contentColor = Amber,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[pagerState.currentPage]), color = Amber
                )
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(t, fontSize = 13.sp) },
                    selectedContentColor = Amber,
                    unselectedContentColor = TextTertiary)
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ChemistryTab(vm)
                1 -> TimersTab(vm)
                else -> ChemistryTab(vm)
            }
        }
    }
}

// ─── Chemistry tab (unchanged logic) ─────────────────────────────────────────

@Composable
fun ChemistryTab(vm: MainViewModel) {
    val chemicals     by vm.chemicals.collectAsState()
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Chemical?>(null) }
    var confirmDelete by remember { mutableStateOf<Chemical?>(null) }
    var setRollsDialog by remember { mutableStateOf<Chemical?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Chemistry", "${chemicals.size} chemicals") }
        if (chemicals.isEmpty()) item { EmptyState("No chemicals tracked yet") }
        items(chemicals, key = { it.id }) { chem ->
            val used   = vm.rolledCount(chem)
            val maxR   = chem.maxRolls.toIntOrNull()
            val pct    = if (maxR != null && maxR > 0) (used.toFloat() / maxR).coerceIn(0f, 1f) else null
            val alert  = when {
                maxR != null && used >= maxR                -> "exhausted"
                maxR != null && used.toFloat() / maxR >= 0.8f -> "warn"
                else -> null
            }
            val adjTime = if (chem.timeAdjPerRoll.isNotBlank() && chem.baseDevTime.isNotBlank()) {
                val base = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                val adj  = chem.timeAdjPerRoll.toDoubleOrNull() ?: 0.0
                "%.2f".format(base + used * adj)
            } else null

            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(chem.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                        Text("${chem.type}${if (chem.dilution.isNotBlank()) " · ${chem.dilution}" else ""}",
                            color = TextSecondary, fontSize = 12.sp)
                    }
                    when (alert) {
                        "exhausted" -> VaultTag("⚠ Exhausted", textColor = RedErr)
                        "warn"      -> VaultTag("⚠ Near limit", textColor = OrangeWarn)
                        else        -> if (maxR != null) VaultTag("OK", textColor = GreenOk)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaultTag("$used roll${if (used != 1) "s" else ""} used")
                    if (maxR != null) VaultTag("cap: $maxR")
                    if (chem.volume.isNotBlank()) VaultTag("${chem.volume}${chem.volumeUnit}")
                    if (chem.mixDate.isNotBlank()) VaultTag("mixed ${formatDate(chem.mixDate)}")
                }
                if (pct != null) {
                    Spacer(Modifier.height(6.dp))
                    VaultProgressBar(pct, color = when {
                        pct >= 1f   -> RedErr
                        pct >= 0.8f -> OrangeWarn
                        else        -> GreenOk
                    })
                }
                if (adjTime != null) {
                    Spacer(Modifier.height(4.dp))
                    val base = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                    val diff = "%.2f".format(adjTime.toDouble() - base)
                    Text("⏱ Adjusted dev time: $adjTime min (+$diff for $used rolls)",
                        color = Amber, fontSize = 11.sp)
                }
                if (chem.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(chem.notes, color = TextTertiary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton("Edit", small = true, ghost = true,
                        onClick = { editing = chem; showSheet = true })
                    VaultButton("Set Rolls", small = true, ghost = true,
                        onClick = { setRollsDialog = chem })
                    VaultButton("Delete", small = true, danger = true,
                        onClick = { confirmDelete = chem })
                }
            }
        }
        item {
            VaultButton("+ Add Chemistry", modifier = Modifier.fillMaxWidth(),
                onClick = { editing = null; showSheet = true })
        }
    }

    if (showSheet) {
        ChemSheet(editing, onDismiss = { showSheet = false; editing = null }) {
            vm.upsertChemical(it); showSheet = false; editing = null
        }
    }
    confirmDelete?.let { chem ->
        ConfirmDialog("Delete \"${chem.name}\"?",
            onConfirm = { vm.deleteChemical(chem); confirmDelete = null },
            onDismiss = { confirmDelete = null })
    }
    setRollsDialog?.let { chem ->
        SetRollsDialog(chem, vm.rolledCount(chem),
            onConfirm = { count -> vm.setChemicalRolls(chem.id, count); setRollsDialog = null },
            onDismiss = { setRollsDialog = null })
    }
}

// ─── Timer data ───────────────────────────────────────────────────────────────

data class DevStep(
    val id: String = uid(),
    val name: String,
    val durationSec: Int,
    val color: androidx.compose.ui.graphics.Color = Amber
)

data class DevTimer(
    val id: String = uid(),
    val name: String,
    val steps: List<DevStep>
)

// Preset processes
val DEV_PRESETS = listOf(
    DevTimer("preset_c41", "C-41 Color", listOf(
        DevStep(name = "Developer",    durationSec = 3*60+30, color = AmberBright),
        DevStep(name = "Blix",         durationSec = 6*60+30, color = OrangeWarn),
        DevStep(name = "Wash",         durationSec = 3*60,    color = BlueInfo),
        DevStep(name = "Stabiliser",   durationSec = 60,      color = GreenOk)
    )),
    DevTimer("preset_bw_std", "B&W Standard", listOf(
        DevStep(name = "Developer",    durationSec = 8*60,    color = AmberBright),
        DevStep(name = "Stop Bath",    durationSec = 60,      color = OrangeWarn),
        DevStep(name = "Fixer",        durationSec = 5*60,    color = BlueInfo),
        DevStep(name = "Wash",         durationSec = 10*60,   color = BlueInfo),
        DevStep(name = "Wetting Agent",durationSec = 60,      color = GreenOk)
    )),
    DevTimer("preset_bw_stand", "B&W Stand (Rodinal 1:100)", listOf(
        DevStep(name = "Develop (stand)", durationSec = 60*60, color = AmberBright),
        DevStep(name = "Stop Bath",       durationSec = 60,    color = OrangeWarn),
        DevStep(name = "Fixer",           durationSec = 5*60,  color = BlueInfo),
        DevStep(name = "Wash",            durationSec = 10*60, color = BlueInfo)
    )),
    DevTimer("preset_e6", "E-6 Slide", listOf(
        DevStep(name = "First Developer", durationSec = 6*60,  color = AmberBright),
        DevStep(name = "Wash",            durationSec = 2*60,  color = BlueInfo),
        DevStep(name = "Color Developer", durationSec = 6*60,  color = OrangeWarn),
        DevStep(name = "Wash",            durationSec = 2*60,  color = BlueInfo),
        DevStep(name = "Blix",            durationSec = 6*60,  color = GreenOk),
        DevStep(name = "Wash",            durationSec = 3*60,  color = BlueInfo),
        DevStep(name = "Stabiliser",      durationSec = 60,    color = GreenOk)
    ))
)

// ─── Timers tab ───────────────────────────────────────────────────────────────

@Composable
fun TimersTab(vm: MainViewModel) {
    val chemicals by vm.chemicals.collectAsState()
    var activeTimer  by remember { mutableStateOf<DevTimer?>(null) }
    var showPresets  by remember { mutableStateOf(false) }
    var showCustom   by remember { mutableStateOf(false) }

    if (activeTimer != null) {
        ActiveTimerScreen(
            timer = activeTimer!!,
            onDone = { activeTimer = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Darkroom Timers") }

        // Presets
        item {
            Text("Process Presets", color = Amber, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(DEV_PRESETS) { preset ->
            PresetCard(preset) { activeTimer = preset }
        }

        // Custom from chemistry
        if (chemicals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("From Your Chemistry", color = Amber, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
            }
            items(chemicals.filter { it.baseDevTime.isNotBlank() }, key = { it.id }) { chem ->
                val rolls = vm.rolledCount(chem)
                val base  = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                val adj   = chem.timeAdjPerRoll.toDoubleOrNull() ?: 0.0
                val totalMin = base + rolls * adj
                val totalSec = (totalMin * 60).toInt()
                VaultCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chem.name, color = TextPrimary, fontSize = 14.sp)
                            Text("${chem.type}${if (chem.dilution.isNotBlank()) " · ${chem.dilution}" else ""}",
                                color = TextSecondary, fontSize = 11.sp)
                            if (adj > 0) Text("Adjusted for $rolls rolls: ${"%.1f".format(totalMin)} min",
                                color = Amber, fontSize = 11.sp)
                        }
                        VaultButton("Start", small = true, onClick = {
                            activeTimer = DevTimer(
                                name = chem.name,
                                steps = listOf(DevStep(name = chem.name, durationSec = totalSec))
                            )
                        })
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            VaultButton("+ Custom Timer", modifier = Modifier.fillMaxWidth(), ghost = true,
                onClick = { showCustom = true })
        }
    }

    if (showCustom) {
        CustomTimerSheet(onDismiss = { showCustom = false }) { timer ->
            activeTimer = timer; showCustom = false
        }
    }
}

@Composable
private fun PresetCard(preset: DevTimer, onStart: () -> Unit) {
    VaultCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                val totalSec = preset.steps.sumOf { it.durationSec }
                Text("${preset.steps.size} steps · ${formatDuration(totalSec)}",
                    color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                // Step pills
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    preset.steps.forEach { step ->
                        VaultTag(step.name.take(8), textColor = step.color)
                    }
                }
            }
            VaultButton("Start", small = true, onClick = onStart)
        }
    }
}

// ─── Active timer screen ──────────────────────────────────────────────────────

@Composable
fun ActiveTimerScreen(timer: DevTimer, onDone: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(timer.steps[0].durationSec) }
    var running     by remember { mutableStateOf(false) }
    var finished    by remember { mutableStateOf(false) }

    val step = timer.steps.getOrNull(currentStep)

    // Countdown tick
    LaunchedEffect(running, currentStep) {
        if (!running) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        // Step finished
        if (currentStep < timer.steps.lastIndex) {
            currentStep++
            secondsLeft = timer.steps[currentStep].durationSec
            // Auto-pause between steps so user sees the transition
            running = false
        } else {
            running = false
            finished = true
        }
    }

    val progress = if (step != null && step.durationSec > 0)
        1f - (secondsLeft.toFloat() / step.durationSec) else 1f

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(timer.name, color = AmberBright, fontSize = 18.sp)
                Text("Step ${currentStep + 1} of ${timer.steps.size}",
                    color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Close, contentDescription = "Stop", tint = TextSecondary)
            }
        }

        // Step progress row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            timer.steps.forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(1f).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(when {
                            i < currentStep  -> s.color
                            i == currentStep -> s.color.copy(alpha = 0.5f)
                            else             -> Border
                        })
                )
            }
        }

        // Main timer display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (finished) {
                Text("✓ Done!", color = GreenOk, fontSize = 48.sp)
                Text("${timer.name} complete", color = TextSecondary, fontSize = 14.sp)
            } else if (step != null) {
                Text(step.name, color = step.color, fontSize = 22.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    formatDuration(secondsLeft),
                    color = if (secondsLeft <= 10 && running) RedErr else TextPrimary,
                    fontSize = 72.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(16.dp))
                // Circular-ish progress bar (linear for simplicity)
                VaultProgressBar(progress, color = step.color)
                Spacer(Modifier.height(8.dp))
                Text("of ${formatDuration(step.durationSec)}", color = TextTertiary, fontSize = 12.sp)
            }
        }

        // Controls
        if (finished) {
            VaultButton("Done", modifier = Modifier.fillMaxWidth(), onClick = onDone)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Next step preview
                timer.steps.getOrNull(currentStep + 1)?.let { next ->
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)).background(Bg3)
                            .border(1.dp, Border, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Next:", color = TextTertiary, fontSize = 11.sp)
                            Text(next.name, color = next.color, fontSize = 12.sp)
                            Text(formatDuration(next.durationSec), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Restart step
                    VaultButton("↺ Reset", ghost = true, modifier = Modifier.weight(0.4f),
                        onClick = { secondsLeft = step?.durationSec ?: 0; running = false })
                    // Play/pause
                    Button(
                        onClick = { running = !running },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) OrangeWarn.copy(alpha = 0.2f) else AmberDark,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (running) "Pause" else "Start", fontSize = 16.sp)
                    }
                    // Skip step
                    if (currentStep < timer.steps.lastIndex) {
                        VaultButton("Skip ›", ghost = true, modifier = Modifier.weight(0.4f),
                            onClick = {
                                running = false
                                currentStep++
                                secondsLeft = timer.steps[currentStep].durationSec
                            })
                    }
                }
            }
        }
    }
}

// ─── Custom timer sheet ───────────────────────────────────────────────────────

@Composable
fun CustomTimerSheet(onDismiss: () -> Unit, onStart: (DevTimer) -> Unit) {
    var name   by remember { mutableStateOf("") }
    // Each step: name, minutes, seconds
    var steps  by remember { mutableStateOf(listOf(Triple("", "8", "0"))) }

    VaultSheet("Custom Timer", onDismiss) {
        VaultTextField(name, { name = it }, "Process name", placeholder = "e.g. HC-110 Dilution B")
        Spacer(Modifier.height(12.dp))
        Text("Steps", color = Amber, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { i, (stepName, mins, secs) ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).background(Bg3)
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Step ${i + 1}", color = TextTertiary, fontSize = 11.sp)
                    if (steps.size > 1) {
                        IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(i) } },
                            Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                VaultTextField(stepName, { v -> steps = steps.toMutableList().also { it[i] = Triple(v, mins, secs) } },
                    "Step name", placeholder = "Developer")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultTextField(mins,
                        { v -> steps = steps.toMutableList().also { it[i] = Triple(stepName, v, secs) } },
                        "Min", modifier = Modifier.weight(1f),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    VaultTextField(secs,
                        { v -> steps = steps.toMutableList().also { it[i] = Triple(stepName, mins, v) } },
                        "Sec", modifier = Modifier.weight(1f),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        VaultButton("+ Add Step", modifier = Modifier.fillMaxWidth(), ghost = true,
            onClick = { steps = steps + Triple("", "0", "0") })
        Spacer(Modifier.height(16.dp))
        VaultButton("Start Timer", modifier = Modifier.fillMaxWidth(), onClick = {
            if (name.isNotBlank() && steps.any { it.first.isNotBlank() }) {
                val devSteps = steps
                    .filter { it.first.isNotBlank() }
                    .map { (n, m, s) ->
                        DevStep(name = n, durationSec = (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0))
                    }
                if (devSteps.isNotEmpty()) onStart(DevTimer(name = name, steps = devSteps))
            }
        })
    }
}

// ─── Dialogs (unchanged) ──────────────────────────────────────────────────────

@Composable
fun SetRollsDialog(chem: Chemical, current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg3,
        title = { Text("Rolls used for ${chem.name}", color = AmberBright) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber,
                    unfocusedBorderColor = Border, focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toIntOrNull() ?: current) }) {
                Text("Set", color = Amber)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun ChemSheet(ed: Chemical?, onDismiss: () -> Unit, onSave: (Chemical) -> Unit) {
    var name           by remember { mutableStateOf(ed?.name ?: "") }
    var type           by remember { mutableStateOf(ed?.type ?: Constants.CHEM_TYPES[0]) }
    var dilution       by remember { mutableStateOf(ed?.dilution ?: "") }
    var volume         by remember { mutableStateOf(ed?.volume ?: "") }
    var volumeUnit     by remember { mutableStateOf(ed?.volumeUnit ?: "ml") }
    var mixDate        by remember { mutableStateOf(ed?.mixDate ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var maxRolls       by remember { mutableStateOf(ed?.maxRolls ?: "") }
    var baseDevTime    by remember { mutableStateOf(ed?.baseDevTime ?: "") }
    var timeAdjPerRoll by remember { mutableStateOf(ed?.timeAdjPerRoll ?: "") }
    var notes          by remember { mutableStateOf(ed?.notes ?: "") }
    val allChemDb = Constants.DEV_DB + Constants.FIX_DB

    VaultSheet(if (ed != null) "Edit Chemistry" else "Add Chemistry", onDismiss) {
        AutoCompleteField(name, { name = it }, "Name / Brand", allChemDb, placeholder = "e.g. Kodak HC-110")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Type", type, Constants.CHEM_TYPES, { type = it }, modifier = Modifier.weight(1f))
            VaultTextField(dilution, { dilution = it }, "Dilution", modifier = Modifier.weight(1f), placeholder = "1:31")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(volume, { volume = it }, "Volume", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            VaultDropdown("Unit", volumeUnit, Constants.CHEM_UNITS, { volumeUnit = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(mixDate, { mixDate = it }, "Mix Date", modifier = Modifier.weight(1f))
            VaultTextField(maxRolls, { maxRolls = it }, "Max Rolls", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, placeholder = "25")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(baseDevTime, { baseDevTime = it }, "Base Dev (min)", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, placeholder = "8")
            VaultTextField(timeAdjPerRoll, { timeAdjPerRoll = it }, "+Time/Roll", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, placeholder = "0.1")
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2,
            placeholder = "Storage, replenishment…")
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Chemistry", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Chemical(id = ed?.id ?: uid(), name = name, type = type, dilution = dilution,
                volume = volume, volumeUnit = volumeUnit, mixDate = mixDate, maxRolls = maxRolls,
                baseDevTime = baseDevTime, timeAdjPerRoll = timeAdjPerRoll,
                manualRolls = ed?.manualRolls ?: -1, notes = notes))
        })
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun formatDuration(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

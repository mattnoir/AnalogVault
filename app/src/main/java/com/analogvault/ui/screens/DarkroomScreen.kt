@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import com.analogvault.data.model.DevRecipe
import com.analogvault.ui.DarkroomTimerState
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.analogvault.util.toDecimalOrNull

// ─── Entry ────────────────────────────────────────────────────────────────────

@Composable
fun DarkroomScreen(vm: MainViewModel) {
    val tabs = listOf("Chemistry", "Recipes", "Timers")
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
                1 -> RecipesTab(vm, onTimerStarted = { scope.launch { pagerState.animateScrollToPage(2) } })
                2 -> TimersTab(vm)
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
                TagRow() {
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

// ─── Recipes tab ──────────────────────────────────────────────────────────────

/** Develop step from the recipe + standard finishing steps for its process. */
fun timerFromRecipe(r: DevRecipe): DevTimer {
    val devSec = ((r.devTimeMin.toDecimalOrNull() ?: 0.0) * 60).toInt().coerceAtLeast(1)
    val steps = mutableListOf(DevStep(name = "Develop", durationSec = devSec, color = AmberBright))
    when {
        r.process.startsWith("C-41") -> {
            steps += DevStep(name = "Blix",       durationSec = 6 * 60 + 30, color = OrangeWarn)
            steps += DevStep(name = "Wash",       durationSec = 3 * 60,      color = BlueInfo)
            steps += DevStep(name = "Stabiliser", durationSec = 60,          color = GreenOk)
        }
        else -> { // B&W variants (standard / stand / semi-stand)
            steps += DevStep(name = "Stop Bath",  durationSec = 60,          color = OrangeWarn)
            steps += DevStep(name = "Fixer",      durationSec = 5 * 60,      color = BlueInfo)
            steps += DevStep(name = "Wash",       durationSec = 10 * 60,     color = BlueInfo)
        }
    }
    return DevTimer(name = r.name.ifBlank { "${r.developer} ${r.dilution}".trim() }, steps = steps)
}

@Composable
fun RecipesTab(vm: MainViewModel, onTimerStarted: () -> Unit) {
    val recipes by vm.recipes.collectAsState()
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<DevRecipe?>(null) }
    var confirmDelete by remember { mutableStateOf<DevRecipe?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Dev Recipes", "${recipes.size} recipes") }
        item {
            Text("Built-in times are published starting points — always verify against " +
                 "your film batch, developer age and workflow.",
                color = TextTertiary, fontSize = 11.sp)
        }
        if (recipes.isEmpty()) item { EmptyState("No recipes yet") }
        items(recipes, key = { it.id }) { r ->
            VaultCard {
                Text(r.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                Text("${r.developer}${if (r.dilution.isNotBlank()) " · ${r.dilution}" else ""}",
                    color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                TagRow() {
                    if (r.filmName.isNotBlank()) VaultTag(r.filmName)
                    VaultTag("${r.devTimeMin} min @ ${r.tempC}°C", textColor = AmberBright)
                    if (r.pushStops != 0)
                        VaultTag(if (r.pushStops > 0) "push +${r.pushStops}" else "pull ${r.pushStops}",
                            textColor = OrangeWarn)
                    if (r.isBuiltIn) VaultTag("built-in", textColor = TextTertiary)
                }
                if (r.agitation.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Agitation: ${r.agitation}", color = TextTertiary, fontSize = 10.sp)
                }
                if (r.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(r.notes, color = TextTertiary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton("▶ Start Timer", small = true, onClick = {
                        vm.startTimer(timerFromRecipe(r)); onTimerStarted()
                    })
                    VaultButton("Edit", small = true, ghost = true,
                        onClick = { editing = r; showSheet = true })
                    VaultButton("Delete", small = true, danger = true,
                        onClick = { confirmDelete = r })
                }
            }
        }
        item {
            VaultButton("+ Add Recipe", modifier = Modifier.fillMaxWidth(),
                onClick = { editing = null; showSheet = true })
        }
    }

    if (showSheet) {
        RecipeSheet(editing, onDismiss = { showSheet = false; editing = null }) {
            vm.upsertRecipe(it); showSheet = false; editing = null
        }
    }
    confirmDelete?.let { r ->
        ConfirmDialog("Delete recipe \"${r.name}\"?",
            onConfirm = { vm.deleteRecipe(r); confirmDelete = null },
            onDismiss = { confirmDelete = null })
    }
}

@Composable
fun RecipeSheet(ed: DevRecipe?, onDismiss: () -> Unit, onSave: (DevRecipe) -> Unit) {
    var name       by remember { mutableStateOf(ed?.name ?: "") }
    var filmName   by remember { mutableStateOf(ed?.filmName ?: "") }
    var process    by remember { mutableStateOf(ed?.process ?: "B&W (Standard)") }
    var developer  by remember { mutableStateOf(ed?.developer ?: "") }
    var dilution   by remember { mutableStateOf(ed?.dilution ?: "") }
    var tempC      by remember { mutableStateOf(ed?.tempC ?: "20") }
    var devTimeMin by remember { mutableStateOf(ed?.devTimeMin ?: "") }
    var pushStops  by remember { mutableIntStateOf(ed?.pushStops ?: 0) }
    var agitation  by remember { mutableStateOf(ed?.agitation ?: "30s initial, then 10s/min") }
    var notes      by remember { mutableStateOf(ed?.notes ?: "") }

    VaultSheet(if (ed != null) "Edit Recipe" else "Add Recipe", onDismiss) {
        VaultTextField(name, { name = it }, "Recipe name", placeholder = "HP5+ in DD-X 1+4")
        Spacer(Modifier.height(10.dp))
        AutoCompleteField(filmName, { filmName = it }, "Film (optional)", Constants.FILM_DB)
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Process", process, Constants.DEVELOP_PROCESSES, { process = it })
        Spacer(Modifier.height(10.dp))
        AutoCompleteField(developer, { developer = it }, "Developer", Constants.DEV_DB)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(dilution, { dilution = it }, "Dilution", modifier = Modifier.weight(1f), placeholder = "1+4")
            VaultTextField(tempC, { tempC = it }, "Temp (°C)", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VaultTextField(devTimeMin, { devTimeMin = it }, "Dev time (min)", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, placeholder = "9.5")
            Column {
                Text("Push/pull", color = TextTertiary, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (pushStops > -3) pushStops-- }, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Remove, null, tint = Amber, modifier = Modifier.size(16.dp))
                    }
                    Text(if (pushStops > 0) "+$pushStops" else "$pushStops",
                        color = if (pushStops == 0) TextSecondary else OrangeWarn, fontSize = 14.sp)
                    IconButton(onClick = { if (pushStops < 3) pushStops++ }, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add, null, tint = Amber, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(agitation, { agitation = it }, "Agitation")
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Recipe", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(DevRecipe(
                id = ed?.id ?: uid(), name = name, filmName = filmName, process = process,
                developer = developer, dilution = dilution, tempC = tempC,
                devTimeMin = devTimeMin, pushStops = pushStops, agitation = agitation,
                notes = notes, isBuiltIn = ed?.isBuiltIn ?: false
            ))
        })
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
    val timerState by vm.timerState.collectAsState()
    var showCustom by remember { mutableStateOf(false) }

    timerState?.let { state ->
        ActiveTimerScreen(state = state, vm = vm)
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
            PresetCard(preset) { vm.startTimer(preset) }
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
                            vm.startTimer(DevTimer(
                                name = chem.name,
                                steps = listOf(DevStep(name = chem.name, durationSec = totalSec))
                            ))
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
            vm.startTimer(timer); showCustom = false
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
                TagRow() {
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
fun ActiveTimerScreen(state: DarkroomTimerState, vm: MainViewModel) {
    // Countdown state lives in MainViewModel (wall-clock based) — it survives
    // tab navigation and can't drift the way a delay(1000) tick loop does.
    val timer       = state.timer
    val currentStep = state.currentStep
    val secondsLeft = state.secondsLeft
    val running     = state.running
    val finished    = state.finished
    val step        = timer.steps.getOrNull(currentStep)

    // Chemistry is time-critical: don't let the screen (and the user's view of
    // the countdown) go dark mid-development.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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
            IconButton(onClick = { vm.stopTimer() }) {
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
            VaultButton("Done", modifier = Modifier.fillMaxWidth(), onClick = { vm.stopTimer() })
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Agitation cues — short vibration at each minute mark of the step
                val agitationCues by vm.agitationCues.collectAsState()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Agitation cues (10 s each minute)", color = TextSecondary, fontSize = 12.sp)
                    Switch(
                        checked = agitationCues,
                        onCheckedChange = { vm.saveAgitationCues(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark),
                        modifier = Modifier.height(24.dp)
                    )
                }
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
                        onClick = { vm.resetTimerStep() })
                    // Play/pause
                    Button(
                        onClick = { vm.toggleTimerRunning() },
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
                            onClick = { vm.skipTimerStep() })
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

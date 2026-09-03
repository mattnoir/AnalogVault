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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.analogvault.data.model.Chemical
import com.analogvault.ui.DarkroomTimerState
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.film.FilmChip
import com.analogvault.ui.film.FilmChipRow
import com.analogvault.ui.film.halation
import com.analogvault.ui.film.hazardHatch
import com.analogvault.ui.theme.*
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.analogvault.util.DevTime
import com.analogvault.ui.film.DyeIcon
import com.analogvault.ui.film.FilmIcons

// ─── Entry ────────────────────────────────────────────────────────────────────

@Composable
fun DarkroomScreen(vm: MainViewModel) {
    val tabs = listOf("Chemistry", "Timers")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    val colors = FilmTheme.colors
    Column(Modifier.fillMaxSize().background(colors.void)) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = colors.void,
            contentColor = colors.cyan,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[pagerState.currentPage]), color = colors.cyan
                )
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(t.uppercase(), style = FilmTheme.type.eyebrow) },
                    selectedContentColor = colors.cyan,
                    unselectedContentColor = colors.dim)
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
        if (chemicals.isEmpty()) item { EmptyState("No chemistry tracked.", verb = "Add a developer", onVerb = { editing = null; showSheet = true }) }
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
                        Text(chem.name.ifBlank { "Unnamed" }, color = FilmTheme.colors.halide, fontSize = 15.sp)
                        Text("${chem.type}${if (chem.dilution.isNotBlank()) " · ${chem.dilution}" else ""}",
                            color = FilmTheme.colors.dim, fontSize = 12.sp)
                    }
                    when (alert) {
                        "exhausted" -> VaultTag("Exhausted", textColor = FilmTheme.colors.mask, icon = FilmIcons.Warn)
                        "warn"      -> VaultTag("Near limit", textColor = FilmTheme.colors.yellow, icon = FilmIcons.Warn)
                        else        -> if (maxR != null) VaultTag("OK", textColor = FilmTheme.colors.cyan)
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
                        pct >= 1f   -> FilmTheme.colors.mask
                        pct >= 0.8f -> FilmTheme.colors.yellow
                        else        -> FilmTheme.colors.cyan
                    })
                }
                if (adjTime != null) {
                    Spacer(Modifier.height(4.dp))
                    val base = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                    val diff = "%.2f".format(adjTime.toDouble() - base)
                    Text("Adjusted dev time: $adjTime min (+$diff for $used rolls)",
                        color = FilmTheme.colors.cyan, fontSize = 11.sp)
                }
                if (chem.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(chem.notes, color = FilmTheme.colors.dim, fontSize = 11.sp)
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

/**
 * What a step is for. Colour is derived from this at draw time rather than
 * stored, so the presets stay plain data and the safelight scheme can recolour
 * every timer without anyone rebuilding the list.
 */
enum class StepRole { DEVELOP, STOP, FIX, WASH, FINISH }

data class DevStep(
    val id: String = uid(),
    val name: String,
    val durationSec: Int,
    val role: StepRole = StepRole.DEVELOP,
    val agitation: DevTime.Agitation = DevTime.Agitation.STANDARD,
)

data class DevTimer(
    val id: String = uid(),
    val name: String,
    val steps: List<DevStep>,
) {
    /**
     * Which temperature rules apply, read off the name. One source of truth —
     * a stored field could disagree with a name the user edited.
     */
    val process: DevTime.ProcessSpec get() = DevTime.specFor(name)
}

/**
 * The same process at a working temperature.
 *
 * B&W times are published at 20 °C and carry a curve, so they scale. C-41 and
 * E-6 are tabulated at one temperature by the kit and are returned untouched —
 * see DevTime for why extrapolating them is the wrong favour to do someone.
 */
fun DevTimer.atTemperature(tempC: Double): DevTimer {
    if (process.isFixedTemperature) return this
    return copy(steps = steps.map { step ->
        // Only the developer scales, because the published time/temperature
        // curves are developer curves and nothing else. A wash is however long
        // it takes water to carry the fixer out; a stop bath is a rinse in acid
        // and thirty seconds is thirty seconds; and fixing is timed off the
        // clearing time of the film, not off a developer's chart. Scaling those
        // by the developer's curve would be borrowing authority the curve does
        // not have.
        if (step.role != StepRole.DEVELOP) step
        else step.copy(durationSec = DevTime.bwAdjustedSec(step.durationSec, tempC))
    })
}

// Preset processes. B&W times are at 20 °C, colour at the kit's own 38 °C.
val DEV_PRESETS = listOf(
    DevTimer("preset_c41", "C-41 Color", listOf(
        DevStep(name = "Developer",  durationSec = 3*60+30, role = StepRole.DEVELOP,
            agitation = DevTime.Agitation(initialSec = 30, everySec = 30, burstSec = 5)),
        DevStep(name = "Blix",       durationSec = 6*60+30, role = StepRole.FIX,
            agitation = DevTime.Agitation(initialSec = 30, everySec = 30, burstSec = 5)),
        DevStep(name = "Wash",       durationSec = 3*60,    role = StepRole.WASH,
            agitation = DevTime.Agitation.NONE),
        DevStep(name = "Stabiliser", durationSec = 60,      role = StepRole.FINISH,
            agitation = DevTime.Agitation.NONE),
    )),
    DevTimer("preset_bw_std", "B&W Standard", listOf(
        DevStep(name = "Developer",     durationSec = 8*60,  role = StepRole.DEVELOP,
            agitation = DevTime.Agitation.STANDARD),
        DevStep(name = "Stop Bath",     durationSec = 60,    role = StepRole.STOP,
            agitation = DevTime.Agitation.CONTINUOUS),
        DevStep(name = "Fixer",         durationSec = 5*60,  role = StepRole.FIX,
            agitation = DevTime.Agitation.STANDARD),
        DevStep(name = "Wash",          durationSec = 10*60, role = StepRole.WASH,
            agitation = DevTime.Agitation.NONE),
        DevStep(name = "Wetting Agent", durationSec = 60,    role = StepRole.FINISH,
            agitation = DevTime.Agitation.NONE),
    )),
    DevTimer("preset_bw_stand", "B&W Stand (Rodinal 1:100)", listOf(
        DevStep(name = "Develop (stand)", durationSec = 60*60, role = StepRole.DEVELOP,
            agitation = DevTime.Agitation.STAND),
        DevStep(name = "Stop Bath",       durationSec = 60,    role = StepRole.STOP,
            agitation = DevTime.Agitation.CONTINUOUS),
        DevStep(name = "Fixer",           durationSec = 5*60,  role = StepRole.FIX,
            agitation = DevTime.Agitation.STANDARD),
        DevStep(name = "Wash",            durationSec = 10*60, role = StepRole.WASH,
            agitation = DevTime.Agitation.NONE),
    )),
    DevTimer("preset_e6", "E-6 Slide", listOf(
        DevStep(name = "First Developer", durationSec = 6*60, role = StepRole.DEVELOP,
            agitation = DevTime.Agitation(initialSec = 30, everySec = 30, burstSec = 5)),
        DevStep(name = "Wash",            durationSec = 2*60, role = StepRole.WASH,
            agitation = DevTime.Agitation.NONE),
        DevStep(name = "Color Developer", durationSec = 6*60, role = StepRole.DEVELOP,
            agitation = DevTime.Agitation(initialSec = 30, everySec = 30, burstSec = 5)),
        DevStep(name = "Wash",            durationSec = 2*60, role = StepRole.WASH,
            agitation = DevTime.Agitation.NONE),
        DevStep(name = "Blix",            durationSec = 6*60, role = StepRole.FIX,
            agitation = DevTime.Agitation(initialSec = 30, everySec = 30, burstSec = 5)),
        DevStep(name = "Wash",            durationSec = 3*60, role = StepRole.WASH,
            agitation = DevTime.Agitation.NONE),
        DevStep(name = "Stabiliser",      durationSec = 60,   role = StepRole.FINISH,
            agitation = DevTime.Agitation.NONE),
    )),
)

// ─── Timers tab ───────────────────────────────────────────────────────────────

@Composable
fun TimersTab(vm: MainViewModel) {
    val colors = FilmTheme.colors
    val chemicals by vm.chemicals.collectAsState()
    val timerState by vm.timerState.collectAsState()
    val tempC by vm.devTempC.collectAsState()
    var showCustom by remember { mutableStateOf(false) }

    timerState?.let { state ->
        ActiveTimerScreen(state = state, vm = vm)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.void),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Temperature first: it changes every time below it, so asking for it
        // after the times have been read would be asking too late.
        item {
            DarkroomEyebrow("Working temperature")
            TemperatureRuler(tempC, onValue = { vm.saveDevTempC(it) })
            Spacer(Modifier.height(6.dp))
            Text(
                "B&W TIMES BELOW ARE SCALED FROM 20 °C · ×${"%.2f".format(DevTime.bwFactor(tempC))}",
                style = FilmTheme.type.rebate, color = colors.dim,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }

        item { DarkroomEyebrow("Process presets") }
        items(DEV_PRESETS, key = { it.id }) { preset ->
            PresetCard(preset, tempC) { vm.startTimer(preset.atTemperature(tempC)) }
        }

        // Custom from chemistry
        val usable = chemicals.filter { it.baseDevTime.isNotBlank() }
        if (usable.isNotEmpty()) {
            item { DarkroomEyebrow("From your chemistry") }
            items(usable, key = { it.id }) { chem ->
                val rolls = vm.rolledCount(chem)
                val base  = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                val adj   = chem.timeAdjPerRoll.toDoubleOrNull() ?: 0.0
                // Two corrections, applied in order: exhaustion first, because
                // it is a property of the chemistry, then temperature, which is
                // a property of the room.
                val exhaustedSec = ((base + rolls * adj) * 60).toInt()
                val timer = DevTimer(
                    name = chem.name,
                    steps = listOf(DevStep(name = chem.name, durationSec = exhaustedSec)),
                ).atTemperature(tempC)
                val finalSec = timer.steps.first().durationSec
                ChemistryTimerCard(
                    name = chem.name,
                    subtitle = listOfNotNull(
                        chem.type.takeIf { it.isNotBlank() },
                        chem.dilution.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    rolls = rolls,
                    hasRollAdjustment = adj > 0,
                    exhaustedSec = exhaustedSec,
                    finalSec = finalSec,
                    fixedTemperature = timer.process.isFixedTemperature,
                    onStart = { vm.startTimer(timer) },
                )
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.padding(horizontal = 14.dp)) {
                DarkroomSegment("+ CUSTOM TIMER", selected = false, accent = colors.cyan,
                    modifier = Modifier.fillMaxWidth()) { showCustom = true }
            }
        }
    }

    if (showCustom) {
        CustomTimerSheet(onDismiss = { showCustom = false }) { timer ->
            vm.startTimer(timer.atTemperature(tempC)); showCustom = false
        }
    }
}

@Composable
private fun PresetCard(preset: DevTimer, tempC: Double, onStart: () -> Unit) {
    val colors = FilmTheme.colors
    val adjusted = preset.atTemperature(tempC)
    val warning = DevTime.warning(preset.process, tempC)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(colors.film)
            .border(1.dp, colors.edge)
            .clickable(onClick = onStart)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(preset.name.uppercase(), style = FilmTheme.type.stock.copy(fontSize = 20.sp),
                color = colors.halide, modifier = Modifier.weight(1f))
            Text("▶", style = FilmTheme.type.data, color = colors.magenta)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${preset.steps.size} STEPS  ▸  ${formatDuration(adjusted.steps.sumOf { it.durationSec })}" +
                if (preset.process.isFixedTemperature)
                    "  ▸  AT ${"%.1f".format(preset.process.fixedC)} °C" else "",
            style = FilmTheme.type.rebate, color = colors.dim,
        )
        Spacer(Modifier.height(9.dp))
        FilmChipRow {
            adjusted.steps.forEach { step ->
                // Full step name, not a truncation: the chips wrap, and
                // "Develop (s" is worse than a second row.
                FilmChip("${step.name} ${formatDuration(step.durationSec)}",
                    color = step.role.color())
            }
        }
        // A colour process at the wrong temperature is the one thing on this
        // screen that ruins film rather than merely disappointing you.
        warning?.let {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .hazardHatch(colors.mask.copy(alpha = 0.12f), stripe = 5.dp)
                    .border(1.dp, colors.mask)
                    .padding(9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DyeIcon(FilmIcons.Warn, null, size = 15.dp, tint = colors.mask)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.halide)
            }
        }
    }
}

@Composable
private fun ChemistryTimerCard(
    name: String,
    subtitle: String,
    rolls: Int,
    hasRollAdjustment: Boolean,
    exhaustedSec: Int,
    finalSec: Int,
    fixedTemperature: Boolean,
    onStart: () -> Unit,
) {
    val colors = FilmTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(colors.film)
            .border(1.dp, colors.edge)
            .clickable(onClick = onStart)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name.uppercase(), style = FilmTheme.type.stock.copy(fontSize = 18.sp),
                    color = colors.halide, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle.uppercase(), style = FilmTheme.type.rebate, color = colors.dim)
                }
            }
            Text(formatDuration(finalSec), style = FilmTheme.type.data.copy(fontSize = 18.sp),
                color = colors.yellow)
        }
        Spacer(Modifier.height(9.dp))
        FilmChipRow {
            if (hasRollAdjustment) FilmChip("+$rolls ROLLS ${formatDuration(exhaustedSec)}")
            if (fixedTemperature) FilmChip("FIXED TEMP", color = colors.mask)
            else if (finalSec != exhaustedSec) FilmChip("TEMP ADJUSTED", color = colors.cyan)
        }
    }
}

// ─── Active timer screen ──────────────────────────────────────────────────────

@Composable
fun ActiveTimerScreen(state: DarkroomTimerState, vm: MainViewModel) {
    // Countdown state lives in MainViewModel (wall-clock based) — it survives
    // tab navigation and can't drift the way a delay(1000) tick loop does.
    val colors      = FilmTheme.colors
    val timer       = state.timer
    val currentStep = state.currentStep
    val secondsLeft = state.secondsLeft
    val running     = state.running
    val finished    = state.finished
    val step        = timer.steps.getOrNull(currentStep)
    val agitationCues by vm.agitationCues.collectAsState()

    // Chemistry is time-critical: don't let the screen (and the user's view of
    // the countdown) go dark mid-development.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val elapsed = if (step != null) step.durationSec - secondsLeft else 0
    val agitatingNow = step != null && running && step.agitation.isAgitating(elapsed)
    val stepColor = step?.role?.color() ?: colors.halide

    Column(
        Modifier.fillMaxSize().background(colors.void).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(timer.name.uppercase(), style = FilmTheme.type.stock.copy(fontSize = 20.sp),
                        color = colors.halide, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("STEP ${currentStep + 1} OF ${timer.steps.size}",
                        style = FilmTheme.type.rebate, color = colors.dim)
                }
                IconButton(onClick = { vm.stopTimer() }, modifier = Modifier.size(40.dp)) {
                    DyeIcon(FilmIcons.Close, "Stop the timer", size = 20.dp, tint = colors.dim)
                }
            }

            Spacer(Modifier.height(10.dp))
            // Step rail — one segment per step, filled as they complete
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                timer.steps.forEachIndexed { i, s ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(when {
                                i < currentStep  -> s.role.color()
                                i == currentStep -> s.role.color().copy(alpha = 0.45f)
                                else             -> colors.edge
                            })
                    )
                }
            }
        }

        // ── The instrument ────────────────────────────────────────────────────
        // Weighted rather than wrapped: the agitation bar is the length of the
        // step, and a bar that only spans the height of the text beside it reads
        // as a decoration instead of a scale.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        if (finished) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DONE", style = FilmTheme.type.display.copy(fontSize = 64.sp), color = colors.cyan)
                Spacer(Modifier.height(6.dp))
                Text("${timer.name.uppercase()} COMPLETE",
                    style = FilmTheme.type.rebate, color = colors.dim)
            }
        } else if (step != null) {
            Row(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                // The agitation bar reads top-down like a burette: the step
                // drains as it runs, and the bands are where your hands have to
                // be. A horizontal bar would have put the next thing you must do
                // off to one side of a number you are already staring at.
                AgitationBar(
                    agitation = step.agitation,
                    durationSec = step.durationSec,
                    elapsedSec = elapsed,
                    accent = stepColor,
                    modifier = Modifier.fillMaxHeight(),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(step.name.uppercase(), style = FilmTheme.type.eyebrow, color = stepColor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatDuration(secondsLeft),
                        style = FilmTheme.type.readout.copy(fontSize = 64.sp, lineHeight = 60.sp),
                        color = if (secondsLeft <= 10 && running) colors.mask else colors.halide,
                        maxLines = 1, softWrap = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("OF ${formatDuration(step.durationSec)}",
                        style = FilmTheme.type.rebate, color = colors.dim)

                    Spacer(Modifier.height(14.dp))
                    // What your hands should be doing, in the same place every
                    // step, so it can be read at a glance from across a sink.
                    val cue = agitationCue(step.agitation, elapsed, step.durationSec, running)
                    Box(
                        Modifier
                            .then(
                                if (agitatingNow) Modifier.halation(colors.magenta, 14.dp, 0.3f, !colors.safelight)
                                else Modifier
                            )
                            .background(if (agitatingNow) colors.magenta else colors.film)
                            .border(1.dp, if (agitatingNow) colors.magenta else colors.edge)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            cue,
                            style = FilmTheme.type.data,
                            color = if (agitatingNow) colors.void else colors.dim,
                        )
                    }
                }
            }
        }
        }

        // ── Controls ──────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            if (finished) {
                DarkroomSegment("DONE", selected = true, accent = colors.cyan,
                    modifier = Modifier.fillMaxWidth()) { vm.stopTimer() }
            } else {
                timer.steps.getOrNull(currentStep + 1)?.let { next ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("NEXT", style = FilmTheme.type.rebate, color = colors.dim)
                        Text(next.name.uppercase(), style = FilmTheme.type.data, color = next.role.color())
                        Text(formatDuration(next.durationSec),
                            style = FilmTheme.type.data, color = colors.dim)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("HAPTIC AGITATION CUES", style = FilmTheme.type.rebate, color = colors.dim)
                    Switch(
                        checked = agitationCues,
                        onCheckedChange = { vm.saveAgitationCues(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.void, checkedTrackColor = colors.cyan,
                            uncheckedThumbColor = colors.dim, uncheckedTrackColor = colors.film,
                        ),
                        modifier = Modifier.height(24.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkroomSegment("↺ RESET", selected = false, accent = colors.cyan,
                        modifier = Modifier.weight(0.5f)) { vm.resetTimerStep() }
                    DarkroomSegment(
                        label = if (running) "❚❚ PAUSE" else "▶ START",
                        selected = true,
                        accent = if (running) colors.yellow else colors.magenta,
                        modifier = Modifier.weight(1.2f),
                        tall = true,
                    ) { vm.toggleTimerRunning() }
                    if (currentStep < timer.steps.lastIndex) {
                        DarkroomSegment("SKIP ›", selected = false, accent = colors.cyan,
                            modifier = Modifier.weight(0.5f)) { vm.skipTimerStep() }
                    }
                }
            }
        }
    }
}

/**
 * The step as a vertical column, with the agitation windows drawn on it.
 *
 * The whole step is the bar; the part that has run is filled. Bands mark the
 * seconds you are meant to be inverting the tank, so the rhythm is something you
 * can see coming rather than a buzz that arrives with no warning — which matters
 * most for stand development, where one band in an hour is the entire schedule.
 */
@Composable
private fun AgitationBar(
    agitation: DevTime.Agitation,
    durationSec: Int,
    elapsedSec: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = FilmTheme.colors
    val windows = remember(agitation, durationSec) { agitation.windows(durationSec) }
    Canvas(
        modifier
            .width(26.dp)
            .background(colors.film)
            .border(1.dp, colors.edge)
            // Decorative: the cue line beside it already says "agitate now" or
            // how long until the next window, in words.
            .clearAndSetSemantics { }
    ) {
        if (durationSec <= 0) return@Canvas
        val h = size.height
        fun y(sec: Int) = h * (sec.toFloat() / durationSec)

        // Elapsed fill
        drawRect(
            color = accent.copy(alpha = 0.16f),
            size = Size(size.width, y(elapsedSec.coerceIn(0, durationSec))),
        )
        // Agitation windows, at least a hairline tall so a five-second burst in
        // a one-hour step does not round away to nothing.
        windows.forEach { w ->
            val top = y(w.first)
            val bottom = maxOf(y(w.last + 1), top + 1.5.dp.toPx())
            drawRect(
                color = colors.magenta,
                topLeft = Offset(0f, top),
                size = Size(size.width, bottom - top),
                alpha = if (elapsedSec > w.last) 0.35f else 1f,
            )
        }
        // Now
        val nowY = y(elapsedSec.coerceIn(0, durationSec))
        drawLine(colors.halide, Offset(0f, nowY), Offset(size.width, nowY), 2.dp.toPx())
    }
}

/** One line of instruction for the hands: agitate, or how long until you do. */
private fun agitationCue(
    agitation: DevTime.Agitation,
    elapsedSec: Int,
    durationSec: Int,
    running: Boolean,
): String = when {
    !running -> "PAUSED"
    agitation.initialSec == Int.MAX_VALUE -> "AGITATE CONTINUOUSLY"
    agitation.isAgitating(elapsedSec) -> "AGITATE NOW"
    else -> agitation.secondsToNext(elapsedSec, durationSec)
        ?.let { "NEXT AGITATION IN ${it}S" }
        ?: "LEAVE IT ALONE"
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
        Text("Steps", color = FilmTheme.colors.cyan, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { i, (stepName, mins, secs) ->
            Column(
                Modifier.fillMaxWidth()
                    .background(FilmTheme.colors.filmRaised)
                    .border(1.dp, FilmTheme.colors.edge)
                    .padding(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Step ${i + 1}", color = FilmTheme.colors.dim, fontSize = 11.sp)
                    if (steps.size > 1) {
                        IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(i) } },
                            Modifier.size(24.dp)) {
                            DyeIcon(FilmIcons.Close, null, size = 14.dp, tint = FilmTheme.colors.mask.copy(alpha = 0.7f))
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
        containerColor = FilmTheme.colors.filmRaised,
        title = { Text("Rolls used for ${chem.name}", color = FilmTheme.colors.yellow) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FilmTheme.colors.cyan,
                    unfocusedBorderColor = FilmTheme.colors.edge, focusedTextColor = FilmTheme.colors.halide,
                    unfocusedTextColor = FilmTheme.colors.halide)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toIntOrNull() ?: current) }) {
                Text("Set", color = FilmTheme.colors.cyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FilmTheme.colors.dim) } }
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

// ─── Dye Layer parts ──────────────────────────────────────────────────────────

/**
 * Colour per step role.
 *
 * Yellow is the developer because yellow means "attention" everywhere else in
 * the app and the developer is the only step where a lost minute is a lost roll.
 * Mask orange for the stop bath (it is acid), cyan for the fixer, violet for the
 * washes, which are structural rather than chemical.
 */
@Composable
fun StepRole.color(): Color = when (this) {
    StepRole.DEVELOP -> FilmTheme.colors.yellow
    StepRole.STOP    -> FilmTheme.colors.mask
    StepRole.FIX     -> FilmTheme.colors.cyan
    StepRole.WASH    -> FilmTheme.colors.violet
    StepRole.FINISH  -> FilmTheme.colors.dim
}

@Composable
private fun DarkroomEyebrow(title: String) {
    val colors = FilmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = FilmTheme.type.eyebrow, color = colors.dim)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = colors.edge)
    }
}

/** Selection carries a bar as well as a colour — see MeterSegment for why. */
@Composable
private fun DarkroomSegment(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = FilmTheme.colors
    Column(
        modifier
            .then(
                if (selected) Modifier.halation(accent, 12.dp, 0.25f, !colors.safelight)
                else Modifier
            )
            .background(colors.film)
            .border(1.dp, if (selected) accent else colors.edge)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = Role.Button },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = if (tall) 15.dp else 11.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = FilmTheme.type.data,
                color = if (selected) accent else colors.dim,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) accent else Color.Transparent)
        )
    }
}

/**
 * Temperature as a scrubbed rebate ruler, matching the meter's calibration
 * control. Resolution is a tenth of a degree because that is the tolerance C-41
 * is specified to, and a control that cannot express the tolerance cannot warn
 * you about it.
 */
@Composable
private fun TemperatureRuler(tempC: Double, onValue: (Double) -> Unit) {
    val colors = FilmTheme.colors
    val lo = DevTime.TEMP_RANGE_C.start
    val hi = DevTime.TEMP_RANGE_C.endInclusive
    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DRAG TO YOUR THERMOMETER", style = FilmTheme.type.rebate, color = colors.dim)
            Text("${"%.1f".format(tempC)} °C",
                style = FilmTheme.type.data.copy(fontSize = 15.sp), color = colors.yellow)
        }
        Spacer(Modifier.height(7.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clipToBounds()
                .background(colors.film)
                .border(1.dp, colors.edge)
                // Operable by TalkBack: a drag-driven Canvas is otherwise a
                // picture of a control rather than a control.
                .semantics {
                    contentDescription = "Working temperature, ${"%.1f".format(tempC)} degrees Celsius"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = tempC.toFloat(),
                        range = lo.toFloat()..hi.toFloat(),
                    )
                    setProgress { target ->
                        onValue(Math.round(target.coerceIn(lo.toFloat(), hi.toFloat()) * 10) / 10.0)
                        true
                    }
                }
                // Drag only, deliberately no tap-to-set: this ruler spans 28 °C
                // in a finger's width, so a stray tap would silently move the
                // chemistry ten degrees.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValue(Math.round((lo + frac * (hi - lo)) * 10) / 10.0)
                    }
                }
        ) {
            val span = (hi - lo).toFloat()
            // A tick per degree, taller at the two references anyone works to:
            // 20 °C for B&W and 38 °C for colour.
            for (t in lo.toInt()..hi.toInt()) {
                val x = size.width * ((t - lo).toFloat() / span)
                val major = t == 20 || t == 38
                drawLine(
                    if (major) colors.dim else colors.dead,
                    Offset(x, if (major) 0f else size.height * 0.35f),
                    Offset(x, size.height),
                    1.dp.toPx(),
                )
            }
            val nub = size.width * ((tempC - lo).toFloat() / span)
            drawLine(colors.yellow, Offset(nub, 0f), Offset(nub, size.height), 3.dp.toPx())
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun formatDuration(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.analogvault.ui.screens

import android.Manifest
import android.content.Context
import com.analogvault.data.network.WeatherResponse
import com.analogvault.ui.WeatherState
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.analogvault.data.model.*
import com.analogvault.data.model.Camera as VaultCamera
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.analogvault.ui.film.ChromaticText
import com.analogvault.ui.film.FilmChip
import com.analogvault.ui.film.FilmChipRow
import com.analogvault.ui.film.FilmStripCard
import com.analogvault.ui.film.rebateLine
import com.analogvault.ui.film.rememberStockAccent
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.analogvault.util.downscalePhotoInPlace
import com.analogvault.util.photoDir
import com.analogvault.util.toDecimalOrNull
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ─── Active Screen ────────────────────────────────────────────────────────────

@Composable
fun ActiveScreen(
    vm: MainViewModel,
    initialSubTab: Int = 0,
    initialRollId: String? = null,
    meterShutter: String = "",
    meterAperture: String = "",
    meterIso: String = "",
    onMeterConsumed: () -> Unit = {},
    onNavigateToDarkroom: () -> Unit = {}
) {
    val rolls   by vm.rolls.collectAsState()
    val films   by vm.films.collectAsState()
    val cameras by vm.cameras.collectAsState()
    val lenses  by vm.lenses.collectAsState()

    var selectedRollId by remember { mutableStateOf<String?>(null) }
    var contactSheetRollId by remember { mutableStateOf<String?>(null) }
    var showLoadSheet  by remember { mutableStateOf(false) }
    // Copy meter values into local state once on arrival; parent clears its copy via callback
    var pendingMeterShutter  by remember { mutableStateOf("") }
    var pendingMeterAperture by remember { mutableStateOf("") }
    var pendingMeterIso      by remember { mutableStateOf("") }
    var showMeterRollPicker  by remember { mutableStateOf(false) }
    var subTab         by remember { mutableIntStateOf(initialSubTab.coerceIn(0, 3)) }

    // Capture meter values whenever the parent supplies them, then immediately tell the
    // parent to clear its copy. Starting blank (not from the param) avoids re-triggering the
    // shot sheet with a stale reading when the Loaded tab is later opened normally.
    LaunchedEffect(meterShutter) {
        if (meterShutter.isNotBlank()) {
            pendingMeterShutter  = meterShutter
            pendingMeterAperture = meterAperture
            pendingMeterIso      = meterIso
            onMeterConsumed()  // tell parent to clear its copy
        }
    }

    val shooting  by remember { derivedStateOf { rolls.filter { !it.finished && !it.developed } } }
    val awaitDev  by remember { derivedStateOf { rolls.filter { it.finished && !it.developed } } }
    val awaitScan by remember { derivedStateOf { rolls.filter { it.developed && !it.scanned } } }
    val done      by remember { derivedStateOf { rolls.filter { it.scanned } } }

    val selectedRoll = selectedRollId?.let { id -> rolls.find { it.id == id } }

    // Open specific roll from dashboard tap
    LaunchedEffect(initialRollId) {
        if (initialRollId != null && selectedRollId == null) selectedRollId = initialRollId
    }

    // When arriving from meter: auto-select if one roll, show picker if multiple
    LaunchedEffect(pendingMeterShutter, shooting.size) {
        if (pendingMeterShutter.isNotBlank() && selectedRollId == null && !showMeterRollPicker) {
            when {
                shooting.size == 1 -> selectedRollId = shooting.first().id
                shooting.size > 1  -> showMeterRollPicker = true
            }
        }
    }

    // Roll picker dialog when arriving from meter with multiple active rolls
    if (showMeterRollPicker) {
        AlertDialog(
            onDismissRequest = {
                showMeterRollPicker = false
                pendingMeterShutter = ""; pendingMeterAperture = ""; pendingMeterIso = ""
            },
            containerColor = Bg3,
            title = { Text("Log shot to which roll?", color = AmberBright) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shooting.forEach { roll ->
                        val film = films.find { it.id == roll.filmId }
                        val cam  = cameras.find { it.id == roll.cameraId }
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Bg4)
                                .border(1.dp, Border, RoundedCornerShape(8.dp))
                                .clickable { selectedRollId = roll.id; showMeterRollPicker = false }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(film?.name ?: "Unknown Film", color = TextPrimary, fontSize = 14.sp)
                                Text(cam?.name ?: "Unknown Camera", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showMeterRollPicker = false
                    pendingMeterShutter = ""; pendingMeterAperture = ""; pendingMeterIso = ""
                }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Contact sheet sits above the list, not above the detail screen: it is a
    // different way of looking at the same roll, not a step deeper into it.
    val contactRoll = contactSheetRollId?.let { id -> rolls.find { it.id == id } }
    if (contactRoll != null) {
        ContactSheetScreen(
            roll = contactRoll,
            film = films.find { it.id == contactRoll.filmId },
            onBack = { contactSheetRollId = null },
            onOpenShot = { contactSheetRollId = null; selectedRollId = contactRoll.id },
        )
        BackHandler { contactSheetRollId = null }
        return
    }

    if (selectedRoll != null) {
        val film = films.find { it.id == selectedRoll.filmId }
        RollDetailScreen(
            roll = selectedRoll, film = film,
            cameras = cameras, lenses = lenses,
            films = films,
            vm = vm,
            onBack = { selectedRollId = null },
            pendingMeterShutter  = pendingMeterShutter,
            pendingMeterAperture = pendingMeterAperture,
            pendingMeterIso      = pendingMeterIso,
            onMeterConsumed = {
                pendingMeterShutter = ""
                pendingMeterAperture = ""
                pendingMeterIso = ""
            }
        )
        return
    }

    // Stage filters, not a second row of tabs.
    //
    // A TabRow underneath the bottom bar gave the screen two competing levels of
    // navigation chrome. These are one property of one list, which is what a
    // chip is for — and unlike tabs they can wrap, so the counts never truncate.
    val stages = listOf(
        "In camera" to shooting,
        "To dev"    to awaitDev,
        "To scan"   to awaitScan,
        "Done"      to done,
    )
    val currentRolls = stages[subTab].second

    Column(Modifier.fillMaxSize()) {
        // The screen used to open on a TabRow, which was doing the job of a
        // title as well as a filter. Removing it in Phase 2 left the list
        // starting cold against the status bar with nothing naming the screen.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            ChromaticText("ROLLS")
            Spacer(Modifier.height(8.dp))
            Text(
                buildList {
                    add("${rolls.size} ROLL${if (rolls.size == 1) "" else "S"}")
                    val frames = rolls.sumOf { it.shots.size }
                    if (frames > 0) add("$frames FRAMES LOGGED")
                }.joinToString(" · "),
                style = FilmTheme.type.eyebrow,
                color = FilmTheme.colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilmChipRow(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
        ) {
            stages.forEachIndexed { i, (label, rollsInStage) ->
                val selected = subTab == i
                FilmChip(
                    text = "$label ${rollsInStage.size}",
                    color = if (selected) FilmTheme.colors.cyan else FilmTheme.colors.dim,
                    filled = selected,
                    onClick = { subTab = i },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentRolls.isEmpty()) {
                item {
                    val msg = when (subTab) {
                        0 -> "No rolls in camera. Load one."
                        1 -> "No rolls awaiting development"
                        2 -> "No rolls awaiting scanning"
                        3 -> "No completed rolls yet"
                        else -> ""
                    }
                    EmptyState(msg)
                }
            }

            items(currentRolls, key = { it.id }) { roll ->
                val film  = films.find { it.id == roll.filmId }
                val cam   = cameras.find { it.id == roll.cameraId }
                val total = roll.totalShots.takeIf { it > 0 } ?: film?.frameCount ?: 36
                val pct   = (roll.shots.size.toFloat() / total).coerceIn(0f, 1f)

                RollListCard(
                    roll = roll, film = film, cam = cam,
                    total = total, pct = pct,
                    onOpen = { selectedRollId = roll.id },
                    // Quick-log only makes sense for rolls still in the camera
                    onQuickLog = if (subTab == 0) ({ vm.quickLogShot(roll.id) }) else null,
                    onContactSheet = { contactSheetRollId = roll.id },
                )
            }

            if (subTab == 0) {
                item {
                    VaultButton("+ Load Film into Camera",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showLoadSheet = true })
                }
            }
            if (subTab == 1 && awaitDev.isNotEmpty()) {
                item {
                    VaultButton("🧪 Open Darkroom / Develop Timers",
                        modifier = Modifier.fillMaxWidth(),
                        ghost = true,
                        onClick = onNavigateToDarkroom)
                }
            }
        }
    }

    if (showLoadSheet) {
        LoadRollSheet(films = films, cameras = cameras, lenses = lenses, rolls = rolls,
            vm = vm, onDismiss = { showLoadSheet = false }) { roll ->
            vm.upsertRoll(roll)
            // Decrement stash quantity or delete if last roll
            val film = films.find { it.id == roll.filmId }
            if (film != null) {
                if (film.quantity > 1) vm.upsertFilm(film.copy(quantity = film.quantity - 1))
                else vm.upsertFilm(film.copy(quantity = 0))
            }
            showLoadSheet = false
        }
    }
}

/**
 * A roll as a length of film, with its provenance printed along the rebate.
 *
 * Camera, load date and process go on the edge rather than into chips because
 * that is where a lab prints them on the real thing, and because they are the
 * facts you want when scanning a list rather than the ones you act on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RollListCard(
    roll: Roll, film: FilmStock?, cam: VaultCamera?,
    total: Int, pct: Float,
    onOpen: () -> Unit,
    onQuickLog: (() -> Unit)? = null,
    onContactSheet: (() -> Unit)? = null,
) {
    val colors = FilmTheme.colors
    val stock = rememberStockAccent(film?.name ?: "", film?.type ?: "", film?.stockAccent ?: "")
    val haptics = LocalHapticFeedback.current
    val exposed = roll.shots.size

    val (stageLabel, stageColor) = when {
        roll.scanned   -> "Archived"  to colors.dead
        roll.developed -> "To scan"   to colors.violet
        roll.finished  -> "To dev"    to colors.yellow
        else           -> "Shooting"  to colors.cyan
    }

    FilmStripCard(
        filmColor = colors.film,
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            // Long-press is the contact sheet, but only once there is something
            // to contact-print. Offering it on an unshot roll would be a
            // gesture that silently does nothing.
            onLongClick = onContactSheet?.takeIf { exposed > 0 }?.let {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            },
        ),
    ) {
        Row(
            Modifier
                .padding(end = 4.dp, top = 10.dp, bottom = 8.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stock.verticalBrush())
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (film?.name ?: "Unknown film").uppercase(),
                    style = FilmTheme.type.stock,
                    color = if (roll.scanned) colors.dim else stock.solid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                FilmChipRow {
                    FilmChip(stageLabel, color = stageColor)
                    FilmChip("$exposed/$total")
                    if (roll.pushIso.toIntOrNull()?.takeIf { it > 0 } != null) {
                        FilmChip("At ISO ${roll.pushIso}", color = colors.magenta)
                    }
                }
            }
            if (onQuickLog != null) {
                // One-tap frame log — defaults from the last shot, edit later
                IconButton(onClick = onQuickLog, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.PlusOne, "Quick-log frame",
                        tint = colors.cyan, modifier = Modifier.size(20.dp))
                }
            }
        }

        Text(
            rebateLine(
                cam?.name,
                roll.startDate.takeIf { it.isNotBlank() }?.let { formatDate(it) },
                roll.devLog?.process?.takeIf { it.isNotBlank() },
                roll.scanLog?.method?.takeIf { it.isNotBlank() },
            ),
            style = FilmTheme.type.rebate,
            color = colors.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        )
    }
}

// ─── Load Roll Sheet ──────────────────────────────────────────────────────────

@Composable
fun LoadRollSheet(
    films: List<FilmStock>, cameras: List<VaultCamera>, lenses: List<Lens>,
    rolls: List<Roll> = emptyList(),
    vm: MainViewModel,
    fixedFilm: FilmStock? = null,   // when loading a specific stash item — film is pre-chosen
    onDismiss: () -> Unit, onSave: (Roll) -> Unit
) {
    var filmId    by remember { mutableStateOf(fixedFilm?.id ?: "") }
    var cameraId  by remember { mutableStateOf("") }
    var lensId    by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var pushIso   by remember { mutableStateOf("") }   // blank = box speed
    var showDatePicker   by remember { mutableStateOf(false) }
    var showAddIsoDialog by remember { mutableStateOf(false) }
    var customIsoInput   by remember { mutableStateOf("") }

    val customIsos by vm.customIsos.collectAsState()

    val busyCameraIds = remember(rolls) {
        rolls.filter { !it.finished && !it.developed }.map { it.cameraId }.toSet()
    }

    val selFilm    = fixedFilm ?: films.find { it.id == filmId }
    val filmName   = selFilm?.name ?: ""
    val selCamera  = cameras.find { it.id == cameraId }
    val cameraName = selCamera?.name ?: ""
    val lensName   = lenses.find { it.id == lensId }?.name ?: ""
    val isBusy     = cameraId.isNotBlank() && cameraId in busyCameraIds
    // Stash flow allows loading into a busy camera (MF backs); loaded-tab flow blocks it.
    val allowBusyLoad = fixedFilm != null

    // Exposure count is taken from the film/roll itself (FilmStock.frameCount) — no manual picker.
    val rollFrames = selFilm?.frameCount?.takeIf { it > 0 } ?: 36

    val isoOptions = remember(selFilm?.iso, customIsos) {
        listOf("Box (${selFilm?.iso ?: "?"})") +
            (Constants.ISOS + customIsos).distinct().sorted().map { it.toString() }
    }

    if (showAddIsoDialog) {
        AlertDialog(
            onDismissRequest = { showAddIsoDialog = false; customIsoInput = "" },
            containerColor = Bg3,
            title = { Text("Add custom ISO", color = AmberBright) },
            text = {
                VaultTextField(customIsoInput, { customIsoInput = it.filter(Char::isDigit) },
                    "ISO value (e.g. 1000)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            },
            confirmButton = {
                TextButton(onClick = {
                    customIsoInput.toIntOrNull()?.let { v -> vm.addCustomIso(v); pushIso = v.toString() }
                    showAddIsoDialog = false; customIsoInput = ""
                }) { Text("Add", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showAddIsoDialog = false; customIsoInput = "" }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
    if (showDatePicker) {
        FullDatePickerDialog(
            initialDate = startDate, includeTime = false,
            onConfirm = { startDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    VaultSheet(if (fixedFilm != null) "Load ${fixedFilm.name}" else "Load Film into Camera", onDismiss) {
        if (fixedFilm != null) {
            TagRow {
                VaultTag(fixedFilm.type.split(" ").first())
                VaultTag("ISO ${fixedFilm.iso}")
                if (fixedFilm.storage.isNotBlank()) VaultTag(fixedFilm.storage)
            }
            Spacer(Modifier.height(12.dp))
        } else {
            VaultDropdown("Film Stock", filmName, films.map { it.name },
                { name -> filmId = films.find { it.name == name }?.id ?: "" })
            Spacer(Modifier.height(10.dp))
        }
        VaultDropdown("Camera",
            if (cameraName.isBlank()) "" else if (isBusy) "$cameraName 📷" else cameraName,
            cameras.map { if (it.id in busyCameraIds) "${it.name} 📷" else it.name },
            { name ->
                val cleanName = name.removeSuffix(" 📷")
                cameraId = cameras.find { it.name == cleanName }?.id ?: ""
                lensId = ""
            })
        Spacer(Modifier.height(10.dp))
        if (selCamera?.lensSystem == "interchangeable") {
            val compatLenses = lenses.filter { lens ->
                Constants.mountCompat(selCamera.mount, lens.mount, selCamera.adapterMounts) != "incompatible"
            }
            val lensOptions = listOf("— No lens —") + compatLenses.map { l ->
                val compat = Constants.mountCompat(selCamera.mount, l.mount, selCamera.adapterMounts)
                "${l.name} [${if (compat == "native") "native" else "via adapter"}]"
            }
            VaultDropdown("Lens", if (lensId.isBlank()) "— No lens —" else lensName, lensOptions,
                { sel -> lensId = if (sel.startsWith("—")) "" else compatLenses.find { l -> sel.startsWith(l.name) }?.id ?: "" })
            Spacer(Modifier.height(10.dp))
        }
        // Load date — picker, not free text
        Column {
            Text("Load Date", color = TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDate(startDate), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                VaultButton("Pick", small = true, ghost = true, onClick = { showDatePicker = true })
            }
        }
        Spacer(Modifier.height(10.dp))
        if (selFilm != null) {
            Text("$rollFrames-exposure roll", color = TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            VaultDropdown(
                "Shoot at ISO",
                if (pushIso.isBlank()) "Box (${selFilm?.iso ?: "?"})" else pushIso,
                isoOptions,
                { pushIso = if (it.startsWith("Box")) "" else it },
                modifier = Modifier.weight(1f)
            )
            VaultButton("+", small = true, ghost = true, onClick = { showAddIsoDialog = true })
        }
        if (pushIso.isNotBlank() && selFilm != null) {
            // Rating film above box speed = push (e.g. 400 film @ EI 800 → push +1)
            val stops = kotlin.math.log2(pushIso.toDouble() / selFilm.iso.toDouble())
            val direction = if (stops > 0) "push +${"%.0f".format(stops)}" else "pull −${"%.0f".format(kotlin.math.abs(stops))}"
            Spacer(Modifier.height(4.dp))
            Text("$direction stop${if (kotlin.math.abs(stops) != 1.0) "s" else ""}",
                color = if (stops > 0) OrangeWarn else BlueInfo, fontSize = 11.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (isBusy) {
            Text(
                if (allowBusyLoad)
                    "⚠ This camera already has a roll loaded. Load anyway for MF cameras with multiple backs."
                else "⚠ This camera already has a roll loaded. Finish or remove it first.",
                color = OrangeWarn, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        VaultButton(
            if (isBusy && allowBusyLoad) "Load Anyway (MF / multiple backs)" else "Load Roll",
            modifier = Modifier.fillMaxWidth(),
            ghost = isBusy && allowBusyLoad,
            enabled = !isBusy || allowBusyLoad,
            onClick = {
                val fid = fixedFilm?.id ?: filmId
                if (fid.isNotBlank() && cameraId.isNotBlank()) {
                    onSave(Roll(id = uid(), filmId = fid, cameraId = cameraId,
                        cameraLensId = lensId, startDate = startDate,
                        pushIso = pushIso, totalShots = rollFrames))
                }
            })
    }
}

// ─── Roll Detail Screen ───────────────────────────────────────────────────────

@Composable
fun RollDetailScreen(
    roll: Roll, film: FilmStock?,
    cameras: List<VaultCamera>, lenses: List<Lens>,
    films: List<FilmStock> = emptyList(),
    vm: MainViewModel, onBack: () -> Unit,
    pendingMeterShutter: String = "",
    pendingMeterAperture: String = "",
    pendingMeterIso: String = "",
    onMeterConsumed: () -> Unit = {}
) {
    val cam   = cameras.find { it.id == roll.cameraId }
    val lens  = lenses.find  { it.id == roll.cameraLensId }
    // Use the exposure count chosen at load time; fall back to the film's frame count.
    val total = roll.totalShots.takeIf { it > 0 } ?: film?.frameCount ?: 36
    val pct   = (roll.shots.size.toFloat() / total).coerceIn(0f, 1f)

    var editingShot    by remember { mutableStateOf<Shot?>(null) }
    var showShotSheet  by remember { mutableStateOf(false) }
    // Meter prefill captured locally so it survives the parent clearing its copy
    // (onMeterConsumed) before the ShotSheet is composed and reads it.
    var meterPrefill   by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // Open shot sheet immediately pre-filled from meter
    LaunchedEffect(pendingMeterShutter) {
        if (pendingMeterShutter.isNotBlank()) {
            meterPrefill  = Triple(pendingMeterShutter, pendingMeterAperture, pendingMeterIso)
            editingShot   = null
            showShotSheet = true
            onMeterConsumed()
        }
    }
    var showDevSheet   by remember { mutableStateOf(false) }
    var showScanSheet  by remember { mutableStateOf(false) }
    var confirmMsg     by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var lightboxPath   by remember { mutableStateOf<String?>(null) }
    var showMap        by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Shot-log exports (SAF)
    val exportBaseName = remember(film, roll.startDate) {
        val name = (film?.name ?: "roll").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_')
        "${name}_${roll.startDate.ifBlank { "undated" }}"
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportRollCsv(it, roll) } }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { vm.exportRollPdf(it, roll) } }

    // System back returns to the roll list before leaving the Loaded tab.
    BackHandler { onBack() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Amber, modifier = Modifier.size(18.dp))
                Text(" All Rolls", color = Amber, fontSize = 13.sp)
            }
        }

        // Roll info card
        item {
            VaultCard {
                Text(film?.name ?: "Unknown Film", color = TextPrimary, fontSize = 17.sp)
                Text("${cam?.name ?: "?"}${if (lens != null) " · ${lens.name}" else ""}",
                    color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                TagRow() {
                    VaultTag("Loaded ${formatDate(roll.startDate)}", textColor = BlueInfo)
                    film?.type?.split(" ")?.firstOrNull()?.let { VaultTag(it) }
                    if (roll.pushIso.isNotBlank() && film != null) {
                        // Rating film above box speed = push
                        val stops = kotlin.math.log2(roll.pushIso.toDouble() / film.iso.toDouble())
                        val label = if (stops > 0) "Push +${"%.0f".format(stops)}" else "Pull −${"%.0f".format(kotlin.math.abs(stops))}"
                        VaultTag("$label @ ISO ${roll.pushIso}", textColor = OrangeWarn)
                    }
                    if (roll.finished)  VaultTag("Finished",  textColor = Amber)
                    if (roll.developed) VaultTag("Developed", textColor = GreenOk)
                    if (roll.scanned)   VaultTag("Scanned",   textColor = GreenOk)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${roll.shots.size}/$total shots", color = TextTertiary, fontSize = 10.sp)
                }
                VaultProgressBar(pct)
                Spacer(Modifier.height(10.dp))
                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    if (!roll.finished) {
                        VaultButton("Mark Finished", small = true, onClick = {
                            confirmMsg = "Mark this roll as finished?" to { vm.markFinished(roll.id, true) }
                        })
                        VaultButton("Unload", small = true, ghost = true, onClick = {
                            val n = roll.shots.size
                            val msg = if (n > 0)
                                "Unload film and return it to stash? $n logged shot${if (n != 1) "s" else ""} will be lost."
                            else "Unload film and return it to stash?"
                            confirmMsg = msg to { vm.unloadRoll(roll); onBack() }
                        })
                    }
                    if (roll.finished && !roll.developed) {
                        VaultButton("Develop", small = true, onClick = { showDevSheet = true })
                        VaultButton("↩ Undo", small = true, ghost = true,
                            onClick = { vm.markFinished(roll.id, false) })
                    }
                    if (roll.developed && !roll.scanned) {
                        VaultButton("Scan", small = true, ghost = true, onClick = { showScanSheet = true })
                        VaultButton("↩ Undo Dev", small = true, ghost = true, onClick = {
                            confirmMsg = "Undo development?" to { vm.markDeveloped(roll.id, null) }
                        })
                    }
                    if (roll.scanned) {
                        VaultButton("↩ Undo Scan", small = true, ghost = true,
                            onClick = { vm.markScanned(roll.id, null) })
                    }
                    VaultButton("Delete", small = true, danger = true, onClick = {
                        confirmMsg = "Delete this roll? ${roll.shots.size} shots will be lost." to {
                            vm.deleteRoll(roll.id); onBack()
                        }
                    })
                }
            }
        }

        // Dev / Scan logs
        roll.devLog?.let { log ->
            item {
                VaultCard {
                    Text("🧪 ${log.process}", color = TextPrimary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    TagRow() {
                        VaultTag(log.developer); VaultTag("${log.temp}°C"); VaultTag("${log.devTime}min")
                    }
                }
            }
        }
        roll.scanLog?.let { log ->
            item {
                VaultCard {
                    Text("🔍 ${log.method}", color = TextPrimary, fontSize = 12.sp)
                    if (log.dpi.isNotBlank()) { Spacer(Modifier.height(4.dp)); VaultTag("${log.dpi} DPI") }
                }
            }
        }

        // Shot log header
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Shot Log", color = Amber, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (roll.shots.isNotEmpty()) {
                        VaultButton("⇪ Export", small = true, ghost = true,
                            onClick = { showExportDialog = true })
                    }
                    val hasGps = roll.shots.any { it.location.contains(",") }
                    if (hasGps) {
                        VaultButton(text = if (showMap) "📋 List" else "🗺 Map", small = true, ghost = true,
                            onClick = { showMap = !showMap })
                    }
                    if (!roll.finished) {
                        VaultButton("+1", small = true, ghost = true,
                            onClick = { vm.quickLogShot(roll.id) })
                        VaultButton("+ Shot", small = true,
                            onClick = {
                                editingShot = null
                                /* meter prefills cleared via pendingMeter* params */
                                showShotSheet = true
                            })
                    }
                }
            }
        }

        // Map view (toggled)
        if (showMap) {
            item(key = "map") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                ) {
                    com.analogvault.ui.components.OsmMapView(
                        shots = roll.shots,
                        rollName = film?.name ?: ""
                    )
                }
            }
        }

        if (roll.shots.isEmpty()) item { EmptyState("No shots logged") }

        items(roll.shots.reversed(), key = { it.id }) { shot ->
            val idx = roll.shots.indexOf(shot) + 1
            VaultCard {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Bg4),
                        contentAlignment = Alignment.Center) {
                        Text("$idx", color = Amber, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        TagRow() {
                            if (shot.shutter.isNotBlank())  VaultTag(shot.shutter, textColor = AmberBright)
                            if (shot.aperture.isNotBlank()) VaultTag("f/${shot.aperture}")
                            if (shot.iso.isNotBlank())      VaultTag("ISO ${shot.iso}")
                        }
                        if (shot.lens.isNotBlank())     { Spacer(Modifier.height(2.dp)); Text(shot.lens, color = TextSecondary, fontSize = 11.sp) }
                        if (shot.location.isNotBlank()) { Spacer(Modifier.height(2.dp)); Text("📍 ${shot.location}", color = BlueInfo, fontSize = 11.sp) }
                        if (shot.notes.isNotBlank())    { Spacer(Modifier.height(2.dp)); Text(shot.notes, color = TextSecondary, fontSize = 11.sp) }
                        if (shot.weather.isNotBlank())  { Spacer(Modifier.height(2.dp)); Text("🌤 ${shot.weather}", color = TextTertiary, fontSize = 10.sp) }
                        Text(shot.date.ifBlank { "—" }, color = TextTertiary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (shot.photoThumbPath.isNotBlank()) {
                            AsyncImage(
                                model = File(shot.photoThumbPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
                                    .clickable { lightboxPath = shot.photoThumbPath }
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Row {
                            IconButton(onClick = { editingShot = shot; showShotSheet = true },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = {
                                confirmMsg = "Delete shot #$idx?" to { vm.deleteShot(roll.id, shot.id) }
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShotSheet) {
        ShotSheet(
            ed = editingShot, roll = roll, lenses = lenses,
            cameras = cameras, films = films, vm = vm,
            prefillShutter   = if (editingShot == null) meterPrefill?.first.orEmpty() else "",
            prefillAperture  = if (editingShot == null) meterPrefill?.second.orEmpty() else "",
            prefillIso       = if (editingShot == null) meterPrefill?.third.orEmpty() else "",
            onDismiss = { showShotSheet = false; editingShot = null; meterPrefill = null }
        ) { shot ->
            if (editingShot != null) vm.updateShot(roll.id, shot)
            else vm.addShot(roll.id, shot)
            showShotSheet = false; editingShot = null; meterPrefill = null
        }
    }
    if (showDevSheet) {
        DevSheet(onDismiss = { showDevSheet = false }) { devLog, cost, selfDev ->
            vm.markDeveloped(roll.id, devLog, cost, selfDev); showDevSheet = false
        }
    }
    if (showScanSheet) {
        ScanSheet(onDismiss = { showScanSheet = false }) { scanLog, cost ->
            vm.markScanned(roll.id, scanLog, cost); showScanSheet = false
        }
    }
    confirmMsg?.let { (msg, action) ->
        ConfirmDialog(msg, confirmLabel = "Confirm",
            onConfirm = { action(); confirmMsg = null },
            onDismiss = { confirmMsg = null })
    }
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Bg3,
            title = { Text("Export shot log", color = AmberBright) },
            text = {
                Text("CSV for spreadsheets, or a printable PDF contact sheet to archive with your negatives.",
                    color = TextSecondary, fontSize = 13.sp)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton("CSV", small = true, ghost = true, onClick = {
                        showExportDialog = false
                        csvLauncher.launch("$exportBaseName.csv")
                    })
                    VaultButton("PDF contact sheet", small = true, onClick = {
                        showExportDialog = false
                        pdfLauncher.launch("$exportBaseName.pdf")
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
    lightboxPath?.let { path ->
        Dialog(onDismissRequest = { lightboxPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f))
                .clickable { lightboxPath = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = File(path), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}

// ─── Shot Sheet ───────────────────────────────────────────────────────────────

@Composable
fun ShotSheet(
    ed: Shot?, roll: Roll, lenses: List<Lens>,
    cameras: List<VaultCamera> = emptyList(),
    films: List<FilmStock> = emptyList(),
    vm: MainViewModel,
    prefillShutter: String = "", prefillAperture: String = "", prefillIso: String = "",
    onDismiss: () -> Unit, onSave: (Shot) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val rollFilm   = remember(roll, films)   { films.find   { it.id == roll.filmId } }
    val rollCamera = remember(roll, cameras) { cameras.find { it.id == roll.cameraId } }
    val defaultIso = prefillIso.ifBlank { roll.pushIso.ifBlank { rollFilm?.iso?.toString() ?: "" } }
    val customIsos by vm.customIsos.collectAsState()
    val isMetric   by vm.isMetric.collectAsState()

    val compatLenses = remember(rollCamera, lenses) {
        if (rollCamera == null || rollCamera.lensSystem == "fixed") emptyList()
        else lenses.filter { l ->
            Constants.mountCompat(rollCamera.mount, l.mount, rollCamera.adapterMounts) != "incompatible"
        }
    }
    val defaultLensName = remember(roll, lenses) {
        if (roll.cameraLensId.isNotBlank()) lenses.find { it.id == roll.cameraLensId }?.name ?: "" else ""
    }

    var shutter   by remember { mutableStateOf(ed?.shutter   ?: prefillShutter) }
    var aperture  by remember { mutableStateOf(ed?.aperture  ?: prefillAperture) }
    var iso       by remember { mutableStateOf(ed?.iso       ?: defaultIso) }
    var lensName  by remember { mutableStateOf(ed?.lens      ?: defaultLensName) }
    var location  by remember { mutableStateOf(ed?.location  ?: "") }
    var notes     by remember { mutableStateOf(ed?.notes     ?: "") }
    var weather   by remember { mutableStateOf(ed?.weather   ?: "") }
    var date      by remember { mutableStateOf(ed?.date ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())) }
    var thumbPath by remember { mutableStateOf(ed?.photoThumbPath ?: "") }
    var gpsLoading    by remember { mutableStateOf(false) }
    var autoLocation  by remember { mutableStateOf(ed == null) }  // auto-fetch for new shots
    var showCamera    by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddIsoDialog by remember { mutableStateOf(false) }
    var customIsoInput by remember { mutableStateOf("") }
    var weatherLoading by remember { mutableStateOf(false) }

    val selectedLens = remember(lensName, lenses) { lenses.find { it.name == lensName } }
    val apertureOptions = remember(selectedLens) {
        val maxAp = selectedLens?.maxAperture?.toDoubleOrNull()
        listOf("") + Constants.APERTURES
            .filter { maxAp == null || it >= maxAp - 0.01 }
            .map { ap -> if (ap == ap.toLong().toDouble()) "f/${ap.toInt()}" else "f/$ap" }
    }
    val allIsos = remember(customIsos) {
        (Constants.ISOS + customIsos).distinct().sorted()
    }

    val weatherState by vm.weatherState.collectAsState()
    // Auto-fill weather on open for new shots
    LaunchedEffect(Unit) {
        if (weather.isBlank() && ed == null) {
            (weatherState as? WeatherState.Success)?.data?.let { d ->
                weather = formatWeatherString(d, isMetric)
            }
        }
        // Auto-fetch location for new shots if toggled on
        if (ed == null && autoLocation && location.isBlank()) {
            gpsLoading = true
            location = getGpsLocationString(context) ?: ""
            gpsLoading = false
        }
    }

    LaunchedEffect(lensName) {
        val maxAp = selectedLens?.maxAperture?.toDoubleOrNull()
        val curAp = aperture.toDoubleOrNull()
        // toString (not %.1f) keeps a '.' decimal separator on comma-locales
        if (maxAp != null && curAp != null && curAp < maxAp - 0.01)
            aperture = maxAp.toString().trimEnd('0').trimEnd('.')
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                thumbPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    savePickedPhoto(context, it)
                }
            }
        }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            gpsLoading = true
            scope.launch { location = getGpsLocationString(context) ?: location; gpsLoading = false }
        }
    }

    // Add custom ISO dialog
    if (showAddIsoDialog) {
        AlertDialog(
            onDismissRequest = { showAddIsoDialog = false; customIsoInput = "" },
            containerColor = Bg3,
            title = { Text("Add custom ISO", color = AmberBright) },
            text = {
                VaultTextField(customIsoInput, { customIsoInput = it.filter(Char::isDigit) },
                    "ISO value (e.g. 1000)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            },
            confirmButton = {
                TextButton(onClick = {
                    customIsoInput.toIntOrNull()?.let { v ->
                        vm.addCustomIso(v)
                        iso = v.toString()
                    }
                    showAddIsoDialog = false; customIsoInput = ""
                }) { Text("Add", color = Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showAddIsoDialog = false; customIsoInput = "" }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showDatePicker) {
        // Shot dates: today back to at most 2 years ago — no future, no distant past.
        val nowY = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        FullDatePickerDialog(
            initialDate = date, includeTime = true,
            yearRange = (nowY - 2)..nowY,
            onConfirm = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    VaultSheet(if (ed != null) "Edit Shot" else "Log Shot", onDismiss) {
        // Shutter + Aperture
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Shutter", shutter, listOf("") + Constants.SHUTTER_SPEEDS,
                { shutter = it }, modifier = Modifier.weight(1f))
            VaultDropdown("Aperture", if (aperture.isBlank()) "" else "f/$aperture",
                apertureOptions,
                { aperture = it.removePrefix("f/") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        // ISO + custom ISO button + Lens
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                VaultDropdown("ISO", iso, listOf("") + allIsos.map { it.toString() }, { iso = it })
            }
            VaultButton("+", small = true, ghost = true, onClick = { showAddIsoDialog = true })
            val lensOptions = listOf("") + (compatLenses.ifEmpty { lenses }).map { it.name }
            Column(Modifier.weight(1f)) {
                VaultDropdown("Lens", lensName, lensOptions, { lensName = it })
            }
        }
        Spacer(Modifier.height(10.dp))
        // Location row with toggle and fetch button
        Column {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Location", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("Auto", color = TextTertiary, fontSize = 11.sp)
                Switch(checked = autoLocation, onCheckedChange = { autoLocation = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark),
                    modifier = Modifier.height(20.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VaultTextField(
                    value = if (gpsLoading) "Getting fix…" else location,
                    onValueChange = { location = it },
                    label = "",
                    modifier = Modifier.weight(1f),
                    enabled = !autoLocation && !gpsLoading
                )
                IconButton(onClick = {
                    locationPermLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }) {
                    if (gpsLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
                    else Icon(Icons.Default.LocationOn, "GPS", tint = if (autoLocation) Amber else TextTertiary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Weather row with fetch button
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VaultTextField(weather, { weather = it }, "Weather", modifier = Modifier.weight(1f))
            IconButton(onClick = {
                weatherLoading = true
                scope.launch {
                    // First try to populate from already-loaded weather state
                    val loaded = (vm.weatherState.value as? WeatherState.Success)?.data
                    if (loaded != null) {
                        weather = formatWeatherString(loaded, isMetric)
                    } else {
                        // No weather loaded yet — fetch now using GPS, then await result
                        val coords = getCurrentLatLon(context)
                        if (coords != null) {
                            vm.fetchWeather(coords.first, coords.second)
                            // Suspend until the fetch completes (Success or Error) — no arbitrary delay
                            val result = vm.weatherState.first { it !is WeatherState.Loading }
                            (result as? WeatherState.Success)?.data?.let { d ->
                                weather = formatWeatherString(d, isMetric)
                            }
                        }
                    }
                    weatherLoading = false
                }
            }) {
                if (weatherLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
                else Icon(Icons.Default.Cloud, "Fetch weather", tint = Amber)
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(10.dp))
        // Date & time with picker button
        Column {
            Text("Date & Time", color = TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                VaultButton("Pick", small = true, ghost = true, onClick = { showDatePicker = true })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultButton("📷 Camera", small = true, ghost = true,
                onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) })
            VaultButton("🖼 Gallery", small = true, ghost = true, onClick = { pickImage.launch("image/*") })
            if (thumbPath.isNotBlank())
                VaultButton("✕ Remove", small = true, ghost = true, onClick = { thumbPath = "" })
        }
        if (thumbPath.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(model = File(thumbPath), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)))
        }
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Shot", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Shot(id = ed?.id ?: uid(), shutter = shutter, aperture = aperture, iso = iso,
                lens = lensName, location = location, notes = notes, weather = weather,
                date = date, photoThumbPath = thumbPath))
        })
    }

    if (showCamera) {
        CameraXCaptureDialog(
            onCapture = { path -> thumbPath = path; showCamera = false },
            onDismiss = { showCamera = false }
        )
    }
}

// ─── CameraX capture ─────────────────────────────────────────────────────────

@Composable
fun CameraXCaptureDialog(onCapture: (String) -> Unit, onDismiss: () -> Unit) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()
    var imageCapture   by remember { mutableStateOf<ImageCapture?>(null) }
    var providerRef    by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // The camera is bound to the Activity lifecycle, so it would keep running
    // (privacy indicator on, battery draining) after the dialog closes.
    DisposableEffect(Unit) {
        onDispose { providerRef?.unbindAll() }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            AndroidView(factory = { ctx ->
                val pv = PreviewView(ctx)
                ProcessCameraProvider.getInstance(ctx).addListener({
                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview  = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val capture  = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    imageCapture = capture
                    providerRef  = provider
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    } catch (e: Exception) { e.printStackTrace() }
                }, ContextCompat.getMainExecutor(ctx))
                pv
            }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(28.dp))
                    }
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Amber)
                        .clickable {
                            val ic = imageCapture ?: return@clickable
                            scope.launch {
                                // capturePhoto resumes with ImageCaptureException on failure —
                                // uncaught it would crash the app
                                val path = try {
                                    capturePhoto(context, ic, ContextCompat.getMainExecutor(context))
                                } catch (e: Exception) { null }
                                if (path != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        downscalePhotoInPlace(File(path))
                                    }
                                    onCapture(path)
                                }
                            }
                        }, contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Camera, contentDescription = "Capture", tint = Bg, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

// ─── Dev / Scan sheets ────────────────────────────────────────────────────────

@Composable
fun DevSheet(onDismiss: () -> Unit, onSave: (DevLog, Double, Boolean) -> Unit) {
    var process   by remember { mutableStateOf(Constants.DEVELOP_PROCESSES[0]) }
    var developer by remember { mutableStateOf("") }
    var dilution  by remember { mutableStateOf("") }
    var temp      by remember { mutableStateOf("20") }
    var devTime   by remember { mutableStateOf("") }
    var notes     by remember { mutableStateOf("") }
    var isSelfDev by remember { mutableStateOf(true) }
    var devCost   by remember { mutableStateOf("") }

    VaultSheet("Develop Roll", onDismiss) {
        VaultDropdown("Process", process, Constants.DEVELOP_PROCESSES, { process = it })
        Spacer(Modifier.height(10.dp))
        AutoCompleteField(developer, { developer = it }, "Developer", Constants.DEV_DB)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(dilution, { dilution = it }, "Dilution", modifier = Modifier.weight(1f), placeholder = "1:31")
            VaultTextField(temp, { temp = it }, "Temp (°C)", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(devTime, { devTime = it }, "Dev Time (min)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        Spacer(Modifier.height(10.dp))
        // Cost tracking
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = isSelfDev, onCheckedChange = { isSelfDev = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark))
            Text(if (isSelfDev) "Self-developed" else "Lab development", color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        VaultTextField(devCost, { devCost = it },
            if (isSelfDev) "Chemical cost (optional)" else "Lab cost (optional)",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Dev Log", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(DevLog(process = process, developer = developer, dilution = dilution,
                temp = temp, devTime = devTime, notes = notes),
                devCost.toDecimalOrNull() ?: 0.0, isSelfDev)
        })
    }
}

@Composable
fun ScanSheet(onDismiss: () -> Unit, onSave: (ScanLog, Double) -> Unit) {
    var method   by remember { mutableStateOf(Constants.SCAN_METHODS[0]) }
    var dpi      by remember { mutableStateOf("") }
    var software by remember { mutableStateOf("") }
    var scanCost by remember { mutableStateOf("") }
    var notes    by remember { mutableStateOf("") }

    VaultSheet("Log Scan", onDismiss) {
        VaultDropdown("Method", method, Constants.SCAN_METHODS, { method = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(dpi, { dpi = it }, "DPI", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            VaultTextField(software, { software = it }, "Software", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(scanCost, { scanCost = it }, "Scan cost (optional)",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Scan Log", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(ScanLog(method = method, dpi = dpi, software = software, notes = notes),
                scanCost.toDecimalOrNull() ?: 0.0)
        })
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private suspend fun capturePhoto(context: Context, imageCapture: ImageCapture, executor: Executor): String? =
    suspendCancellableCoroutine { cont ->
        val file = File(photoDir(context), "shot_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(), executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { cont.resume(file.absolutePath) }
                override fun onError(e: ImageCaptureException) { cont.resumeWithException(e) }
            })
    }

private fun savePickedPhoto(context: Context, uri: Uri): String {
    val file = File(photoDir(context), "pick_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { input.copyTo(it) }
    }
    downscalePhotoInPlace(file)
    return file.absolutePath
}

/** Current (lat, lon) via a fresh high-accuracy fix, falling back to last known location.
 *  Non-private: also used by MainViewModel.quickLogShot. */
suspend fun getCurrentLatLon(context: Context): Pair<Double, Double>? =
    suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts    = CancellationTokenSource()
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(loc.latitude to loc.longitude)
                    else {
                        try {
                            client.lastLocation.addOnSuccessListener { last ->
                                cont.resume(if (last != null) last.latitude to last.longitude else null)
                            }.addOnFailureListener { cont.resume(null) }
                        } catch (e: SecurityException) { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) { cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }

/**
 * Locale-invariant "lat, lon" string (~1 m precision). Locale.US is required:
 * comma-decimal locales would produce "40,416775, -3,703790", which
 * parseLatLon (and therefore the shot map) cannot read back.
 */
fun formatLatLon(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.6f, %.6f", lat, lon)

private suspend fun getGpsLocationString(context: Context): String? =
    getCurrentLatLon(context)?.let { (lat, lon) -> formatLatLon(lat, lon) }

/** Formats a [WeatherResponse] into the compact string stored on a [Shot].
 *  Non-private: also used by MainViewModel.quickLogShot. */
fun formatWeatherString(data: WeatherResponse, isMetric: Boolean): String {
    val unit = if (isMetric) "°C" else "°F"
    val temp = if (isMetric) data.main.temp else data.main.temp * 9.0 / 5.0 + 32.0
    // API always returns m/s (fetch uses units=metric) — convert for mph display
    val wind = if (isMetric) data.wind.speed else data.wind.speed * 2.237
    return buildString {
        append("${"%.0f".format(temp)}$unit")
        data.weather.firstOrNull()?.description?.let { append(", $it") }
        append(", ${data.clouds.all}% cloud")
        if (data.wind.speed > 0) append(", wind ${"%.1f".format(wind)}${if (isMetric) "m/s" else "mph"}")
    }
}

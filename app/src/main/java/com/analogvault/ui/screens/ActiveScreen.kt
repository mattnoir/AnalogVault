package com.analogvault.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
    meterIso: String = ""
) {
    val rolls   by vm.rolls.collectAsState()
    val films   by vm.films.collectAsState()
    val cameras by vm.cameras.collectAsState()
    val lenses  by vm.lenses.collectAsState()

    var selectedRollId by remember { mutableStateOf<String?>(null) }
    var showLoadSheet  by remember { mutableStateOf(false) }
    // If we arrived from meter, show shot sheet on the most recent shooting roll
    var pendingMeterShutter  by remember { mutableStateOf(meterShutter) }
    var pendingMeterAperture by remember { mutableStateOf(meterAperture) }
    var pendingMeterIso      by remember { mutableStateOf(meterIso) }
    var subTab         by remember { mutableIntStateOf(initialSubTab.coerceIn(0, 3)) }

    val shooting  = rolls.filter { !it.finished && !it.developed }
    val awaitDev  = rolls.filter { it.finished && !it.developed }
    val awaitScan = rolls.filter { it.developed && !it.scanned }
    val done      = rolls.filter { it.scanned }

    // Resolve sentinel: open first shooting roll when coming from meter
    if (selectedRollId == "__OPEN_FIRST_SHOOTING__" && shooting.isNotEmpty()) {
        selectedRollId = shooting.first().id
    }

    val selectedRoll = selectedRollId?.let { id -> rolls.find { it.id == id } }

    // Open specific roll from dashboard tap
    LaunchedEffect(initialRollId) {
        if (initialRollId != null && selectedRollId == null) {
            selectedRollId = initialRollId
        }
    }

    // Auto-navigate to most recent active roll when coming from meter
    // (shooting list is derived below; we just set flag here and let the list settle)
    LaunchedEffect(pendingMeterShutter) {
        if (pendingMeterShutter.isNotBlank() && selectedRollId == null) {
            selectedRollId = "__OPEN_FIRST_SHOOTING__"
        }
    }

    if (selectedRoll != null) {
        val film = films.find { it.id == selectedRoll.filmId }
        RollDetailScreen(
            roll = selectedRoll, film = film,
            cameras = cameras, lenses = lenses, vm = vm,
            onBack = { selectedRollId = null },
            pendingMeterShutter  = pendingMeterShutter.also  { pendingMeterShutter = "" },
            pendingMeterAperture = pendingMeterAperture.also { pendingMeterAperture = "" },
            pendingMeterIso      = pendingMeterIso.also      { pendingMeterIso = "" }
        )
        return
    }

    // Filter rolls per tab (defined above)
    val tabLabels = listOf(
        "In Camera (${shooting.size})",
        "Dev (${awaitDev.size})",
        "Scan (${awaitScan.size})",
        "Done (${done.size})"
    )

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = Bg2,
            contentColor = Amber,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[subTab]), color = Amber
                )
            }
        ) {
            tabLabels.forEachIndexed { i, label ->
                Tab(
                    selected = subTab == i,
                    onClick = { subTab = i },
                    text = { Text(label, fontSize = 11.sp) },
                    selectedContentColor = Amber,
                    unselectedContentColor = TextTertiary
                )
            }
        }

        val currentRolls = when (subTab) {
            0 -> shooting; 1 -> awaitDev; 2 -> awaitScan; 3 -> done; else -> emptyList()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentRolls.isEmpty()) {
                item {
                    val msg = when (subTab) {
                        0 -> "No rolls in camera"
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
                val total = film?.shots ?: 36
                val pct   = (roll.shots.size.toFloat() / total).coerceIn(0f, 1f)

                RollListCard(
                    roll = roll, film = film, cam = cam,
                    total = total, pct = pct,
                    onOpen = { selectedRollId = roll.id }
                )
            }

            if (subTab == 0) {
                item {
                    VaultButton("+ Load Film into Camera",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showLoadSheet = true })
                }
            }
        }
    }

    if (showLoadSheet) {
        LoadRollSheet(films = films, cameras = cameras, lenses = lenses,
            onDismiss = { showLoadSheet = false }) { roll ->
            vm.upsertRoll(roll); showLoadSheet = false
        }
    }
}

@Composable
private fun RollListCard(
    roll: Roll, film: FilmStock?, cam: VaultCamera?,
    total: Int, pct: Float,
    onOpen: () -> Unit
) {
    VaultCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(film?.name ?: "Unknown Film", color = TextPrimary, fontSize = 15.sp)
                Text(cam?.name ?: "Unknown Camera", color = TextSecondary, fontSize = 12.sp)
            }
            val (tagLabel, tagColor) = when {
                roll.scanned   -> "Scanned"   to GreenOk
                roll.developed -> "Developed" to GreenOk
                roll.finished  -> "Finished"  to Amber
                else           -> "Shooting"  to BlueInfo
            }
            VaultTag(tagLabel, textColor = tagColor)
        }
        if (roll.startDate.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("Loaded ${formatDate(roll.startDate)}", color = TextTertiary, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${roll.shots.size}/$total", color = TextTertiary, fontSize = 10.sp)
            Text("${(pct * 100).toInt()}%", color = TextTertiary, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        VaultProgressBar(pct)
        // Archive badge for done rolls
        if (roll.scanned && roll.devLog != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VaultTag(roll.devLog.process.ifBlank { "Developed" }, textColor = GreenOk)
                if (roll.scanLog != null) VaultTag(roll.scanLog.method.ifBlank { "Scanned" }, textColor = BlueInfo)
            }
        }
    }
}

// ─── Load Roll Sheet ──────────────────────────────────────────────────────────

@Composable
fun LoadRollSheet(
    films: List<FilmStock>, cameras: List<VaultCamera>, lenses: List<Lens>,
    onDismiss: () -> Unit, onSave: (Roll) -> Unit
) {
    var filmId    by remember { mutableStateOf("") }
    var cameraId  by remember { mutableStateOf("") }
    var lensId    by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    val filmName   = films.find { it.id == filmId }?.name ?: ""
    val cameraName = cameras.find { it.id == cameraId }?.name ?: ""
    val lensName   = lenses.find { it.id == lensId }?.name ?: ""
    val selCamera  = cameras.find { it.id == cameraId }

    VaultSheet("Load Film into Camera", onDismiss) {
        VaultDropdown("Film Stock", filmName, films.map { it.name },
            { name -> filmId = films.find { it.name == name }?.id ?: "" })
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Camera", cameraName, cameras.map { it.name },
            { name -> cameraId = cameras.find { it.name == name }?.id ?: ""; lensId = "" })
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
        VaultTextField(startDate, { startDate = it }, "Load Date (YYYY-MM-DD)")
        Spacer(Modifier.height(16.dp))
        VaultButton("Load Roll", modifier = Modifier.fillMaxWidth(), onClick = {
            if (filmId.isNotBlank() && cameraId.isNotBlank()) {
                onSave(Roll(id = uid(), filmId = filmId, cameraId = cameraId,
                    cameraLensId = lensId, startDate = startDate))
            }
        })
    }
}

// ─── Roll Detail Screen ───────────────────────────────────────────────────────

@Composable
fun RollDetailScreen(
    roll: Roll, film: FilmStock?,
    cameras: List<VaultCamera>, lenses: List<Lens>,
    vm: MainViewModel, onBack: () -> Unit,
    pendingMeterShutter: String = "",
    pendingMeterAperture: String = "",
    pendingMeterIso: String = ""
) {
    val cam   = cameras.find { it.id == roll.cameraId }
    val lens  = lenses.find  { it.id == roll.cameraLensId }
    val total = film?.shots ?: 36
    val pct   = (roll.shots.size.toFloat() / total).coerceIn(0f, 1f)

    var showShotSheet  by remember { mutableStateOf(false) }
    // Open shot sheet immediately pre-filled from meter
    LaunchedEffect(pendingMeterShutter) {
        if (pendingMeterShutter.isNotBlank()) {
            showShotSheet = true
        }
    }
    var editingShot    by remember { mutableStateOf<Shot?>(null) }
    var showDevSheet   by remember { mutableStateOf(false) }
    var showScanSheet  by remember { mutableStateOf(false) }
    var confirmMsg     by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var lightboxPath   by remember { mutableStateOf<String?>(null) }
    // Pre-filled values coming from the light meter

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.ArrowBack, null, tint = Amber, modifier = Modifier.size(18.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaultTag("Loaded ${formatDate(roll.startDate)}", textColor = BlueInfo)
                    film?.type?.split(" ")?.firstOrNull()?.let { VaultTag(it) }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    val hasGps = roll.shots.any { it.location.contains(",") }
                    if (hasGps) {
                        VaultButton(text = "🗺 Map", small = true, ghost = true,
                            onClick = { /* map view handled inline below */ })
                    }
                    if (!roll.finished) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            vm = vm,
            prefillShutter   = if (editingShot == null) pendingMeterShutter else "",
            prefillAperture  = if (editingShot == null) pendingMeterAperture else "",
            prefillIso       = if (editingShot == null) pendingMeterIso else "",
            onDismiss = { showShotSheet = false; editingShot = null }
        ) { shot ->
            if (editingShot != null) vm.updateShot(roll.id, shot)
            else vm.addShot(roll.id, shot)
            showShotSheet = false; editingShot = null
        }
    }
    if (showDevSheet) {
        DevSheet(onDismiss = { showDevSheet = false }) { vm.markDeveloped(roll.id, it); showDevSheet = false }
    }
    if (showScanSheet) {
        ScanSheet(onDismiss = { showScanSheet = false }) { vm.markScanned(roll.id, it); showScanSheet = false }
    }
    confirmMsg?.let { (msg, action) ->
        ConfirmDialog(msg, confirmLabel = "Confirm",
            onConfirm = { action(); confirmMsg = null },
            onDismiss = { confirmMsg = null })
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
    ed: Shot?, roll: Roll, lenses: List<Lens>, vm: MainViewModel,
    prefillShutter: String = "", prefillAperture: String = "", prefillIso: String = "",
    onDismiss: () -> Unit, onSave: (Shot) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var shutter   by remember { mutableStateOf(ed?.shutter   ?: prefillShutter) }
    var aperture  by remember { mutableStateOf(ed?.aperture  ?: prefillAperture) }
    var iso       by remember { mutableStateOf(ed?.iso       ?: prefillIso) }
    var lensName  by remember { mutableStateOf(ed?.lens      ?: "") }
    var location  by remember { mutableStateOf(ed?.location  ?: "") }
    var notes     by remember { mutableStateOf(ed?.notes     ?: "") }
    var weather   by remember { mutableStateOf(ed?.weather   ?: "") }
    // Date + time
    var date      by remember { mutableStateOf(ed?.date ?: run {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    }) }
    var thumbPath by remember { mutableStateOf(ed?.photoThumbPath ?: "") }
    var gpsLoading by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    // Auto-fill weather from vm.weatherState
    val weatherState by vm.weatherState.collectAsState()
    LaunchedEffect(Unit) {
        if (weather.isBlank()) {
            val ws = weatherState
            if (ws is com.analogvault.ui.WeatherState.Success) {
                val d = ws.data
                weather = buildString {
                    append("${"%.0f".format(d.main.temp)}°C")
                    d.weather.firstOrNull()?.description?.let { append(", $it") }
                    append(", ${d.clouds.all}% cloud")
                    if (d.wind.speed > 0) append(", wind ${d.wind.speed}m/s")
                }
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { thumbPath = saveUriToCache(context, it) }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true
    }
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            gpsLoading = true
            scope.launch { location = getGps(context) ?: location; gpsLoading = false }
        }
    }

    VaultSheet(if (ed != null) "Edit Shot" else "Log Shot", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Shutter", shutter, listOf("") + Constants.SHUTTER_SPEEDS,
                { shutter = it }, modifier = Modifier.weight(1f))
            VaultDropdown("Aperture", if (aperture.isBlank()) "" else "f/$aperture",
                listOf("") + Constants.APERTURES.map { "f/$it" },
                { aperture = it.removePrefix("f/") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("ISO", iso, listOf("") + Constants.ISOS.map { it.toString() },
                { iso = it }, modifier = Modifier.weight(1f))
            val lensOptions = listOf("") + lenses.map { it.name }
            VaultDropdown("Lens", lensName, lensOptions, { lensName = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VaultTextField(location, { location = it }, "Location (GPS)", modifier = Modifier.weight(1f))
            IconButton(onClick = {
                locationPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }) {
                if (gpsLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
                else Icon(imageVector = Icons.Default.LocationOn, contentDescription = "GPS", tint = Amber)
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(weather, { weather = it }, "Weather notes")
        Spacer(Modifier.height(4.dp))
        Text("Auto-filled from Weather tab if available", color = TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(10.dp))
        VaultTextField(date, { date = it }, "Date & Time (YYYY-MM-DD HH:mm)")
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
                                val path = capturePhoto(context, ic, ContextCompat.getMainExecutor(context))
                                if (path != null) onCapture(path)
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
fun DevSheet(onDismiss: () -> Unit, onSave: (DevLog) -> Unit) {
    var process   by remember { mutableStateOf(Constants.DEVELOP_PROCESSES[0]) }
    var developer by remember { mutableStateOf("") }
    var dilution  by remember { mutableStateOf("") }
    var temp      by remember { mutableStateOf("20") }
    var devTime   by remember { mutableStateOf("") }
    var notes     by remember { mutableStateOf("") }

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
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Dev Log", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(DevLog(process = process, developer = developer, dilution = dilution,
                temp = temp, devTime = devTime, notes = notes))
        })
    }
}

@Composable
fun ScanSheet(onDismiss: () -> Unit, onSave: (ScanLog) -> Unit) {
    var method   by remember { mutableStateOf(Constants.SCAN_METHODS[0]) }
    var dpi      by remember { mutableStateOf("") }
    var software by remember { mutableStateOf("") }
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
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Scan Log", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(ScanLog(method = method, dpi = dpi, software = software, notes = notes))
        })
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private suspend fun capturePhoto(context: Context, imageCapture: ImageCapture, executor: Executor): String? =
    suspendCancellableCoroutine { cont ->
        val dir  = File(context.cacheDir, "camera_photos").also { it.mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(), executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { cont.resume(file.absolutePath) }
                override fun onError(e: ImageCaptureException) { cont.resumeWithException(e) }
            })
    }

private fun saveUriToCache(context: Context, uri: Uri): String {
    val dir  = File(context.cacheDir, "camera_photos").also { it.mkdirs() }
    val file = File(dir, "pick_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { it.copyTo(file.outputStream()) }
    return file.absolutePath
}

private suspend fun getGps(context: Context): String? =
    suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts    = CancellationTokenSource()
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    cont.resume(if (loc != null) "%.5f, %.5f".format(loc.latitude, loc.longitude) else null)
                }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) { cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }

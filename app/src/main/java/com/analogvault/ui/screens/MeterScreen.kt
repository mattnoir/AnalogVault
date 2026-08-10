package com.analogvault.ui.screens

import android.Manifest
import android.hardware.camera2.CaptureResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.analogvault.data.model.ZoomLevel
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.analogvault.util.Exposure
import kotlin.math.*
import kotlin.math.roundToInt

// ─── Entry ────────────────────────────────────────────────────────────────────

@Composable
fun MeterScreen(
    vm: MainViewModel,
    onUseInShot: ((shutter: String, aperture: String, iso: String) -> Unit)? = null
) {
    val zoomLevels by vm.zoomLevels.collectAsState()
    val displayZooms = remember(zoomLevels) {
        zoomLevels.distinctBy { it.label to it.mm }.sortedBy { it.mm }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasCamPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamPerm = it }
    // Only prompt when not already granted — avoids re-asking on every tab visit
    LaunchedEffect(Unit) { if (!hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA) }
    MeterContent(vm, displayZooms, hasCamPerm, { permLauncher.launch(Manifest.permission.CAMERA) }, onUseInShot)
}

// ─── Metering engine state ────────────────────────────────────────────────────

data class MeterReading(
    /** Scene EV derived from Camera2 AE metadata — device/scene truth */
    val sceneEV: Double,
    /** Raw sensor ISO used by AE */
    val sensorIso: Int,
    /** Raw shutter used by AE, seconds */
    val sensorShutterSec: Double,
    /** Lens aperture used by AE */
    val sensorAperture: Double,
    val source: String = "camera2"
)

// ─── Main content ─────────────────────────────────────────────────────────────
// CameraX's Camera2Interop is "experimental" but in 1.3.x it is NOT a @RequiresOptIn
// marker, so neither @OptIn nor a compiler opt-in flag has any effect (the IDE may still
// flag the usage — a false positive; the Gradle build is the source of truth and compiles).

@Composable
fun MeterContent(
    vm: MainViewModel,
    zoomLevels: List<ZoomLevel>,
    hasCamPerm: Boolean,
    onRequestPerm: () -> Unit,
    onUseInShot: ((shutter: String, aperture: String, iso: String) -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── User inputs — persisted via settings so the per-device calibration
    //    (and last-used ISO/shutter/metering/mode) survive restarts ───────────
    val filmIso     by vm.meterIso.collectAsState()
    val shutter     by vm.meterShutter.collectAsState()
    val metering    by vm.meterMetering.collectAsState()
    val calibThirds by vm.meterCalibThirds.collectAsState()
    val meterMode   by vm.meterMode.collectAsState()       // shutter | aperture | table
    val fixedAperture by vm.meterAperture.collectAsState()
    val recipFilm   by vm.recipFilm.collectAsState()
    // Draft tracks the slider while dragging; persisted on release
    var calibDraft  by remember(calibThirds) { mutableIntStateOf(calibThirds) }
    val calibOffset = calibDraft / 3.0

    // ── Live meter state — lives in the ViewModel so readings, EV lock and
    //    zone marks survive tab navigation ────────────────────────────────────
    val liveReading by vm.meterReading.collectAsState()
    val evLocked    by vm.meterEvLocked.collectAsState()
    val manualEV    by vm.meterManualEv.collectAsState()
    val zoneEnabled by vm.meterZoneEnabled.collectAsState()
    val zone        by vm.meterZone.collectAsState()
    val shadowEv    by vm.meterShadowEv.collectAsState()
    val highlightEv by vm.meterHighlightEv.collectAsState()

    // ── Camera ────────────────────────────────────────────────────────────────
    var cameraOn     by remember { mutableStateOf(false) }
    var cameraCtrl   by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfoObj by remember { mutableStateOf<CameraInfo?>(null) }
    var providerRef  by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var appliedRegionKey by remember { mutableStateOf<String?>(null) }
    // Tap-to-meter point as a fraction of the preview size; null = center
    var tapPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // The camera binds to the Activity lifecycle; without an explicit unbind it
    // keeps running (privacy indicator on, battery draining) after the user
    // navigates away from this tab.
    DisposableEffect(Unit) {
        onDispose { providerRef?.unbindAll() }
    }

    // Base EV before zone placement: locked → manual, live → calibrated reading
    val baseEV = when {
        evLocked            -> manualEV
        liveReading != null -> (liveReading!!.sceneEV + calibOffset).coerceIn(-2.0, 22.0)
        else                -> manualEV
    }
    val zoneActive  = zoneEnabled && metering == Constants.METERING_SPOT
    val effectiveEV = if (zoneActive) baseEV + Exposure.zoneOffsetEv(zone) else baseEV

    // ── Zoom ──────────────────────────────────────────────────────────────────
    var activeZoom   by remember { mutableStateOf<ZoomLevel?>(null) }
    var showZoomEdit by remember { mutableStateOf(false) }

    // ── Solved exposure (mode-dependent) ─────────────────────────────────────
    // Shutter-priority / table: fix shutter → solve aperture (snapped to third-stops).
    val apExact   = Constants.calcAperture(filmIso, shutter, effectiveEV)
    val apSnapped = Constants.nearestStandardAperture(apExact)
    // Aperture-priority: fix aperture → solve shutter (snapped to the standard scale).
    val secExact  = Exposure.solveShutterSec(filmIso, fixedAperture, effectiveEV)
    val shSnapped = Exposure.nearestStandardShutter(secExact)

    // Shutter seconds the reciprocity helper should correct
    val displaySec = if (meterMode == "aperture") secExact else Constants.evalShutter(shutter)

    // ── Layout ───────────────────────────────────────────────────────────────
    // Outer Column is NOT scrollable — AndroidView breaks verticalScroll.
    // Camera is fixed height. Scrollable section sits below.
    Column(Modifier.fillMaxSize()) {

        // ── CAMERA VIEWFINDER — compact fixed height, always on top ───────────
        if (cameraOn && hasCamPerm) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Bg2)
                    // Tap-to-meter: place the AE region where the user taps
                    // (SPOT / CENTRE); double-tap recenters.
                    .pointerInput(metering) {
                        detectTapGestures(
                            onTap = { ofs ->
                                if (metering == Constants.METERING_SPOT ||
                                    metering == Constants.METERING_CENTRE
                                ) {
                                    tapPoint = (ofs.x / size.width) to (ofs.y / size.height)
                                }
                            },
                            onDoubleTap = { tapPoint = null }
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        val pv = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()

                            // Build preview with Camera2Interop to capture metadata
                            val previewBuilder = Preview.Builder()
                            val captureCallback = object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                                private var lastPostMs = 0L
                                private var lastEv = Double.NaN
                                override fun onCaptureCompleted(
                                    session: android.hardware.camera2.CameraCaptureSession,
                                    request: android.hardware.camera2.CaptureRequest,
                                    result: android.hardware.camera2.TotalCaptureResult
                                ) {
                                    val isoVal    = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
                                    val shutterNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
                                    val apertureF = result.get(CaptureResult.LENS_APERTURE) ?: return

                                    val shutterSec = shutterNs / 1_000_000_000.0
                                    val ev = Exposure.evFromSensor(isoVal, shutterSec, apertureF.toDouble())

                                    // Throttle: a state write per camera frame (~30 Hz) recomposes the
                                    // screen constantly; ~4 Hz is plenty for a meter readout. Large EV
                                    // jumps still post immediately so the needle feels responsive.
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - lastPostMs < 250 && abs(ev - lastEv) < 0.05) return
                                    lastPostMs = now
                                    lastEv = ev

                                    vm.onMeterReading(isoVal, shutterSec, apertureF.toDouble())
                                }
                            }
                            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(captureCallback)
                            val preview = previewBuilder.build().also { it.setSurfaceProvider(pv.surfaceProvider) }

                            try {
                                provider.unbindAll()
                                val cam = provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
                                )
                                cameraCtrl    = cam.cameraControl
                                cameraInfoObj = cam.cameraInfo
                                providerRef   = provider
                                appliedRegionKey = null   // re-apply AE region on (re)bind
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    },
                    update = { pv ->
                        // Apply zoom
                        activeZoom?.mm?.toFloat()?.let { mm ->
                            cameraInfoObj?.zoomState?.value?.let { state ->
                                val ratio = (mm / 23f).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                cameraCtrl?.setZoomRatio(ratio)
                            }
                        }
                        // Apply an AE region for the selected metering mode at the
                        // tapped point (or center). Best-effort: devices without
                        // AE-region support keep default full-frame metering.
                        val ctrl = cameraCtrl
                        val regionKey = "$metering:${tapPoint?.first}:${tapPoint?.second}"
                        if (ctrl != null && pv.width > 0 && appliedRegionKey != regionKey) {
                            appliedRegionKey = regionKey
                            try {
                                val regionSize = when (metering) {
                                    Constants.METERING_SPOT   -> 0.15f
                                    Constants.METERING_CENTRE -> 0.6f
                                    else                      -> null
                                }
                                if (regionSize != null) {
                                    val fx = tapPoint?.first ?: 0.5f
                                    val fy = tapPoint?.second ?: 0.5f
                                    val pt = pv.meteringPointFactory
                                        .createPoint(pv.width * fx, pv.height * fy, regionSize)
                                    ctrl.startFocusAndMetering(
                                        FocusMeteringAction.Builder(pt, FocusMeteringAction.FLAG_AE)
                                            .disableAutoCancel().build()
                                    )
                                } else {
                                    ctrl.cancelFocusAndMetering()
                                }
                            } catch (_: Exception) { /* fall back to full-frame AE */ }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Metering overlay (reticle follows the tap point)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawMeteringOverlay(metering, tapPoint)
                }
                // EV status chip
                Box(
                    Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Bg.copy(alpha = 0.80f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    val src = liveReading?.source ?: "—"
                    Text(
                        if (evLocked) "LOCKED EV ${"%.1f".format(effectiveEV)}"
                        else if (liveReading != null) "LIVE ${"%.1f".format(effectiveEV)} · $src"
                        else "Waiting for AE…",
                        color = if (evLocked) OrangeWarn else AmberBright,
                        fontSize = 10.sp
                    )
                }
                // Sensor info chip
                liveReading?.let { r ->
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Bg.copy(alpha = 0.80f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "ISO${r.sensorIso} · 1/${(1.0/r.sensorShutterSec).roundToInt()} · f${"%.1f".format(r.sensorAperture)}",
                            color = TextSecondary, fontSize = 9.sp
                        )
                    }
                }
                // Tap hint chip
                if (metering == Constants.METERING_SPOT ||
                    metering == Constants.METERING_CENTRE
                ) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Bg.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(if (tapPoint == null) "tap to meter" else "2×tap to recenter",
                            color = TextTertiary, fontSize = 9.sp)
                    }
                }
            }
        }

        // ── SCROLLABLE LOWER SECTION ──────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {

            // EV result card — big value depends on the priority mode
            if (meterMode == "aperture") {
                EVCard(
                    effectiveEV = effectiveEV,
                    resultLabel = "USE SHUTTER",
                    resultValue = shSnapped,
                    resultFootnote = "exact ${formatSeconds(secExact)}",
                    subline = "ISO $filmIso · ${formatAperture(fixedAperture)}",
                    isLive = !evLocked && liveReading != null
                )
            } else {
                EVCard(
                    effectiveEV = effectiveEV,
                    resultLabel = "USE APERTURE",
                    resultValue = formatAperture(apSnapped),
                    resultFootnote = "exact f/${"%.1f".format(apExact)}",
                    subline = "ISO $filmIso · $shutter",
                    isLive = !evLocked && liveReading != null
                )
            }

            // Zone placement indicator + low-light warning
            if (zoneActive && zone != 5) {
                Spacer(Modifier.height(6.dp))
                val off = Exposure.zoneOffsetEv(zone)
                Text(
                    "Zone ${romanZone(zone)} placement → ${if (off > 0) "−" else "+"}${abs(off)} stop${if (abs(off) != 1) "s" else ""} exposure",
                    color = OrangeWarn, fontSize = 11.sp
                )
            }
            liveReading?.let { r ->
                if (!evLocked && r.sceneEV < 1.0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚠ Below the phone sensor's reliable range — switch to Manual EV and apply reciprocity",
                        color = OrangeWarn, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Priority mode selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "shutter" to "Shutter-pri",
                    "aperture" to "Aperture-pri",
                    "table" to "EV Table"
                ).forEach { (key, label) ->
                    val sel = meterMode == key
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (sel) AmberDark else Bg3)
                            .border(1.dp, if (sel) Amber else Border, RoundedCornerShape(6.dp))
                            .clickable { vm.saveMeterMode(key) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (sel) AmberBright else TextSecondary, fontSize = 11.sp) }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Camera controls row (when camera is on)
            if (cameraOn) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton(
                        text = if (evLocked) "🔒 EV Locked" else "🔓 Lock EV",
                        modifier = Modifier.weight(1f), ghost = true, small = true,
                        onClick = { vm.setMeterLock(!evLocked, evAtLock = baseEV) }
                    )
                    VaultButton("✕ Camera", ghost = true, small = true,
                        onClick = {
                            providerRef?.unbindAll()
                            providerRef = null
                            cameraCtrl = null; cameraInfoObj = null; appliedRegionKey = null
                            cameraOn = false
                            vm.clearMeterReading(); vm.setMeterLock(false)
                        })
                }
                Spacer(Modifier.height(8.dp))

                // Cal offset — only relevant when camera is live
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Cal offset", color = TextSecondary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${Constants.formatThirds(calibDraft)} EV",
                            color = if (calibDraft == 0) TextTertiary else OrangeWarn,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        if (calibDraft != 0) {
                            TextButton(onClick = { calibDraft = 0; vm.saveMeterCalibThirds(0) },
                                contentPadding = PaddingValues(0.dp)) {
                                Text("reset", color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
                // 1/3-stop increments (classic camera EV steps), ±5 EV; persisted on release
                Slider(
                    value = calibDraft.toFloat(),
                    onValueChange = { calibDraft = it.roundToInt() },
                    onValueChangeFinished = { vm.saveMeterCalibThirds(calibDraft) },
                    valueRange = -15f..15f, steps = 29,
                    colors = SliderDefaults.colors(thumbColor = OrangeWarn, activeTrackColor = OrangeWarn, inactiveTrackColor = Border)
                )
            } else {
                // Camera off: show Live button + manual EV slider
                VaultButton(
                    text = if (hasCamPerm) "📷 Live Meter" else "📷 Enable Camera",
                    modifier = Modifier.fillMaxWidth(), ghost = true,
                    onClick = { if (hasCamPerm) { cameraOn = true; vm.setMeterLock(false) } else onRequestPerm() }
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Manual EV", color = TextSecondary, fontSize = 12.sp)
                    Text("%.1f".format(manualEV), color = Amber, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                // 1/3-stop increments to match camera EV convention
                Slider(
                    value = manualEV.toFloat(),
                    onValueChange = { vm.setMeterManualEv((it * 3).roundToInt() / 3.0) },
                    valueRange = -2f..22f, steps = 71,
                    colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Border)
                )
            }

            // ── Zone System placement (spot metering) ─────────────────────────
            if (metering == Constants.METERING_SPOT) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Zone placement", color = TextSecondary, fontSize = 12.sp)
                    Switch(
                        checked = zoneEnabled,
                        onCheckedChange = { vm.setMeterZoneEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark),
                        modifier = Modifier.height(24.dp)
                    )
                }
                if (zoneEnabled) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Constants.ZONES) { (z, _) ->
                            val sel = zone == z
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) AmberDark else Bg3)
                                    .border(1.dp, if (sel) Amber else Border, RoundedCornerShape(6.dp))
                                    .clickable { vm.setMeterZone(z) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(romanZone(z), color = if (sel) AmberBright else TextSecondary,
                                    fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(Constants.ZONES.firstOrNull { it.first == zone }?.second ?: "",
                        color = TextTertiary, fontSize = 10.sp)

                    // Shadow / highlight marks → scene contrast range
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        VaultButton("▼ Mark shadow", small = true, ghost = true,
                            onClick = { vm.markMeterShadow(baseEV) })
                        VaultButton("▲ Mark highlight", small = true, ghost = true,
                            onClick = { vm.markMeterHighlight(baseEV) })
                        if (shadowEv != null || highlightEv != null) {
                            VaultButton("✕", small = true, ghost = true,
                                onClick = { vm.clearMeterMarks() })
                        }
                    }
                    if (shadowEv != null || highlightEv != null) {
                        Spacer(Modifier.height(4.dp))
                        val s = shadowEv; val h = highlightEv
                        val text = buildString {
                            if (s != null) append("▼ ${"%.1f".format(s)}")
                            if (s != null && h != null) append("   ")
                            if (h != null) append("▲ ${"%.1f".format(h)}")
                            if (s != null && h != null) {
                                val range = h - s
                                append("   Δ ${"%.1f".format(range)} stops · ")
                                append(when {
                                    range <= 5.0 -> "flat scene — N+1 dev?"
                                    range <= 7.0 -> "normal — N dev"
                                    else         -> "contrasty — N−1 dev / compensate"
                                })
                            }
                        }
                        Text(text, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Zoom row
            if (zoomLevels.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zoomLevels) { z ->
                        val sel = activeZoom?.id == z.id
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (sel) AmberDark else Bg3)
                                .border(1.dp, if (sel) Amber else Border, RoundedCornerShape(6.dp))
                                .clickable { activeZoom = if (sel) null else z }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(z.label, color = if (sel) TextPrimary else TextSecondary, fontSize = 13.sp)
                                Text("${z.mm}mm", color = TextTertiary, fontSize = 9.sp)
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(Bg3)
                                .border(1.dp, Border, RoundedCornerShape(6.dp))
                                .clickable { showZoomEdit = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("✎ Edit", color = TextTertiary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Metering + ISO + fixed input (shutter or aperture, by mode)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Metering no longer needs the extra width now that its labels are
                // single words; the shutter column needs it, because "1/1000" is
                // the widest value any of these three ever shows.
                VaultDropdown("Metering", metering, Constants.METERING_TYPES,
                    { vm.saveMeterMetering(it) }, modifier = Modifier.weight(1.1f))
                VaultDropdown("ISO", filmIso.toString(), Constants.ISOS.map { it.toString() },
                    { vm.saveMeterIso(it.toIntOrNull() ?: 400) }, modifier = Modifier.weight(0.85f))
                if (meterMode == "aperture") {
                    VaultDropdown("Aperture", formatAperture(fixedAperture),
                        Constants.APERTURES.map { formatAperture(it) },
                        { sel -> sel.removePrefix("f/").toDoubleOrNull()?.let { vm.saveMeterAperture(it) } },
                        modifier = Modifier.weight(1.05f))
                } else {
                    VaultDropdown("Shutter", shutter, Constants.SHUTTER_SPEEDS,
                        { vm.saveMeterShutter(it) }, modifier = Modifier.weight(1.05f))
                }
            }

            // ── Reciprocity failure correction (long exposures) ───────────────
            if (displaySec >= 1.0) {
                Spacer(Modifier.height(10.dp))
                val factor = Constants.RECIPROCITY.firstOrNull { it.first == recipFilm }?.second ?: 1.30
                val corrected = Exposure.reciprocityCorrect(displaySec, factor)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg3)
                        .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp)
                ) {
                    Text("RECIPROCITY FAILURE", color = TextTertiary, fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "metered ${formatSeconds(displaySec)} → expose ≈ ${formatSeconds(corrected)}",
                        color = AmberBright, fontSize = 14.sp, fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    VaultDropdown("Film", recipFilm, Constants.RECIPROCITY.map { it.first },
                        { vm.saveRecipFilm(it) })
                }
            }

            Spacer(Modifier.height(10.dp))

            // Equivalent exposures: full table in table mode, ±2 stops otherwise
            if (meterMode == "table") {
                FullExposureTable(filmIso, shutter, effectiveEV, onSelect = { vm.saveMeterShutter(it) })
            } else if (meterMode == "shutter") {
                NearbyTable(filmIso, shutter, effectiveEV)
            }

            // Use in Shot button
            if (onUseInShot != null) {
                Spacer(Modifier.height(12.dp))
                // Snapped to standard scales so the values match the ShotSheet
                // options exactly; toString keeps '.' decimals on comma-locales.
                val useShutter: String
                val useApertureNum: String
                if (meterMode == "aperture") {
                    useShutter = shSnapped
                    useApertureNum = apertureNumString(fixedAperture)
                } else {
                    useShutter = shutter
                    useApertureNum = apertureNumString(apSnapped)
                }
                VaultButton(
                    text = "📋 Use in Shot  $useShutter · f/$useApertureNum · ISO $filmIso",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onUseInShot(useShutter, useApertureNum, filmIso.toString()) }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showZoomEdit) ZoomEditSheet(zoomLevels, vm) { showZoomEdit = false }
}

/**
 * "f/8" for whole stops, "f/5.6" otherwise — matches the APERTURES scale and
 * stays '.'-decimal on all locales.
 *
 * Below f/2 the decimal is always printed, so the widest stop reads "f/1.0"
 * rather than "f/1". Photographic convention writes fast glass with a decimal
 * (f/1.0, f/1.2, f/1.4) and a bare "f/1" reads like a truncated "f/1.4" — which
 * is a whole stop of exposure in the wrong direction.
 */
private fun formatAperture(a: Double): String {
    val whole = a == a.toLong().toDouble()
    // Only whole values need forcing; f/0.95 and f/1.4 already carry a decimal,
    // and rounding them to one place would collapse 0.95 onto 1.0 and give the
    // aperture dropdown two entries with the same label.
    return when {
        a < 2.0 && whole -> "f/${"%.1f".format(java.util.Locale.US, a)}"
        whole            -> "f/${a.toLong()}"
        else             -> "f/$a"
    }
}

/** Bare aperture number as stored on shots ("8", "5.6"). */
private fun apertureNumString(a: Double): String =
    if (a == a.toLong().toDouble()) a.toLong().toString() else a.toString()

/** "1/125" below one second, "2.5s"/"45s" above. Double.toString keeps '.' on all locales. */
private fun formatSeconds(sec: Double): String = when {
    sec < 1.0   -> "1/${(1.0 / sec).roundToInt()}"
    sec < 10.0  -> "${((sec * 10).roundToInt() / 10.0).toString().trimEnd('0').trimEnd('.')}s"
    else        -> "${sec.roundToInt()}s"
}

private fun romanZone(zone: Int): String =
    listOf("0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(zone) { "$zone" }

// ─── Canvas metering overlay ──────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeteringOverlay(
    metering: String,
    tapPoint: Pair<Float, Float>? = null
) {
    val w = size.width; val h = size.height
    val cx = w * (tapPoint?.first ?: 0.5f)
    val cy = h * (tapPoint?.second ?: 0.5f)
    val stroke = Stroke(2.dp.toPx())
    val color = Color(0xCCD4935A.toInt())

    when (metering) {
        Constants.METERING_SPOT -> {
            val r = minOf(w, h) / 6f
            drawCircle(color, r, Offset(cx, cy), style = stroke)
            val gap = r + 8.dp.toPx(); val len = 16.dp.toPx()
            drawLine(color, Offset(cx - gap - len, cy), Offset(cx - gap, cy), stroke.width)
            drawLine(color, Offset(cx + gap, cy),       Offset(cx + gap + len, cy), stroke.width)
            drawLine(color, Offset(cx, cy - gap - len), Offset(cx, cy - gap), stroke.width)
            drawLine(color, Offset(cx, cy + gap),       Offset(cx, cy + gap + len), stroke.width)
        }
        Constants.METERING_CENTRE -> {
            drawCircle(color, minOf(w, h) / 3f, Offset(cx, cy), style = stroke)
            val m = 20.dp.toPx(); val p = 10.dp.toPx()
            listOf(
                Offset(p, p) to Offset(p + m, p), Offset(p, p) to Offset(p, p + m),
                Offset(w-p, p) to Offset(w-p-m, p), Offset(w-p, p) to Offset(w-p, p+m),
                Offset(p, h-p) to Offset(p+m, h-p), Offset(p, h-p) to Offset(p, h-p-m),
                Offset(w-p, h-p) to Offset(w-p-m, h-p), Offset(w-p, h-p) to Offset(w-p, h-p-m),
            ).forEach { (a, b) -> drawLine(color, a, b, stroke.width) }
        }
        Constants.METERING_HIGHLIGHT -> {
            drawRect(color, Offset(0f, 0f), Size(w, h / 4f), style = stroke)
        }
        else -> { // Matrix — 3×3 grid
            for (c in 1..2) drawLine(color, Offset(w * c / 3f, 0f), Offset(w * c / 3f, h), stroke.width / 2)
            for (r in 1..2) drawLine(color, Offset(0f, h * r / 3f), Offset(w, h * r / 3f), stroke.width / 2)
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun EVCard(
    effectiveEV: Double,
    resultLabel: String, resultValue: String, resultFootnote: String,
    subline: String, isLive: Boolean
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SCENE EV", color = TextTertiary, fontSize = 9.sp, maxLines = 1)
                Text("%.1f".format(effectiveEV), color = Amber, fontSize = 44.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                Box(Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isLive) GreenOk.copy(0.15f) else Bg4)
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(if (isLive) "LIVE · camera2" else "MANUAL",
                        color = if (isLive) GreenOk else TextTertiary, fontSize = 9.sp, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(resultLabel, color = TextTertiary, fontSize = 9.sp, maxLines = 1)
                // The readout must never reflow. Six mono glyphs at 36sp ("1/1000")
                // do not fit half the card on a 360dp screen, and the default
                // wrapping turned "f/1.4" into "f/1" over "4" — which reads as a
                // plausible, wrong aperture. Step the size down instead of wrapping.
                Text(
                    resultValue,
                    color = AmberBright,
                    fontSize = if (resultValue.length > 5) 28.sp else 36.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
                Text(resultFootnote, color = TextTertiary, fontSize = 9.sp, maxLines = 1)
                Text(subline, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NearbyTable(filmIso: Int, shutter: String, effectiveEV: Double) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Text("EQUIVALENT EXPOSURES", color = TextTertiary, fontSize = 9.sp)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(4.dp))
        listOf(-2, -1, 0, 1, 2).forEach { offset ->
            val idx = (Constants.SHUTTER_SPEEDS.indexOf(shutter) + offset)
                .coerceIn(0, Constants.SHUTTER_SPEEDS.lastIndex)
            val sh  = Constants.SHUTTER_SPEEDS[idx]
            val ap  = formatAperture(Constants.nearestStandardAperture(Constants.calcAperture(filmIso, sh, effectiveEV)))
            val hl  = offset == 0
            Row(
                Modifier.fillMaxWidth()
                    .background(if (hl) AmberDark.copy(0.15f) else Color.Transparent)
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(sh, color = if (hl) AmberBright else TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(ap, color = if (hl) AmberBright else TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Full shutter → aperture table for the current EV; tapping a row selects that shutter. */
@Composable
private fun FullExposureTable(
    filmIso: Int, shutter: String, effectiveEV: Double,
    onSelect: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Text("EV ${"%.1f".format(effectiveEV)} · ALL EQUIVALENT EXPOSURES", color = TextTertiary, fontSize = 9.sp)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(4.dp))
        Constants.SHUTTER_SPEEDS.filter { it != "B" }.forEach { sh ->
            val ap = Constants.nearestStandardAperture(Constants.calcAperture(filmIso, sh, effectiveEV))
            // Skip rows outside any real lens range to keep the table useful
            if (ap >= 0.95 && ap <= 32.0) {
                val hl = sh == shutter
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hl) AmberDark.copy(0.15f) else Color.Transparent)
                        .clickable { onSelect(sh) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(sh, color = if (hl) AmberBright else TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(formatAperture(ap), color = if (hl) AmberBright else TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ─── Zoom Edit Sheet ──────────────────────────────────────────────────────────

@Composable
fun ZoomEditSheet(zoomLevels: List<ZoomLevel>, vm: MainViewModel, onDismiss: () -> Unit) {
    var newLabel  by remember { mutableStateOf("") }
    var newMm     by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editLabel by remember { mutableStateOf("") }
    var editMm    by remember { mutableStateOf("") }

    VaultSheet("Edit Zoom Levels", onDismiss) {
        zoomLevels.forEach { z ->
            if (editingId == z.id) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaultTextField(editLabel, { editLabel = it }, "Label", modifier = Modifier.weight(1f))
                        VaultTextField(editMm, { editMm = it }, "mm", modifier = Modifier.weight(0.7f),
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaultButton("Save", small = true, modifier = Modifier.weight(1f), onClick = {
                            if (editLabel.isNotBlank()) {
                                vm.upsertZoomLevel(z.copy(label = editLabel.trim(), mm = editMm.toIntOrNull() ?: z.mm))
                                editingId = null
                            }
                        })
                        VaultButton("Cancel", small = true, ghost = true, modifier = Modifier.weight(1f),
                            onClick = { editingId = null })
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${z.label}  ${z.mm}mm", color = TextPrimary, fontSize = 13.sp)
                    Row {
                        IconButton(onClick = { editingId = z.id; editLabel = z.label; editMm = z.mm.toString() },
                            Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = Amber)
                        }
                        IconButton(onClick = { vm.deleteZoomLevel(z) }, Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = RedErr.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(10.dp))
        Text("Add zoom level", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultTextField(newLabel, { newLabel = it }, "Label (e.g. 2x)", modifier = Modifier.weight(1f))
            VaultTextField(newMm, { newMm = it }, "mm", modifier = Modifier.weight(0.7f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        }
        Spacer(Modifier.height(8.dp))
        VaultButton("Add", modifier = Modifier.fillMaxWidth(), onClick = {
            if (newLabel.isNotBlank() && newMm.isNotBlank()) {
                vm.upsertZoomLevel(ZoomLevel(uid(), newLabel.trim(), newMm.toIntOrNull() ?: 0))
                newLabel = ""; newMm = ""
            }
        })
    }
}

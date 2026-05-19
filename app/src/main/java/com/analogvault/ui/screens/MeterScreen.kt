package com.analogvault.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.analogvault.data.model.ZoomLevel
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    var hasCamPerm by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamPerm = it }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.CAMERA) }
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

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun MeterContent(
    vm: MainViewModel,
    zoomLevels: List<ZoomLevel>,
    hasCamPerm: Boolean,
    onRequestPerm: () -> Unit,
    onUseInShot: ((shutter: String, aperture: String, iso: String) -> Unit)? = null
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── User inputs ───────────────────────────────────────────────────────────
    var filmIso      by remember { mutableIntStateOf(400) }
    var shutter      by remember { mutableStateOf("1/125") }
    var metering     by remember { mutableStateOf(Constants.METERING_TYPES[0]) }
    var calibOffset  by remember { mutableStateOf(0.0) }   // user EV correction, persists

    // ── Camera ────────────────────────────────────────────────────────────────
    var cameraOn     by remember { mutableStateOf(false) }
    var cameraCtrl   by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfoObj by remember { mutableStateOf<CameraInfo?>(null) }

    // ── Live metadata reading ─────────────────────────────────────────────────
    var liveReading  by remember { mutableStateOf<MeterReading?>(null) }
    val liveReadingRef = rememberUpdatedState(liveReading)

    // ── Manual EV (slider) ────────────────────────────────────────────────────
    var manualEV     by remember { mutableStateOf(12.0) }
    var evLocked     by remember { mutableStateOf(false) }

    // Effective EV for exposure calculation
    val effectiveEV = when {
        evLocked         -> manualEV
        liveReading != null -> (liveReading!!.sceneEV + calibOffset).coerceIn(-2.0, 22.0)
        else             -> manualEV
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────
    var activeZoom   by remember { mutableStateOf<ZoomLevel?>(null) }
    var showZoomEdit by remember { mutableStateOf(false) }

    // ── EXIF ──────────────────────────────────────────────────────────────────
    var exifResult   by remember { mutableStateOf<ExifReading?>(null) }
    val exifPicker   = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { exifResult = readExif(context, it) }
    }

    val solvedAperture = remember(filmIso, shutter, effectiveEV) {
        "f/${"%.1f".format(Constants.calcAperture(filmIso, shutter, effectiveEV))}"
    }

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
                                override fun onCaptureCompleted(
                                    session: android.hardware.camera2.CameraCaptureSession,
                                    request: android.hardware.camera2.CaptureRequest,
                                    result: android.hardware.camera2.TotalCaptureResult
                                ) {
                                    if (evLocked) return
                                    val isoVal    = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
                                    val shutterNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
                                    val apertureF = result.get(CaptureResult.LENS_APERTURE) ?: return

                                    val shutterSec = shutterNs / 1_000_000_000.0
                                    // EV = log2(N² / t) − log2(ISO/100)
                                    // where N=aperture, t=shutter in seconds
                                    // This gives scene EV at the measured exposure
                                    val ev = log2((apertureF * apertureF) / shutterSec) - log2(isoVal / 100.0)

                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        liveReading = MeterReading(
                                            sceneEV       = ev.coerceIn(-6.0, 24.0),
                                            sensorIso     = isoVal,
                                            sensorShutterSec = shutterSec,
                                            sensorAperture = apertureF.toDouble()
                                        )
                                    }
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
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    },
                    update = {
                        // Apply zoom
                        activeZoom?.mm?.toFloat()?.let { mm ->
                            cameraInfoObj?.zoomState?.value?.let { state ->
                                val ratio = (mm / 23f).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                cameraCtrl?.setZoomRatio(ratio)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Metering overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawMeteringOverlay(metering)
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

            // EV result card
            EVCard(
                effectiveEV    = effectiveEV,
                solvedAperture = solvedAperture,
                filmIso        = filmIso,
                shutter        = shutter,
                isLive         = !evLocked && liveReading != null
            )

            Spacer(Modifier.height(10.dp))

            // Camera controls row (when camera is on)
            if (cameraOn) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton(
                        text = if (evLocked) "🔒 EV Locked" else "🔓 Lock EV",
                        modifier = Modifier.weight(1f), ghost = true, small = true,
                        onClick = {
                            evLocked = !evLocked
                            if (evLocked) manualEV = effectiveEV
                        }
                    )
                    VaultButton("✕ Camera", ghost = true, small = true,
                        onClick = { cameraOn = false; liveReading = null; evLocked = false })
                }
                Spacer(Modifier.height(8.dp))

                // Cal offset — only relevant when camera is live
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Cal offset", color = TextSecondary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${if (calibOffset >= 0) "+" else ""}${"%.1f".format(calibOffset)}",
                            color = if (calibOffset == 0.0) TextTertiary else OrangeWarn,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        if (calibOffset != 0.0) {
                            TextButton(onClick = { calibOffset = 0.0 },
                                contentPadding = PaddingValues(0.dp)) {
                                Text("reset", color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Slider(
                    value = calibOffset.toFloat(),
                    onValueChange = { calibOffset = it.toDouble() },
                    valueRange = -5f..5f, steps = 49,
                    colors = SliderDefaults.colors(thumbColor = OrangeWarn, activeTrackColor = OrangeWarn, inactiveTrackColor = Border)
                )
            } else {
                // Camera off: show Live button + manual EV slider
                VaultButton(
                    text = if (hasCamPerm) "📷 Live Meter" else "📷 Enable Camera",
                    modifier = Modifier.fillMaxWidth(), ghost = true,
                    onClick = { if (hasCamPerm) { cameraOn = true; evLocked = false } else onRequestPerm() }
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Manual EV", color = TextSecondary, fontSize = 12.sp)
                    Text("%.1f".format(manualEV), color = Amber, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = manualEV.toFloat(),
                    onValueChange = { manualEV = it.toDouble() },
                    valueRange = -2f..22f, steps = 95,
                    colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Border)
                )
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

            // Metering + ISO + Shutter in one compact row — below zoom
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VaultDropdown("Metering", metering, Constants.METERING_TYPES,
                    { metering = it }, modifier = Modifier.weight(1.4f))
                VaultDropdown("ISO", filmIso.toString(), Constants.ISOS.map { it.toString() },
                    { filmIso = it.toIntOrNull() ?: 400 }, modifier = Modifier.weight(1f))
                VaultDropdown("Shutter", shutter, Constants.SHUTTER_SPEEDS,
                    { shutter = it }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            // Nearby combinations table
            NearbyTable(filmIso, shutter, effectiveEV)

            // Use in Shot button
            if (onUseInShot != null) {
                Spacer(Modifier.height(12.dp))
                val apNum = "%.1f".format(Constants.calcAperture(filmIso, shutter, effectiveEV))
                VaultButton(
                    text = "📋 Use in Shot  $shutter · f/$apNum · ISO $filmIso",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onUseInShot(shutter, apNum, filmIso.toString()) }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showZoomEdit) ZoomEditSheet(zoomLevels, vm) { showZoomEdit = false }
}

// ─── Canvas metering overlay ──────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeteringOverlay(metering: String) {
    val w = size.width; val h = size.height
    val cx = w / 2f; val cy = h / 2f
    val stroke = Stroke(2.dp.toPx())
    val color = Color(0xCCD4935A.toInt())

    when (metering) {
        "Spot" -> {
            val r = minOf(w, h) / 6f
            drawCircle(color, r, Offset(cx, cy), style = stroke)
            val gap = r + 8.dp.toPx(); val len = 16.dp.toPx()
            drawLine(color, Offset(cx - gap - len, cy), Offset(cx - gap, cy), stroke.width)
            drawLine(color, Offset(cx + gap, cy),       Offset(cx + gap + len, cy), stroke.width)
            drawLine(color, Offset(cx, cy - gap - len), Offset(cx, cy - gap), stroke.width)
            drawLine(color, Offset(cx, cy + gap),       Offset(cx, cy + gap + len), stroke.width)
        }
        "Center-Weighted" -> {
            drawCircle(color, minOf(w, h) / 3f, Offset(cx, cy), style = stroke)
            val m = 20.dp.toPx(); val p = 10.dp.toPx()
            listOf(
                Offset(p, p) to Offset(p + m, p), Offset(p, p) to Offset(p, p + m),
                Offset(w-p, p) to Offset(w-p-m, p), Offset(w-p, p) to Offset(w-p, p+m),
                Offset(p, h-p) to Offset(p+m, h-p), Offset(p, h-p) to Offset(p, h-p-m),
                Offset(w-p, h-p) to Offset(w-p-m, h-p), Offset(w-p, h-p) to Offset(w-p, h-p-m),
            ).forEach { (a, b) -> drawLine(color, a, b, stroke.width) }
        }
        "Highlight-Weighted" -> {
            drawRect(color, Offset(0f, 0f), Size(w, h / 4f), style = stroke)
        }
        else -> { // Evaluative — 3×3 grid
            for (c in 1..2) drawLine(color, Offset(w * c / 3f, 0f), Offset(w * c / 3f, h), stroke.width / 2)
            for (r in 1..2) drawLine(color, Offset(0f, h * r / 3f), Offset(w, h * r / 3f), stroke.width / 2)
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun EVCard(
    effectiveEV: Double, solvedAperture: String,
    filmIso: Int, shutter: String, isLive: Boolean
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("SCENE EV", color = TextTertiary, fontSize = 9.sp)
                Text("%.1f".format(effectiveEV), color = Amber, fontSize = 44.sp, fontFamily = FontFamily.Monospace)
                Box(Modifier.clip(RoundedCornerShape(3.dp))
                    .background(if (isLive) GreenOk.copy(0.15f) else Bg4)
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(if (isLive) "LIVE · camera2" else "MANUAL",
                        color = if (isLive) GreenOk else TextTertiary, fontSize = 9.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("USE APERTURE", color = TextTertiary, fontSize = 9.sp)
                Text(solvedAperture, color = AmberBright, fontSize = 36.sp, fontFamily = FontFamily.Monospace)
                Text("ISO $filmIso · $shutter", color = TextSecondary, fontSize = 10.sp)
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
            val ap  = "f/${"%.1f".format(Constants.calcAperture(filmIso, sh, effectiveEV))}"
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

@Composable
private fun ExifPanel(
    exif: ExifReading, onDismiss: () -> Unit,
    onUseEV: (Double) -> Unit, onApplyInputs: () -> Unit
) {
    val exifEV = if (exif.iso != null && exif.shutter != null && exif.aperture != null) {
        val ap = exif.aperture.toDoubleOrNull() ?: 0.0
        val t  = Constants.evalShutter(exif.shutter)
        if (ap > 0 && t > 0) (log2(ap * ap / t) - log2(exif.iso / 100.0)) else null
    } else null

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(BlueInfo.copy(0.08f)).border(1.dp, BlueInfo.copy(0.35f), RoundedCornerShape(10.dp))
        .padding(12.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("EXIF", color = BlueInfo, fontSize = 10.sp)
                IconButton(onClick = onDismiss, Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null,
                        tint = TextTertiary, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exif.iso?.let     { VaultTag("ISO $it", textColor = AmberBright) }
                exif.shutter?.let { VaultTag(it, textColor = AmberBright) }
                exif.aperture?.let { VaultTag("f/$it", textColor = AmberBright) }
            }
            exif.lens?.let { Text("Lens: $it", color = TextTertiary, fontSize = 10.sp) }
            exifEV?.let { ev ->
                Spacer(Modifier.height(6.dp))
                Text("EV ${"%.1f".format(ev)}", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton("Use EV", small = true, onClick = { onUseEV(ev) })
                    VaultButton("Apply ISO+Shutter", small = true, ghost = true, onClick = onApplyInputs)
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

// ─── EXIF reading ─────────────────────────────────────────────────────────────

data class ExifReading(val iso: Int?, val shutter: String?, val aperture: String?, val lens: String?)

private fun readExif(context: Context, uri: Uri): ExifReading = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 }
        val shutterStr = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let { t ->
            if (t >= 1.0) "${"%.0f".format(t)}s" else if (t > 0) "1/${(1.0 / t).roundToInt()}" else null
        }
        val apRaw = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
            ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
        val apStr = apRaw?.let { raw ->
            val parts = raw.split("/")
            if (parts.size == 2) {
                val n = parts[0].toDoubleOrNull() ?: return@let raw
                val d = parts[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return@let raw
                "%.1f".format(n / d)
            } else raw
        }
        ExifReading(iso, shutterStr, apStr, exif.getAttribute(ExifInterface.TAG_LENS_MODEL))
    } ?: ExifReading(null, null, null, null)
} catch (e: Exception) { ExifReading(null, null, null, null) }

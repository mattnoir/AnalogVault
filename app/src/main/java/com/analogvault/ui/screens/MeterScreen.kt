package com.analogvault.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
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
import java.util.concurrent.Executors
import kotlin.math.*
import kotlin.math.roundToInt

// ─── Entry ────────────────────────────────────────────────────────────────────

@Composable
fun MeterScreen(vm: MainViewModel, onUseInShot: ((shutter: String, aperture: String, iso: String) -> Unit)? = null) {
    val zoomLevels by vm.zoomLevels.collectAsState()
    val displayZooms = remember(zoomLevels) {
        zoomLevels.distinctBy { it.label to it.mm }.sortedBy { it.mm }
    }
    var hasCamPerm by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamPerm = it }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.CAMERA) }
    MeterContent(vm, displayZooms, hasCamPerm, onRequestPerm = { permLauncher.launch(Manifest.permission.CAMERA) }, onUseInShot = onUseInShot)
}

// ─── Main ─────────────────────────────────────────────────────────────────────

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

    // ── Exposure inputs ───────────────────────────────────────────────────────
    var iso      by remember { mutableIntStateOf(400) }
    var shutter  by remember { mutableStateOf("1/125") }
    var metering by remember { mutableStateOf(Constants.METERING_TYPES[0]) }

    // ── Camera state ──────────────────────────────────────────────────────────
    var cameraOn      by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo    by remember { mutableStateOf<CameraInfo?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // ── EV state ──────────────────────────────────────────────────────────────
    // liveEV: raw from analyser (never written to by user)
    // manualEV: user-controlled slider — never overwritten by analyser
    // evLocked: true = use manualEV even when camera on
    // calibOffset: user-adjustable stops to add to live reading (per-environment calibration)
    var liveEV      by remember { mutableStateOf<Double?>(null) }
    var manualEV    by remember { mutableStateOf(12.0) }
    var evLocked    by remember { mutableStateOf(false) }
    var calibOffset by remember { mutableStateOf(0.0) }  // stops, -5..+5

    val effectiveEV = when {
        evLocked          -> manualEV
        liveEV != null    -> (liveEV!! + calibOffset).coerceIn(-2.0, 22.0)
        else              -> manualEV
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────
    var activeZoom   by remember { mutableStateOf<ZoomLevel?>(null) }
    var showZoomEdit by remember { mutableStateOf(false) }

    // ── EXIF ──────────────────────────────────────────────────────────────────
    var exifResult by remember { mutableStateOf<ExifReading?>(null) }
    val exifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { exifResult = readExif(context, it) }
    }

    val solvedAperture = remember(iso, shutter, effectiveEV) {
        "f/${"%.1f".format(Constants.calcAperture(iso, shutter, effectiveEV))}"
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // 1. Camera viewfinder (collapsible, top so it's prominent when on)
        if (cameraOn && hasCamPerm) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Bg2)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val pv = PreviewView(ctx)
                        ProcessCameraProvider.getInstance(ctx).addListener({
                            val provider = ProcessCameraProvider.getInstance(ctx).get()
                            val preview = Preview.Builder().build()
                                .also { it.setSurfaceProvider(pv.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                            analysis.setAnalyzer(analysisExecutor) { proxy ->
                                if (!evLocked) liveEV = analyzeEVCalibrated(proxy, metering)
                                proxy.close()
                            }
                            try {
                                provider.unbindAll()
                                val cam = provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                                )
                                cameraControl = cam.cameraControl
                                cameraInfo    = cam.cameraInfo
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    },
                    update = {
                        activeZoom?.mm?.toFloat()?.let { mm ->
                            cameraInfo?.zoomState?.value?.let { state ->
                                val ratio = (mm / 23f).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                cameraControl?.setZoomRatio(ratio)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Metering mode visual overlay
                MeteringOverlay(metering = metering, modifier = Modifier.fillMaxSize())

                // EV overlay
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Bg.copy(alpha = 0.80f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (evLocked) "MANUAL EV ${"%.1f".format(effectiveEV)}"
                               else "LIVE EV ${"%.1f".format(effectiveEV)}  +${"%.1f".format(calibOffset)}stop cal",
                        color = if (evLocked) OrangeWarn else AmberBright,
                        fontSize = 11.sp
                    )
                }
                // Close camera button
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Bg.copy(alpha = 0.80f))
                ) {
                    IconButton(onClick = { cameraOn = false; liveEV = null }) {
                        Icon(imageVector = Icons.Default.VideocamOff, contentDescription = "Close camera", tint = TextSecondary)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {

            // 2. ── EXIF panel (highest priority — bypasses everything) ────────
            exifResult?.let { exif ->
                ExifPanel(
                    exif = exif,
                    onDismiss = { exifResult = null },
                    onUseEV = { ev -> manualEV = ev; evLocked = true },
                    onApplyInputs = {
                        exif.iso?.let { iso = it }
                        exif.shutter?.let { shutter = it }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            // 3. ── EV display + manual slider ─────────────────────────────────
            EVCard(
                effectiveEV   = effectiveEV,
                solvedAperture = solvedAperture,
                iso            = iso,
                shutter        = shutter,
                isLive         = !evLocked && liveEV != null
            )

            Spacer(Modifier.height(10.dp))

            // Manual EV slider — always visible and editable
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Manual EV", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%.1f".format(manualEV), color = Amber, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace)
                    if (cameraOn && !evLocked) {
                        Text("(live)", color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }
            Slider(
                value = manualEV.toFloat(),
                onValueChange = { manualEV = it.toDouble(); evLocked = true },
                valueRange = -2f..22f,
                steps = 95,
                colors = SliderDefaults.colors(
                    thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Border
                )
            )

            // 4. ── Camera calibration offset (shown when camera on, unlocked) ─
            if (cameraOn && !evLocked) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Calibration offset", color = TextSecondary, fontSize = 12.sp)
                        Text("Adjust if reading feels off for your environment",
                            color = TextTertiary, fontSize = 10.sp)
                    }
                    Text(
                        "${if (calibOffset >= 0) "+" else ""}${"%.1f".format(calibOffset)} EV",
                        color = if (calibOffset == 0.0) TextTertiary else OrangeWarn,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = calibOffset.toFloat(),
                    onValueChange = { calibOffset = it.toDouble() },
                    valueRange = -5f..5f,
                    steps = 49,
                    colors = SliderDefaults.colors(
                        thumbColor = OrangeWarn, activeTrackColor = OrangeWarn, inactiveTrackColor = Border
                    )
                )
                if (calibOffset != 0.0) {
                    TextButton(
                        onClick = { calibOffset = 0.0 },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Reset offset", color = TextTertiary, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            // 5. ── Camera toggle + Lock EV + EXIF button ─────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultButton(
                    text = when {
                        !hasCamPerm          -> "📷 Enable Camera"
                        cameraOn && evLocked -> "🔒 EV Locked"
                        cameraOn             -> "🔓 Lock EV"
                        else                 -> "📷 Live Meter"
                    },
                    modifier = Modifier.weight(1f),
                    ghost = true,
                    onClick = {
                        when {
                            !hasCamPerm -> onRequestPerm()
                            !cameraOn   -> { cameraOn = true; evLocked = false }
                            else        -> {
                                evLocked = !evLocked
                                if (evLocked) manualEV = effectiveEV
                            }
                        }
                    }
                )
                VaultButton(
                    text = "📁 EXIF",
                    ghost = true,
                    small = true,
                    onClick = { exifPicker.launch("image/*") }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 6. ── Zoom row ───────────────────────────────────────────────────
            if (zoomLevels.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zoomLevels) { z ->
                        val sel = activeZoom?.id == z.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
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
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp)).background(Bg3)
                                .border(1.dp, Border, RoundedCornerShape(6.dp))
                                .clickable { showZoomEdit = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("✎ Edit", color = TextTertiary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 7. ── Metering + ISO + Shutter (always editable) ─────────────────
            // These are always in a scrollable column below the viewfinder so they
            // are reachable even when camera is on
            VaultDropdown("Metering Mode", metering, Constants.METERING_TYPES, { metering = it })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VaultDropdown(
                    "ISO", iso.toString(), Constants.ISOS.map { it.toString() },
                    { iso = it.toIntOrNull() ?: 400 },
                    modifier = Modifier.weight(1f)
                )
                VaultDropdown(
                    "Shutter", shutter, Constants.SHUTTER_SPEEDS,
                    { shutter = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // 8. ── Nearby combinations ────────────────────────────────────────
            NearbyTable(iso = iso, shutter = shutter, effectiveEV = effectiveEV)

            Spacer(Modifier.height(16.dp))

            // Use in Shot button
            if (onUseInShot != null) {
                val apNum = "%.1f".format(Constants.calcAperture(iso, shutter, effectiveEV))
                VaultButton(
                    text = "📋 Use in Shot Log  ($shutter · f/$apNum · ISO $iso)",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onUseInShot(shutter, apNum, iso.toString()) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showZoomEdit) ZoomEditSheet(zoomLevels, vm) { showZoomEdit = false }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun EVCard(
    effectiveEV: Double,
    solvedAperture: String,
    iso: Int,
    shutter: String,
    isLive: Boolean
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("SCENE EV", color = TextTertiary, fontSize = 9.sp)
                Text("%.1f".format(effectiveEV), color = Amber, fontSize = 44.sp,
                    fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(3.dp))
                            .background(if (isLive) GreenOk.copy(alpha = 0.15f) else Bg4)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(if (isLive) "LIVE" else "MANUAL",
                            color = if (isLive) GreenOk else TextTertiary, fontSize = 9.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("USE APERTURE", color = TextTertiary, fontSize = 9.sp)
                Text(solvedAperture, color = AmberBright, fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace)
                Text("ISO $iso · $shutter", color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NearbyTable(iso: Int, shutter: String, effectiveEV: Double) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("NEARBY COMBINATIONS", color = TextTertiary, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        // Header
        Row(Modifier.fillMaxWidth()) {
            Text("SHUTTER", color = TextTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text("APERTURE", color = TextTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(4.dp))
        listOf(-2, -1, 0, 1, 2).forEach { offset ->
            val idx = (Constants.SHUTTER_SPEEDS.indexOf(shutter) + offset)
                .coerceIn(0, Constants.SHUTTER_SPEEDS.lastIndex)
            val sh  = Constants.SHUTTER_SPEEDS[idx]
            val ap  = "f/${"%.1f".format(Constants.calcAperture(iso, sh, effectiveEV))}"
            val hl  = offset == 0
            Row(
                Modifier.fillMaxWidth()
                    .background(if (hl) AmberDark.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(sh, color = if (hl) AmberBright else TextSecondary, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
                Text(ap, color = if (hl) AmberBright else TextSecondary, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ExifPanel(
    exif: ExifReading,
    onDismiss: () -> Unit,
    onUseEV: (Double) -> Unit,
    onApplyInputs: () -> Unit
) {
    val exifEV = if (exif.iso != null && exif.shutter != null && exif.aperture != null) {
        val ap = exif.aperture.toDoubleOrNull() ?: 0.0
        val t  = Constants.evalShutter(exif.shutter)
        if (ap > 0 && t > 0) (log2(ap * ap / t) - log2(exif.iso / 100.0)) else null
    } else null

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BlueInfo.copy(alpha = 0.08f))
            .border(1.dp, BlueInfo.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("EXIF DATA", color = BlueInfo, fontSize = 10.sp)
                IconButton(onClick = onDismiss, Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss",
                        tint = TextTertiary, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exif.iso?.let     { VaultTag("ISO $it",  textColor = AmberBright) }
                exif.shutter?.let { VaultTag(it,          textColor = AmberBright) }
                exif.aperture?.let { VaultTag("f/$it",   textColor = AmberBright) }
            }
            exif.lens?.let {
                Spacer(Modifier.height(4.dp))
                Text("Lens: $it", color = TextTertiary, fontSize = 10.sp)
            }
            exifEV?.let { ev ->
                Spacer(Modifier.height(8.dp))
                Text("Calculated EV: ${"%.1f".format(ev)}", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton("Use EV ${"%.1f".format(ev)}", small = true,
                        onClick = { onUseEV(ev) })
                    VaultButton("Apply ISO + Shutter", small = true, ghost = true,
                        onClick = onApplyInputs)
                }
            }
        }
    }
}


// ─── Metering overlay ─────────────────────────────────────────────────────────

@Composable
private fun MeteringOverlay(metering: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f
        val strokePx = 2.dp.toPx()
        val color = androidx.compose.ui.graphics.Color(0xCCD4935A.toInt())  // amber 80%
        when (metering) {
            "Spot" -> {
                val r = minOf(w, h) / 6f
                drawCircle(color = color, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx))
                // Crosshair lines
                val gap = r + 8.dp.toPx(); val len = 20.dp.toPx()
                drawLine(color, androidx.compose.ui.geometry.Offset(cx - gap - len, cy), androidx.compose.ui.geometry.Offset(cx - gap, cy), strokePx)
                drawLine(color, androidx.compose.ui.geometry.Offset(cx + gap, cy), androidx.compose.ui.geometry.Offset(cx + gap + len, cy), strokePx)
                drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy - gap - len), androidx.compose.ui.geometry.Offset(cx, cy - gap), strokePx)
                drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy + gap), androidx.compose.ui.geometry.Offset(cx, cy + gap + len), strokePx)
            }
            "Center-Weighted" -> {
                val r = minOf(w, h) / 3f
                drawCircle(color = color, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx))
                // Corner marks
                val m = 24.dp.toPx(); val p = 12.dp.toPx()
                listOf(
                    androidx.compose.ui.geometry.Offset(p, p) to androidx.compose.ui.geometry.Offset(p + m, p),
                    androidx.compose.ui.geometry.Offset(p, p) to androidx.compose.ui.geometry.Offset(p, p + m),
                    androidx.compose.ui.geometry.Offset(w - p, p) to androidx.compose.ui.geometry.Offset(w - p - m, p),
                    androidx.compose.ui.geometry.Offset(w - p, p) to androidx.compose.ui.geometry.Offset(w - p, p + m),
                    androidx.compose.ui.geometry.Offset(p, h - p) to androidx.compose.ui.geometry.Offset(p + m, h - p),
                    androidx.compose.ui.geometry.Offset(p, h - p) to androidx.compose.ui.geometry.Offset(p, h - p - m),
                    androidx.compose.ui.geometry.Offset(w - p, h - p) to androidx.compose.ui.geometry.Offset(w - p - m, h - p),
                    androidx.compose.ui.geometry.Offset(w - p, h - p) to androidx.compose.ui.geometry.Offset(w - p, h - p - m),
                ).forEach { (a, b) -> drawLine(color, a, b, strokePx) }
            }
            "Highlight-Weighted" -> {
                // Top third highlight band
                val bandH = h / 4f
                drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, bandH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokePx))
                // Small label
            }
            else -> {
                // Evaluative: 3x3 grid
                for (col in 1..2) drawLine(color, androidx.compose.ui.geometry.Offset(w * col / 3f, 0f), androidx.compose.ui.geometry.Offset(w * col / 3f, h), strokePx / 2)
                for (row in 1..2) drawLine(color, androidx.compose.ui.geometry.Offset(0f, h * row / 3f), androidx.compose.ui.geometry.Offset(w, h * row / 3f), strokePx / 2)
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
                            Icon(Icons.Default.Edit, null, Modifier.size(14.dp), tint = Amber)
                        }
                        IconButton(onClick = { vm.deleteZoomLevel(z) }, Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = RedErr.copy(alpha = 0.7f))
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

// ─── EXIF ─────────────────────────────────────────────────────────────────────

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

// ─── EV analysis (calibrated) ────────────────────────────────────────────────

private fun analyzeEVCalibrated(imageProxy: ImageProxy, meteringMode: String): Double? = try {
    val plane = imageProxy.planes[0]
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    val w = imageProxy.width; val h = imageProxy.height
    val rs = plane.rowStride; val ps = plane.pixelStride
    val step = 4
    fun lin(c: Int): Double { val s = c / 255.0; return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    fun lum(i: Int): Double {
        if (i + 2 >= bytes.size) return 0.0
        return 0.2126 * lin(bytes[i].toInt() and 0xFF) +
               0.7152 * lin(bytes[i+1].toInt() and 0xFF) +
               0.0722 * lin(bytes[i+2].toInt() and 0xFF)
    }
    val cx = w / 2.0; val cy = h / 2.0
    var sumW = 0.0; var sumL = 0.0
    val highlights = mutableListOf<Double>()
    for (y in 0 until h step step) for (x in 0 until w step step) {
        val l = lum(y * rs + x * ps)
        val w2 = when (meteringMode) {
            "Spot" -> {
                val r2 = (minOf(w, h) / 6.0).pow(2)
                if ((x - cx).pow(2) + (y - cy).pow(2) < r2) 1.0 else 0.0
            }
            "Center-Weighted" -> max(0.2, 1.0 - sqrt((x - cx).pow(2) + (y - cy).pow(2)) / (w / 2.0) * 0.8)
            "Highlight-Weighted" -> { highlights.add(l); 1.0 }
            else -> 1.0
        }
        sumL += l * w2; sumW += w2
    }
    val avg = when {
        meteringMode == "Highlight-Weighted" ->
            highlights.sortedDescending().take(max(1, (highlights.size * 0.1).toInt())).average()
        sumW > 0 -> sumL / sumW
        else -> return null
    }
    // Base calibration: log2(avg/0.18) + 12 maps 18% grey to EV12 (sunny 16 approx)
    // -3 stops: phone AE keeps sensor in midrange regardless of scene, so raw pixels
    // read consistently bright; offset corrects for this systematic bias
    (log2(max(1e-6, avg) / 0.18) + 12.0 - 3.0).coerceIn(-2.0, 22.0)
} catch (e: Exception) { null }

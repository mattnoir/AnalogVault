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

@Composable
fun MeterScreen(vm: MainViewModel) {
    val zoomLevels by vm.zoomLevels.collectAsState()
    var hasCamPerm by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamPerm = it }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.CAMERA) }
    MeterContent(vm, zoomLevels, hasCamPerm) { permLauncher.launch(Manifest.permission.CAMERA) }
}

@Composable
fun MeterContent(
    vm: MainViewModel,
    zoomLevels: List<ZoomLevel>,
    hasCamPerm: Boolean,
    onRequestPerm: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var iso          by remember { mutableIntStateOf(400) }
    var shutter      by remember { mutableStateOf("1/125") }
    var metering     by remember { mutableStateOf(Constants.METERING_TYPES[0]) }
    var showZoomEdit by remember { mutableStateOf(false) }
    var activeZoom   by remember { mutableStateOf<ZoomLevel?>(null) }

    // Camera toggle
    var cameraOn     by remember { mutableStateOf(false) }
    var liveEV       by remember { mutableStateOf<Double?>(null) }

    // Manual EV — slider never resets because liveEV is separate
    var manualEV     by remember { mutableStateOf(12.0) }
    var evLocked     by remember { mutableStateOf(false) }
    val effectiveEV  = if (evLocked || liveEV == null) manualEV else liveEV!!

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo    by remember { mutableStateOf<CameraInfo?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var exifResult   by remember { mutableStateOf<ExifReading?>(null) }
    val exifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { exifResult = readExif(context, it) }
    }

    val solvedAperture = remember(iso, shutter, effectiveEV) {
        "f/${"%.1f".format(Constants.calcAperture(iso, shutter, effectiveEV))}"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // Live viewfinder
        if (cameraOn && hasCamPerm) {
            Box(Modifier.fillMaxWidth().height(260.dp).background(Bg2)) {
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
                                cameraInfo = cam.cameraInfo
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    },
                    update = {
                        // Apply zoom ratio when activeZoom changes
                        val zoomMm = activeZoom?.mm?.toFloat()
                        if (zoomMm != null) {
                            val state = cameraInfo?.zoomState?.value
                            if (state != null) {
                                val ratio = (zoomMm / 23f)   // 23mm ≈ 1x on most phones
                                    .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                cameraControl?.setZoomRatio(ratio)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // EV overlay top-left
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Bg.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        if (evLocked) "MANUAL ${"%.1f".format(effectiveEV)}" else "LIVE ${"%.1f".format(effectiveEV)}",
                        color = if (evLocked) OrangeWarn else AmberBright, fontSize = 12.sp
                    )
                }
                // Camera off button top-right
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Bg.copy(alpha = 0.78f))) {
                    IconButton(onClick = { cameraOn = false; liveEV = null }) {
                        Icon(imageVector = Icons.Default.VideocamOff, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
        }

        Column(Modifier.padding(16.dp)) {

            // Camera / EXIF buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultButton(
                    text = when {
                        cameraOn && evLocked -> "🔒 EV Locked"
                        cameraOn             -> "🔓 Lock EV"
                        hasCamPerm           -> "📷 Live Meter"
                        else                 -> "📷 Enable Camera"
                    },
                    modifier = Modifier.weight(1f),
                    ghost = true,
                    onClick = {
                        when {
                            !hasCamPerm  -> onRequestPerm()
                            !cameraOn    -> { cameraOn = true; evLocked = false }
                            else         -> {
                                evLocked = !evLocked
                                if (evLocked) manualEV = effectiveEV
                            }
                        }
                    }
                )
                VaultButton("📁 EXIF", ghost = true, small = true,
                    onClick = { exifPicker.launch("image/*") })
            }

            Spacer(Modifier.height(12.dp))

            // Zoom row
            if (zoomLevels.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zoomLevels) { z ->
                        val sel = activeZoom?.id == z.id
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
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
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Bg3)
                                .border(1.dp, Border, RoundedCornerShape(6.dp))
                                .clickable { showZoomEdit = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("✎ Edit", color = TextTertiary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            VaultDropdown("Metering Mode", metering, Constants.METERING_TYPES, { metering = it })
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VaultDropdown("ISO", iso.toString(), Constants.ISOS.map { it.toString() },
                    { iso = it.toIntOrNull() ?: 400 }, modifier = Modifier.weight(1f))
                VaultDropdown("Shutter", shutter, Constants.SHUTTER_SPEEDS,
                    { shutter = it }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            // Result card
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg3)
                .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SCENE EV", color = TextTertiary, fontSize = 9.sp)
                            Text("%.1f".format(effectiveEV), color = Amber, fontSize = 38.sp)
                            Text(if (evLocked || liveEV == null) "manual" else "live",
                                color = TextTertiary, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("APERTURE", color = TextTertiary, fontSize = 9.sp)
                            Text(solvedAperture, color = AmberBright, fontSize = 38.sp)
                            Text("ISO $iso · $shutter", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("NEARBY", color = TextTertiary, fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    listOf(-2, -1, 0, 1, 2).forEach { offset ->
                        val idx = (Constants.SHUTTER_SPEEDS.indexOf(shutter) + offset)
                            .coerceIn(0, Constants.SHUTTER_SPEEDS.lastIndex)
                        val sh = Constants.SHUTTER_SPEEDS[idx]
                        val ap = "f/${"%.1f".format(Constants.calcAperture(iso, sh, effectiveEV))}"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sh, color = if (offset == 0) AmberBright else TextSecondary, fontSize = 13.sp)
                            Text("→", color = TextTertiary, fontSize = 11.sp)
                            Text(ap, color = if (offset == 0) AmberBright else TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Manual EV slider — touching this locks EV immediately
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Manual EV", color = TextSecondary, fontSize = 12.sp)
                Text("%.1f".format(manualEV), color = Amber, fontSize = 12.sp)
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
            if (cameraOn && !evLocked) {
                Text("Drag slider to override live reading", color = TextTertiary, fontSize = 10.sp)
            }

            // EXIF panel
            exifResult?.let { exif ->
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(BlueInfo.copy(alpha = 0.08f))
                    .border(1.dp, BlueInfo.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("EXIF", color = BlueInfo, fontSize = 11.sp)
                            IconButton(onClick = { exifResult = null }, Modifier.size(20.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            exif.iso?.let { VaultTag("ISO $it", textColor = AmberBright) }
                            exif.shutter?.let { VaultTag(it, textColor = AmberBright) }
                            exif.aperture?.let { VaultTag("f/$it", textColor = AmberBright) }
                        }
                        val exifEV = if (exif.iso != null && exif.shutter != null && exif.aperture != null) {
                            val ap = exif.aperture.toDoubleOrNull() ?: 0.0
                            val t = Constants.evalShutter(exif.shutter)
                            if (ap > 0 && t > 0) (log2(ap * ap / t) - log2(exif.iso / 100.0)) else null
                        } else null
                        exifEV?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("EV: ${"%.1f".format(it)}", color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VaultButton("Use EV", small = true,
                                    onClick = { manualEV = it; evLocked = true })
                                VaultButton("Apply ISO+Shutter", small = true, ghost = true, onClick = {
                                    exif.iso?.let { v -> iso = v }
                                    exif.shutter?.let { v -> shutter = v }
                                })
                            }
                        }
                        exif.lens?.let {
                            Spacer(Modifier.height(4.dp))
                            Text("Lens: $it", color = TextTertiary, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showZoomEdit) ZoomEditSheet(zoomLevels, vm) { showZoomEdit = false }
}

// ─── Zoom Edit Sheet (unchanged) ──────────────────────────────────────────────

@Composable
fun ZoomEditSheet(zoomLevels: List<ZoomLevel>, vm: MainViewModel, onDismiss: () -> Unit) {
    var newLabel by remember { mutableStateOf("") }
    var newMm    by remember { mutableStateOf("") }
    VaultSheet("Edit Zoom Levels", onDismiss) {
        zoomLevels.forEach { z ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${z.label}  ${z.mm}mm", color = TextPrimary, fontSize = 13.sp)
                IconButton(onClick = { vm.deleteZoomLevel(z) }, Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = RedErr.copy(alpha = 0.7f))
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
                vm.upsertZoomLevel(ZoomLevel(uid(), newLabel, newMm.toIntOrNull() ?: 0))
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


// ─── EV analysis ─────────────────────────────────────────────────────────────

private fun analyzeEVCalibrated(imageProxy: ImageProxy, meteringMode: String): Double? = try {
    val plane = imageProxy.planes[0]
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    val w = imageProxy.width; val h = imageProxy.height
    val rs = plane.rowStride; val ps = plane.pixelStride
    val step = 4
    fun lin(c: Int): Double { val s = c/255.0; return if (s<=0.04045) s/12.92 else ((s+0.055)/1.055).pow(2.4) }
    fun lum(i: Int): Double {
        if (i+2>=bytes.size) return 0.0
        return 0.2126*lin(bytes[i].toInt() and 0xFF) + 0.7152*lin(bytes[i+1].toInt() and 0xFF) + 0.0722*lin(bytes[i+2].toInt() and 0xFF)
    }
    val lums = ArrayList<Double>((w/step) * (h/step))
    for (y in 0 until h step step) for (x in 0 until w step step) lums.add(lum(y*rs + x*ps))
    if (lums.isEmpty()) null else {
        val cx = w/2.0; val cy = h/2.0
        val avg = when (meteringMode) {
            "Spot" -> {
                val r2 = (minOf(w,h)/6.0).pow(2)
                var s = 0.0; var n = 0
                for (y in 0 until h step step) for (x in 0 until w step step) {
                    if ((x-cx).pow(2)+(y-cy).pow(2) < r2) { s += lum(y*rs+x*ps); n++ }
                }
                if (n > 0) s/n else lums.average()
            }
            "Center-Weighted" -> {
                var s = 0.0; var wt = 0.0
                for (y in 0 until h step step) for (x in 0 until w step step) {
                    val d = sqrt((x-cx).pow(2)+(y-cy).pow(2)) / (w/2.0)
                    val w2 = max(0.2, 1.0 - d*0.8)
                    s += lum(y*rs+x*ps)*w2; wt += w2
                }
                if (wt > 0) s/wt else lums.average()
            }
            "Highlight-Weighted" -> lums.sortedDescending().take(max(1,(lums.size*0.1).toInt())).average()
            else -> lums.average()
        }
        (log2(max(1e-6, avg) / 0.18) + 12.0 - 3.0).coerceIn(-2.0, 22.0)
    }
} catch (e: Exception) { null }

package com.analogvault.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.analogvault.data.model.ZoomLevel
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.*

@Composable
fun MeterScreen(vm: MainViewModel) {
    val zoomLevels by vm.zoomLevels.collectAsState()

    var hasCamPerm by remember { mutableStateOf(false) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { hasCamPerm = it }

    LaunchedEffect(Unit) {
        permLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCamPerm) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required for light meter", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                VaultButton("Grant Permission", onClick = { permLauncher.launch(Manifest.permission.CAMERA) })
            }
        }
    } else {
        MeterContent(vm, zoomLevels)
    }
}

@Composable
fun MeterContent(vm: MainViewModel, zoomLevels: List<ZoomLevel>) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Meter state
    var iso          by remember { mutableIntStateOf(400) }
    var shutter      by remember { mutableStateOf("1/125") }
    var metering     by remember { mutableStateOf(Constants.METERING_TYPES[0]) }
    var measuredEV   by remember { mutableStateOf<Double?>(null) }
    var targetEV     by remember { mutableStateOf(12.0) }
    var activeZoom   by remember { mutableStateOf<ZoomLevel?>(null) }
    var showZoomEdit by remember { mutableStateOf(false) }

    val solvedAperture = remember(iso, shutter, targetEV) {
        val ap = Constants.calcAperture(iso, shutter, targetEV)
        "f/%.1f".format(ap)
    }

    // Analysis executor
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Viewfinder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Bg2)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val ev = analyzeEV(imageProxy, metering)
                            measuredEV = ev
                            if (ev != null) targetEV = ev
                            imageProxy.close()
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) { e.printStackTrace() }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            // EV overlay
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
                .clip(RoundedCornerShape(6.dp)).background(Bg.copy(alpha = 0.75f)).padding(8.dp)) {
                Text("EV ${"%.1f".format(measuredEV ?: targetEV)}", color = AmberBright, fontSize = 14.sp)
            }
            // Metering mode overlay
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(RoundedCornerShape(6.dp)).background(Bg.copy(alpha = 0.75f)).padding(4.dp)) {
                Text(metering.split("/").first(), color = TextSecondary, fontSize = 10.sp)
            }
        }

        Column(Modifier.padding(16.dp)) {
            // Zoom levels row
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
                Spacer(Modifier.height(14.dp))
            }

            // Metering mode picker
            VaultDropdown("Metering Mode", metering, Constants.METERING_TYPES, { metering = it })
            Spacer(Modifier.height(10.dp))

            // ISO + Shutter
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VaultDropdown("ISO", iso.toString(), Constants.ISOS.map { it.toString() },
                    { iso = it.toIntOrNull() ?: 400 }, modifier = Modifier.weight(1f))
                VaultDropdown("Shutter", shutter, Constants.SHUTTER_SPEEDS,
                    { shutter = it }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            // EV display + aperture recommendation
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Bg3)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SCENE EV", color = TextTertiary, fontSize = 9.sp)
                            Text("%.1f".format(measuredEV ?: targetEV), color = Amber, fontSize = 36.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("RECOMMENDED APERTURE", color = TextTertiary, fontSize = 9.sp)
                            Text(solvedAperture, color = AmberBright, fontSize = 36.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("at ISO $iso · $shutter", color = TextSecondary, fontSize = 12.sp)

                    // Nearby aperture options
                    Spacer(Modifier.height(12.dp))
                    Text("NEARBY OPTIONS", color = TextTertiary, fontSize = 9.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(-1, 0, 1).forEach { offset ->
                            val adjShIdx = (Constants.SHUTTER_SPEEDS.indexOf(shutter) + offset)
                                .coerceIn(0, Constants.SHUTTER_SPEEDS.lastIndex)
                            val adjSh = Constants.SHUTTER_SPEEDS[adjShIdx]
                            val ap = Constants.calcAperture(iso, adjSh, targetEV)
                            VaultTag("$adjSh → f/${"%.1f".format(ap)}",
                                textColor = if (offset == 0) AmberBright else TextSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // EV manual override slider
            Text("Manual EV adjust: ${"%.1f".format(targetEV)}", color = TextSecondary, fontSize = 12.sp)
            Slider(
                value = targetEV.toFloat(),
                onValueChange = { targetEV = it.toDouble() },
                valueRange = -2f..22f,
                steps = 95,
                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Border)
            )
        }
    }

    if (showZoomEdit) {
        ZoomEditSheet(zoomLevels, vm, onDismiss = { showZoomEdit = false })
    }
}

// ─── Zoom Edit Sheet ──────────────────────────────────────────────────────────

@Composable
fun ZoomEditSheet(zoomLevels: List<ZoomLevel>, vm: MainViewModel, onDismiss: () -> Unit) {
    var newLabel by remember { mutableStateOf("") }
    var newMm    by remember { mutableStateOf("") }

    VaultSheet("Edit Zoom Levels", onDismiss) {
        zoomLevels.forEach { z ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${z.label}  ${z.mm}mm", color = TextPrimary, fontSize = 13.sp)
                IconButton(onClick = { vm.deleteZoomLevel(z) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
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

// ─── EV Analysis ─────────────────────────────────────────────────────────────

private fun analyzeEV(imageProxy: ImageProxy, meteringMode: String): Double? {
    return try {
        val plane  = imageProxy.planes[0]
        val buffer = plane.buffer
        val bytes  = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val w = imageProxy.width
        val h = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        fun linearize(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        fun lum(i: Int): Double {
            val r = bytes[i].toInt() and 0xFF
            val g = bytes[i+1].toInt() and 0xFF
            val b = bytes[i+2].toInt() and 0xFF
            return 0.2126*linearize(r) + 0.7152*linearize(g) + 0.0722*linearize(b)
        }

        val lums = mutableListOf<Double>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * rowStride + x * pixelStride
                if (i + 2 < bytes.size) lums.add(lum(i))
            }
        }
        if (lums.isEmpty()) return null

        val avg = when (meteringMode) {
            "Spot" -> {
                val cx = w / 2; val cy = h / 2; val r = w / 4
                val spot = mutableListOf<Double>()
                for (y in 0 until h) for (x in 0 until w) {
                    if ((x-cx)*(x-cx) + (y-cy)*(y-cy) < r*r) {
                        val i = y * rowStride + x * pixelStride
                        if (i + 2 < bytes.size) spot.add(lum(i))
                    }
                }
                if (spot.isEmpty()) lums.average() else spot.average()
            }
            "Center-Weighted" -> {
                var sum = 0.0; var wt = 0.0
                val cx = w / 2.0; val cy = h / 2.0
                for (y in 0 until h) for (x in 0 until w) {
                    val d = sqrt((x-cx).pow(2) + (y-cy).pow(2)) / (w / 2.0)
                    val w2 = max(0.3, 1.0 - d * 0.7)
                    val i = y * rowStride + x * pixelStride
                    if (i + 2 < bytes.size) { sum += lum(i) * w2; wt += w2 }
                }
                if (wt == 0.0) lums.average() else sum / wt
            }
            "Highlight-Weighted" -> {
                val sorted = lums.sortedDescending()
                sorted.take(max(1, (sorted.size * 0.15).toInt())).average()
            }
            else -> lums.average()
        }

        val clamped = max(1e-4, avg)
        max(-2.0, min(22.0, log2(clamped / 0.18) + 12.0))
    } catch (e: Exception) { null }
}

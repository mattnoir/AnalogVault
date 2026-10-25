package com.analogvault.ui.screens

import android.Manifest
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.analogvault.data.model.Camera
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Lens
import com.analogvault.data.model.Roll
import com.analogvault.data.model.ZoomLevel
import com.analogvault.data.model.meterFrame
import com.analogvault.data.model.fixedLens
import com.analogvault.data.model.isMonochrome
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.MeterPriority
import com.analogvault.ui.components.VaultButton
import com.analogvault.ui.components.VaultDropdown
import com.analogvault.ui.components.VaultSheet
import com.analogvault.ui.components.VaultTextField
import com.analogvault.ui.film.FilmChip
import com.analogvault.ui.film.SprocketRail
import com.analogvault.ui.film.halation
import com.analogvault.ui.film.hardShadow
import com.analogvault.ui.film.hazardHatch
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.analogvault.util.Exposure
import com.analogvault.util.GearClamp
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import com.analogvault.ui.film.DyeIcon
import com.analogvault.ui.film.FilmIcons

// ─── Entry ────────────────────────────────────────────────────────────────────

@Composable
fun MeterScreen(
    vm: MainViewModel,
    onClose: () -> Unit = {},
    onUseInShot: ((shutter: String, aperture: String, iso: String) -> Unit)? = null
) {
    val zoomLevels by vm.zoomLevels.collectAsState()
    val displayZooms = remember(zoomLevels) {
        zoomLevels.distinctBy { it.label to it.mm }.sortedBy { it.mm }
    }
    val context = LocalContext.current
    var hasCamPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamPerm = it }
    // Only prompt when not already granted — avoids re-asking on every visit
    LaunchedEffect(Unit) { if (!hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA) }
    MeterContent(
        vm, displayZooms, hasCamPerm,
        { permLauncher.launch(Manifest.permission.CAMERA) },
        onClose, onUseInShot,
    )
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
// CameraX's Camera2Interop is "experimental" but in 1.5.x it is NOT a @RequiresOptIn
// marker, so neither @OptIn nor a compiler opt-in flag has any effect (the IDE may still
// flag the usage — a false positive; the Gradle build is the source of truth and compiles).

@Composable
fun MeterContent(
    vm: MainViewModel,
    zoomLevels: List<ZoomLevel>,
    hasCamPerm: Boolean,
    onRequestPerm: () -> Unit,
    onClose: () -> Unit,
    onUseInShot: ((shutter: String, aperture: String, iso: String) -> Unit)? = null
) {
    val colors = FilmTheme.colors
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── The roll being metered for ────────────────────────────────────────────
    val rolls    by vm.rolls.collectAsState()
    val films    by vm.films.collectAsState()
    val cameras  by vm.cameras.collectAsState()
    val lenses   by vm.lenses.collectAsState()
    val rollId   by vm.meterRollId.collectAsState()

    val loadedRolls = remember(rolls) {
        rolls.filter { !it.finished && !it.developed }.sortedByDescending { it.startDate }
    }
    // Default to the roll actually in a camera, so the common case — one roll
    // loaded, meter it — needs no picking at all.
    LaunchedEffect(loadedRolls) {
        if (rollId == null || loadedRolls.none { it.id == rollId }) {
            vm.setMeterRoll(loadedRolls.firstOrNull()?.id)
        }
    }
    val roll   = loadedRolls.find { it.id == rollId }
    val film   = films.find { it.id == roll?.filmId }
    val camera = cameras.find { it.id == roll?.cameraId }
    val lensOverrides by vm.meterLensOverrides.collectAsState()
    val rollLens = lenses.find { it.id == roll?.cameraLensId }
    // A lens swapped on in the meter wins over the one the roll was loaded
    // with — for the meter only, the roll keeps its own. A fixed-lens body has
    // no stash lens on the roll; its own aperture range stands in so the
    // ladder clamps on the glass it was built with.
    val lens = roll?.let { r -> lensOverrides[r.id]?.let { id -> lenses.find { it.id == id } } }
        ?: rollLens ?: camera?.fixedLens()
    // Only glass that fits the body, natively or through an adapter you own —
    // the same list the roll's lens is picked from.
    val swappableLenses = remember(camera, lenses) {
        if (camera?.lensSystem != "interchangeable") emptyList()
        else lenses.filter {
            Constants.mountCompat(camera.mount, it.mount, camera.adapterMounts) != "incompatible"
        }
    }

    // ── User inputs — persisted via settings so the per-device calibration
    //    (and last-used ISO/shutter/metering) survive restarts ────────────────
    val meterIso    by vm.meterIso.collectAsState()
    val shutter     by vm.meterShutter.collectAsState()
    val metering    by vm.meterMetering.collectAsState()
    val priority    by vm.meterPriority.collectAsState()
    val aperturePriority = priority == MeterPriority.APERTURE
    val manual = priority == MeterPriority.MANUAL
    val calibThirds by vm.meterCalibThirds.collectAsState()
    val recipFilm   by vm.recipFilm.collectAsState()
    val monoContrast by vm.meterMonoContrast.collectAsState()
    // Draft tracks the ruler while dragging so the preview follows the finger
    var contrastDraft by remember(monoContrast) { mutableIntStateOf(monoContrast) }
    // Draft tracks the ruler while dragging; persisted on release
    var calibDraft  by remember(calibThirds) { mutableIntStateOf(calibThirds) }
    val calibOffset = calibDraft / 3.0

    // A roll's speed wins over the meter's own ISO. Metering a loaded roll at
    // some other speed is not a preference, it is a mistake, so the control
    // becomes a readout for as long as a roll is selected.
    val rollIso = rollSpeed(roll, film)
    val filmIso = rollIso ?: meterIso

    // ── Live meter state — lives in the ViewModel so readings, EV lock and
    //    zone marks survive navigation ─────────────────────────────────────────
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
    // Kept so a logged frame can carry what the viewfinder showed at that moment.
    var previewView  by remember { mutableStateOf<PreviewView?>(null) }
    var appliedRegionKey by remember { mutableStateOf<String?>(null) }
    /** Null until the camera binds and we can ask the characteristics. */
    var aeRegionsSupported by remember { mutableStateOf<Boolean?>(null) }
    // Reticle position as a fraction of the preview. Two values: the one being
    // dragged (redrawn every frame) and the one the AE region was last set from.
    // Re-issuing startFocusAndMetering on every pointer sample makes the camera
    // hunt continuously and the reading never settles.
    var reticle by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var committedReticle by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    // Devices with no AE regions can only give a whole-frame reading. Say so and
    // meter centre-weighted rather than drawing a spot reticle that does nothing.
    val spotUnsupported = aeRegionsSupported == false &&
        (metering == Constants.METERING_SPOT || metering == Constants.METERING_CENTRE)
    val effectiveMetering =
        if (spotUnsupported) Constants.METERING_CENTRE else metering

    // A black-and-white roll previews in grey unless switched off; anything
    // else previews in colour unless switched on. Grey is also the quick way
    // to judge a scene's contrast without its colours getting in the way.
    val monoOverrides by vm.meterMonoOverrides.collectAsState()
    val filmMono = film?.isMonochrome() == true
    val mono = monoOverrides[roll?.id] ?: filmMono

    // The camera binds to the Activity lifecycle; without an explicit unbind it
    // keeps running (privacy indicator on, battery draining) after the user
    // leaves the meter.
    DisposableEffect(Unit) { onDispose { providerRef?.unbindAll() } }

    // Base EV before zone placement: locked → manual, live → calibrated reading
    val baseEV = when {
        evLocked            -> manualEV
        liveReading != null -> (liveReading!!.sceneEV + calibOffset).coerceIn(-2.0, 22.0)
        else                -> manualEV
    }
    val zoneActive  = zoneEnabled && effectiveMetering == Constants.METERING_SPOT
    val effectiveEV = if (zoneActive) baseEV + Exposure.zoneOffsetEv(zone) else baseEV

    // ── Zoom ──────────────────────────────────────────────────────────────────
    var activeZoom   by remember { mutableStateOf<ZoomLevel?>(null) }
    var showZoomEdit by remember { mutableStateOf(false) }

    // ── The clamp ─────────────────────────────────────────────────────────────
    val limits = remember(camera, lens) { GearClamp.limitsOf(camera, lens) }
    // Aperture priority and manual hold the lens wide open unless a rung or a
    // stop on the manual dial was tapped. The tap lasts until the meter closes
    // or the lens changes: opening the meter again means wide open again,
    // whatever was dialled last time.
    var heldApertureTap by remember(lens?.id, lens?.maxAperture) { mutableStateOf<Double?>(null) }
    val heldAperture = heldApertureTap ?: limits.widestAperture ?: FALLBACK_HELD_APERTURE
    val heldIsWideOpen = heldApertureTap == null && limits.widestAperture != null
    // Holding the widest stop goes back to following the lens, so a later lens
    // swap opens wide on the new glass.
    val holdAperture = { ap: Double ->
        val wideOpen = limits.widestAperture?.let { abs(log2(ap / it)) < 0.1 } == true
        heldApertureTap = if (wideOpen) null else ap
    }
    val rungs = remember(filmIso, effectiveEV, limits, priority, shutter) {
        GearClamp.ladder(filmIso, effectiveEV, limits)
            // Rungs needing an aperture no lens has are noise on every ladder,
            // clamped or not. Filter on the exact value, not the snapped one:
            // snapping always lands inside the scale, so a rung wanting f/0.1
            // would survive as a plausible-looking f/0.95. Whole stops only —
            // the dial on a film body has whole stops, and 30 third-stop rungs
            // is a scroll, not a choice.
            .filter { it.apertureExact >= 0.95 && it.apertureExact <= 32.0 }
            .filter {
                it.shutter in Constants.WHOLE_STOP_SHUTTERS ||
                    (priority == MeterPriority.SHUTTER && it.shutter == shutter)
            }
    }
    // Manual picks no rung: both halves are dialled, and the light only gets
    // to say how far off they are.
    val selected = when (priority) {
        MeterPriority.APERTURE -> GearClamp.rungForAperture(rungs, filmIso, effectiveEV, heldAperture)
        MeterPriority.SHUTTER  -> rungs.find { it.shutter == shutter }
        MeterPriority.MANUAL   -> null
    }
    // The speed the readouts and the commit bar name. Under aperture priority
    // it is whatever the light asks of the held aperture, not the stored one.
    val activeShutter = selected?.shutter
        ?: if (aperturePriority) Exposure.nearestStandardShutter(
            GearClamp.shutterSecFor(filmIso, heldAperture, effectiveEV)
        ) else shutter
    val advice = remember(filmIso, effectiveEV, limits, camera, lens) {
        GearClamp.advise(filmIso, effectiveEV, limits, camera, lens)
    }

    // Shutter seconds the reciprocity helper should correct
    val displaySec = selected?.shutterSec ?: Constants.evalShutter(activeShutter)
    // Manual takes the held stop as it is: an f/1.7 lens set wide open is f/1.7,
    // not the f/1.8 the third-stop scale would snap it to.
    val apExact = if (manual) heldAperture
        else selected?.apertureExact ?: GearClamp.apertureFor(filmIso, displaySec, effectiveEV)
    val apSnapped = if (manual) heldAperture
        else selected?.aperture ?: Constants.nearestStandardAperture(apExact)
    // Signed stops between what is dialled and what the light asks for. Manual only.
    val manualOff = if (manual) GearClamp.stopsOff(filmIso, effectiveEV, displaySec, heldAperture) else null
    // Why the frame cannot be committed, or null when it can.
    //
    // Only a missing rung blocks. As the light moves, the stored shutter can
    // stop having any f-stop on the standard scale while other rungs remain
    // perfectly usable; leaving the button live then would log the frame at the
    // scale's floor. A rung outside the gear's range does not block: it is
    // hatched and the banner says why, but choosing it is the photographer's
    // call — a third wider than the lens is a third under at full aperture.
    // Manual never blocks: whatever is dialled is the frame.
    val blockedLabel = when {
        manual || selected != null -> null
        aperturePriority           -> "NO SPEED FITS"
        else                       -> "PICK A SPEED"
    }
    // Under manual only the dialled values count. A scene the gear cannot
    // expose correctly is the banner's business; a frame set inside the gear's
    // range and logged two stops under on purpose is not beyond it.
    val beyondGear = if (manual) {
        GearClamp.shutterReason(displaySec, limits) != null ||
            GearClamp.apertureReason(heldAperture, limits) != null
    } else advice != null || selected?.achievable == false
    val unreachable = blockedLabel != null || beyondGear
    // A dash, not a snapped number, once the answer leaves the scale: rounding
    // f/0.1 up to f/0.95 prints an f-stop that exists and is still four stops out.
    val apertureLabel =
        if (apExact < 0.95 || apExact > 32.0) "—" else GearClamp.formatAperture(apSnapped)

    // ── Frame ─────────────────────────────────────────────────────────────────
    // The preview is cropped to the shape of the negative. A phone sensor is
    // one fixed rectangle, so a frame turned across it loses some of the
    // other side: horizontal on an upright phone is a strip of a tall image.
    val frameChoice   by vm.meterFrame.collectAsState()
    val frameVertical by vm.meterFrameVertical.collectAsState()
    val gridOn        by vm.meterGrid.collectAsState()
    val autoFrame = camera.meterFrame() ?: film.meterFrame()
    val frameKey = if (frameChoice == Constants.METER_FRAME_AUTO) autoFrame else frameChoice
    /** Long side over short side; null fills the viewfinder uncropped. */
    val frameLong = frameKey?.let { Constants.METER_FRAMES[it] }
    val frameTurns = frameLong != null && frameLong != 1f
    /** Width over height, as laid out. */
    val frameRatio = frameLong?.let { if (frameVertical && frameTurns) 1f / it else it }
    // An upright frame gets a taller viewfinder; at the usual height a 2:3
    // would be a postage stamp in the middle of the screen.
    val viewfinderHeight = if (frameRatio != null && frameRatio < 1f) 300.dp else 250.dp

    // ── Layout ───────────────────────────────────────────────────────────────
    // The outer Column is NOT scrollable — AndroidView breaks verticalScroll.
    // Viewfinder and commit bar are fixed; only the controls between them scroll.
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.void)
    ) {
        MeterStatusBar(
            roll = roll, film = film, camera = camera,
            rollCount = loadedRolls.size,
            onCycleRoll = {
                if (loadedRolls.size > 1) {
                    val i = loadedRolls.indexOfFirst { it.id == rollId }
                    vm.setMeterRoll(loadedRolls[(i + 1) % loadedRolls.size].id)
                }
            },
            onClose = onClose,
        )

        // ── VIEWFINDER ────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(viewfinderHeight)
                .clipToBounds()
                .background(colors.film)
        ) {
            // Preview and overlay share one box so the reticle, the grid and the
            // AE region all live inside the frame, not the letterbox around it.
            val frameModifier = Modifier
                .align(Alignment.Center)
                .then(if (frameRatio == null) Modifier.fillMaxSize() else Modifier.aspectRatio(frameRatio))
                // FILL_CENTER scales the TextureView past the frame; without a clip
                // the overflow paints into the letterbox.
                .clipToBounds()
            if (cameraOn && hasCamPerm) {
                AndroidView(
                    factory = { ctx ->
                        val pv = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        previewView = pv
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
                                // A spot reading has to be a real AE region. Ask the
                                // sensor how many it will accept before promising one.
                                aeRegionsSupported = try {
                                    (Camera2CameraInfo.from(cam.cameraInfo)
                                        .getCameraCharacteristic(
                                            CameraCharacteristics.CONTROL_MAX_REGIONS_AE
                                        ) ?: 0) > 0
                                } catch (_: Exception) { false }
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        // A new frame resizes the view, and a metering point maps
                        // through the view's size: re-aim the AE region once the
                        // preview has settled into its new shape.
                        pv.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                            if (r - l != oldR - oldL || b - t != oldB - oldT) {
                                pv.post { appliedRegionKey = null }
                            }
                        }
                        pv
                    },
                    update = { pv ->
                        pv.applyMonoFilter(if (mono) contrastDraft / 10f else null)
                        // Apply zoom
                        activeZoom?.mm?.toFloat()?.let { mm ->
                            cameraInfoObj?.zoomState?.value?.let { state ->
                                val ratio = (mm / 23f).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                cameraCtrl?.setZoomRatio(ratio)
                            }
                        }
                        // Drive a real CONTROL_AE_REGIONS rectangle at the reticle.
                        // Cropping the preview would look the same and mean nothing:
                        // the number would still be the whole-frame average.
                        val ctrl = cameraCtrl
                        val key = "$effectiveMetering:${committedReticle.x}:${committedReticle.y}"
                        if (ctrl != null && pv.width > 0 && appliedRegionKey != key) {
                            appliedRegionKey = key
                            try {
                                val regionSize = when (effectiveMetering) {
                                    Constants.METERING_SPOT   -> 0.15f
                                    Constants.METERING_CENTRE -> 0.6f
                                    else                      -> null
                                }
                                if (regionSize != null && aeRegionsSupported != false) {
                                    val pt = pv.meteringPointFactory.createPoint(
                                        pv.width * committedReticle.x,
                                        pv.height * committedReticle.y,
                                        regionSize,
                                    )
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
                    modifier = frameModifier
                )
            } else {
                // No preview: the readouts still have to work, so the viewfinder
                // becomes the panel that offers to turn the camera on.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(colors.filmRaised, colors.film, colors.void)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MeterSegment(
                        label = if (hasCamPerm) "LIVE METER" else "ENABLE CAMERA",
                        selected = true,
                        accent = colors.cyan,
                        modifier = Modifier.width(190.dp),
                        onClick = {
                            if (hasCamPerm) { cameraOn = true; vm.setMeterLock(false) }
                            else onRequestPerm()
                        },
                    )
                }
            }

            // Reticle and framing marks, drawn only over a running preview.
            //
            // The overlay must not exist when the camera is off: a full-bleed
            // Canvas carrying pointerInput is a hit-test target across the whole
            // viewfinder, and it swallowed every tap aimed at the "live meter"
            // panel underneath it. Same reason the gesture modifiers are attached
            // conditionally rather than returning early inside the block.
            if (cameraOn && hasCamPerm) {
                val draggable = aeRegionsSupported != false &&
                    (effectiveMetering == Constants.METERING_SPOT ||
                        effectiveMetering == Constants.METERING_CENTRE)
                Canvas(
                    frameModifier
                        // Bright-line edge, only when there is a letterbox to
                        // set the frame apart from
                        .then(
                            if (frameRatio == null) Modifier
                            else Modifier.border(1.dp, colors.halide.copy(alpha = 0.35f))
                        )
                        .then(
                            if (!draggable) Modifier else Modifier
                                .pointerInput(effectiveMetering) {
                                    detectDragGestures(
                                        onDrag = { change, drag ->
                                            change.consume()
                                            reticle = Offset(
                                                (reticle.x + drag.x / size.width).coerceIn(0.06f, 0.94f),
                                                (reticle.y + drag.y / size.height).coerceIn(0.08f, 0.92f),
                                            )
                                        },
                                        // Commit on release, not per sample:
                                        // re-arming AE mid drag makes the reading
                                        // chase the finger and settle on nothing.
                                        onDragEnd = { committedReticle = reticle },
                                    )
                                }
                                .pointerInput(effectiveMetering) {
                                    detectTapGestures(
                                        onTap = { ofs ->
                                            reticle = Offset(
                                                (ofs.x / size.width).coerceIn(0.06f, 0.94f),
                                                (ofs.y / size.height).coerceIn(0.08f, 0.92f),
                                            )
                                            committedReticle = reticle
                                        },
                                        onDoubleTap = {
                                            reticle = Offset(0.5f, 0.5f)
                                            committedReticle = reticle
                                        },
                                    )
                                }
                        )
                ) {
                    drawMeteringOverlay(
                        metering = effectiveMetering,
                        reticle = reticle,
                        reticleColor = colors.magenta,
                        gridColor = colors.halide.copy(alpha = 0.13f),
                        grid = gridOn,
                        active = draggable,
                    )
                }
            }

            if (cameraOn && liveReading != null) {
                Text(
                    if (evLocked) "HELD · EV ${"%.1f".format(baseEV)}" else "LIVE · CAMERA2",
                    style = FilmTheme.type.rebate,
                    color = if (evLocked) colors.yellow else colors.cyan,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(colors.void.copy(alpha = 0.7f))
                        .border(1.dp, if (evLocked) colors.yellow else colors.cyan)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            if (spotUnsupported) {
                Text(
                    "NO AE REGIONS ON THIS PHONE · CENTRE-WEIGHTED",
                    style = FilmTheme.type.rebate,
                    color = colors.mask,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(colors.void.copy(alpha = 0.7f))
                        .border(1.dp, colors.mask)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            // Readouts over the preview. You are holding a camera in the other
            // hand; the numbers belong where you are already pointing.
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to colors.void.copy(alpha = 0.6f),
                            1f to colors.void.copy(alpha = 0.94f),
                        )
                    )
                    .padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 11.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SCENE EV", style = FilmTheme.type.rebate, color = colors.dim)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "%.1f".format(effectiveEV),
                        style = FilmTheme.type.readout,
                        color = colors.yellow,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.halation(colors.yellow, 18.dp, 0.22f, !colors.safelight),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        apertureLabel,
                        style = FilmTheme.type.stock,
                        color = if (unreachable) colors.mask else colors.cyan,
                        maxLines = 1, softWrap = false,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        (manualOff?.let { offLabel(it) } ?: "EXACT f/${"%.1f".format(apExact)}") +
                            " · ISO $filmIso · $activeShutter",
                        style = FilmTheme.type.rebate,
                        color = colors.dim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── CLAMP BANNER ──────────────────────────────────────────────────────
        // The one thing on this screen that has to interrupt: the gear in your
        // hands cannot take the frame you are pointing it at.
        advice?.let { ClampBanner(it) }

        // ── CONTROLS ──────────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // The ladder leads because it is the control the commit bar reads
            // from: everything below it adjusts the reading, this is where you
            // choose the frame. The design sketch put it last, on a mockup that
            // had no manual-EV ruler and no film-speed row above it.
            MeterEyebrow(if (manual) "Manual exposure" else "Equivalent exposures")
            PriorityToggle(
                priority = priority,
                heldAperture = heldAperture,
                heldIsWideOpen = heldIsWideOpen,
                canReopen = heldApertureTap != null && limits.widestAperture != null,
                heldShutter = shutter,
                onPriority = { p ->
                    // Manual takes over from where the meter was, the way flicking
                    // a body from auto to M leaves the dials where auto put them:
                    // the speed aperture priority found, or the stop shutter
                    // priority solved for.
                    if (p == MeterPriority.MANUAL && !manual) {
                        if (aperturePriority) vm.saveMeterShutter(activeShutter)
                        else if (blockedLabel == null && apExact in 0.95..32.0) holdAperture(apSnapped)
                    }
                    vm.saveMeterPriority(p)
                },
                onWideOpen = { heldApertureTap = null },
            )
            Spacer(Modifier.height(8.dp))
            if (manualOff != null) {
                MatchNeedle(manualOff)
                Spacer(Modifier.height(10.dp))
                ManualDial(
                    title = "SHUTTER",
                    values = (Constants.WHOLE_STOP_SHUTTERS + shutter)
                        .filter { it != "B" }
                        .distinct()
                        .sortedBy { Constants.evalShutter(it) },
                    isSelected = { it == shutter },
                    label = { it },
                    achievable = { GearClamp.shutterReason(Constants.evalShutter(it), limits) == null },
                    onSelect = { vm.saveMeterShutter(it) },
                )
                Spacer(Modifier.height(8.dp))
                ManualDial(
                    title = "APERTURE",
                    // Whole stops, plus the lens's own wide-open value and the
                    // stop being held, either of which may sit between them.
                    values = (Constants.WHOLE_STOP_APERTURES +
                        listOfNotNull(limits.widestAperture, heldAperture))
                        .sorted()
                        .fold(emptyList<Double>()) { acc, ap ->
                            if (acc.lastOrNull()?.let { abs(log2(ap / it)) < 0.1 } == true) acc
                            else acc + ap
                        },
                    isSelected = { abs(log2(it / heldAperture)) < 0.1 },
                    label = { GearClamp.formatAperture(it) },
                    achievable = { GearClamp.apertureReason(it, limits) == null },
                    onSelect = holdAperture,
                )
            } else if (rungs.isEmpty()) {
                // No standard shutter maps onto an aperture any lens carries.
                // The banner above says what to do; this only has to explain why
                // the ladder is not there.
                Text(
                    "NOTHING ON THE STANDARD SCALES EXPOSES THIS SCENE",
                    style = FilmTheme.type.data, color = colors.dead,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            } else {
                ExposureLadder(
                    rungs = rungs,
                    selectedShutter = selected?.shutter,
                    onSelect = { rung ->
                        if (aperturePriority) holdAperture(rung.aperture)
                        else vm.saveMeterShutter(rung.shutter)
                    },
                )
            }
            if (!limits.unclamped) {
                Text(
                    hatchLegend(camera, lens),
                    style = FilmTheme.type.rebate, color = colors.dim,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                )
            }

            // ── Lens (interchangeable bodies) ─────────────────────────────────
            if (roll != null && swappableLenses.isNotEmpty()) {
                MeterEyebrow("Lens · meter only")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(swappableLenses, key = { it.id }) { l ->
                        val sel = lens?.id == l.id
                        val onRoll = l.id == roll.cameraLensId
                        Column(
                            Modifier
                                .background(colors.film)
                                .border(1.dp, if (sel) colors.violet else colors.edge)
                                .clickable {
                                    vm.setMeterLens(roll.id, l.id.takeUnless { onRoll })
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                l.name.ifBlank { "${l.focalLength}MM" },
                                style = FilmTheme.type.data,
                                color = if (sel) colors.violet else colors.halide,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp),
                            )
                            Text(
                                listOfNotNull(
                                    l.maxAperture.takeIf { it.isNotBlank() }?.let { "F/$it" },
                                    "ON ROLL".takeIf { onRoll },
                                ).joinToString(" · ").ifBlank { "—" },
                                style = FilmTheme.type.rebate, color = colors.dim,
                            )
                        }
                    }
                }
            }

            MeterEyebrow("Metering")
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Constants.METERING_TYPES.forEach { m ->
                    MeterSegment(
                        label = m,
                        selected = metering == m,
                        accent = colors.cyan,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.saveMeterMetering(m) },
                    )
                }
            }

            MeterEyebrow("Hold")
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MeterSegment(
                    label = if (evLocked) "EV HELD" else "HOLD EV",
                    selected = evLocked,
                    accent = colors.yellow,
                    modifier = Modifier.weight(2f),
                    onClick = { vm.setMeterLock(!evLocked, evAtLock = baseEV) },
                )
                MeterSegment(
                    label = if (cameraOn) "CAMERA OFF" else "CAMERA ON",
                    selected = false,
                    accent = colors.cyan,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (cameraOn) {
                            providerRef?.unbindAll(); providerRef = null
                            cameraCtrl = null; cameraInfoObj = null
                            appliedRegionKey = null; aeRegionsSupported = null
                            cameraOn = false
                            vm.clearMeterReading(); vm.setMeterLock(false)
                        } else if (hasCamPerm) {
                            cameraOn = true; vm.setMeterLock(false)
                        } else onRequestPerm()
                    },
                )
            }

            // ── Black & white preview ─────────────────────────────────────────
            if (cameraOn && hasCamPerm) {
                MeterEyebrow(if (filmMono) "Preview · black & white roll" else "Preview")
                Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MeterSegment("COLOUR", !mono, colors.cyan, Modifier.weight(1f)) {
                        vm.setMeterMono(roll?.id, on = false, filmDefault = filmMono)
                    }
                    MeterSegment("B&W", mono, colors.cyan, Modifier.weight(1f)) {
                        vm.setMeterMono(roll?.id, on = true, filmDefault = filmMono)
                    }
                }
                if (mono) {
                    Spacer(Modifier.height(10.dp))
                    CalibrationRuler(
                        value = contrastDraft,
                        range = Constants.MONO_CONTRAST_RANGE,
                        accent = colors.halide,
                        label = "CONTRAST %.1f×".format(contrastDraft / 10f),
                        caption = "DRAG FOR SOFTER OR HARDER GREYS",
                        onValue = { contrastDraft = it },
                        onCommit = { vm.saveMeterMonoContrast(contrastDraft) },
                    )
                }
            }

            // Manual EV when there is nothing live to read
            if (!cameraOn || liveReading == null) {
                MeterEyebrow("Manual EV")
                CalibrationRuler(
                    value = ((manualEV - 10.0) * 3).roundToInt(),
                    range = -36..36,
                    accent = colors.yellow,
                    label = "%.1f EV".format(manualEV),
                    caption = "DRAG TO SET THE SCENE BY EYE",
                    onValue = { vm.setMeterManualEv(10.0 + it / 3.0) },
                    onCommit = {},
                )
            } else {
                MeterEyebrow("Calibration offset")
                CalibrationRuler(
                    value = calibDraft,
                    range = -15..15,
                    accent = colors.yellow,
                    label = "${Constants.formatThirds(calibDraft)} EV",
                    caption = "DRAG TO MATCH YOUR HANDHELD METER",
                    onValue = { calibDraft = it },
                    onCommit = { vm.saveMeterCalibThirds(calibDraft) },
                )
            }

            // ── Film speed ────────────────────────────────────────────────────
            MeterEyebrow(if (rollIso != null) "Film speed · from the roll" else "Film speed")
            if (rollIso != null) {
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilmChip("ISO $rollIso", color = colors.cyan, filled = true)
                    if (roll?.pushIso?.toIntOrNull()?.let { it > 0 && it != film?.iso } == true) {
                        val stops = log2(rollIso.toDouble() / (film?.iso ?: rollIso))
                        FilmChip(
                            "${if (stops > 0) "PUSH" else "PULL"} ${formatStops(stops)}",
                            color = colors.magenta,
                        )
                    }
                    FilmChip(film?.name.orEmpty().ifBlank { "UNNAMED STOCK" })
                }
            } else {
                Row(Modifier.padding(horizontal = 14.dp)) {
                    VaultDropdown("ISO", meterIso.toString(), Constants.ISOS.map { it.toString() },
                        { vm.saveMeterIso(it.toIntOrNull() ?: 400) })
                }
            }

            // ── Focal presets ─────────────────────────────────────────────────
            if (zoomLevels.isNotEmpty()) {
                MeterEyebrow("Focal length")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(zoomLevels, key = { it.id }) { z ->
                        val sel = activeZoom?.id == z.id
                        Column(
                            Modifier
                                .background(colors.film)
                                .border(1.dp, if (sel) colors.violet else colors.edge)
                                .clickable { activeZoom = if (sel) null else z }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(z.label, style = FilmTheme.type.data,
                                color = if (sel) colors.violet else colors.halide)
                            Text("${z.mm} MM", style = FilmTheme.type.rebate, color = colors.dim)
                        }
                    }
                    item {
                        Column(
                            Modifier
                                .background(colors.film)
                                .border(1.dp, colors.edge)
                                .clickable { showZoomEdit = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("⌁", style = FilmTheme.type.data, color = colors.halide)
                            Text("EDIT", style = FilmTheme.type.rebate, color = colors.dim)
                        }
                    }
                }
            }

            // ── Frame ─────────────────────────────────────────────────────────
            if (cameraOn && hasCamPerm) {
                MeterEyebrow("Frame")
                val frameOptions = remember {
                    listOf(Constants.METER_FRAME_AUTO, Constants.METER_FRAME_FILL) +
                        Constants.METER_FRAMES.keys
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(frameOptions, key = { it }) { f ->
                        val auto = f == Constants.METER_FRAME_AUTO
                        MeterSegment(
                            label = if (auto) "AUTO ${autoFrame ?: Constants.METER_FRAME_FILL}" else f,
                            selected = frameChoice == f,
                            accent = colors.cyan,
                            modifier = Modifier.width(if (auto) 96.dp else 58.dp),
                            onClick = { vm.saveMeterFrame(f) },
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (frameTurns) {
                        MeterSegment("HORIZONTAL", !frameVertical, colors.cyan, Modifier.weight(1f)) {
                            vm.saveMeterFrameVertical(false)
                        }
                        MeterSegment("VERTICAL", frameVertical, colors.cyan, Modifier.weight(1f)) {
                            vm.saveMeterFrameVertical(true)
                        }
                    }
                    MeterSegment("GRID", gridOn, colors.cyan, Modifier.width(80.dp)) {
                        vm.saveMeterGrid(!gridOn)
                    }
                }
                if (frameRatio != null) {
                    Text(
                        "CROPS THE PREVIEW · MATRIX STILL READS THE WHOLE SENSOR",
                        style = FilmTheme.type.rebate, color = colors.dim,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                    )
                }
            }

            // ── Zone placement (spot metering only) ───────────────────────────
            if (effectiveMetering == Constants.METERING_SPOT) {
                MeterEyebrow("Zone placement")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeterSegment(
                        label = if (zoneEnabled) "ON" else "OFF",
                        selected = zoneEnabled,
                        accent = colors.cyan,
                        modifier = Modifier.width(80.dp),
                        onClick = { vm.setMeterZoneEnabled(!zoneEnabled) },
                    )
                    if (zoneEnabled) {
                        Text(
                            Constants.ZONES.firstOrNull { it.first == zone }?.second ?: "",
                            style = FilmTheme.type.data, color = colors.dim,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (zoneEnabled) {
                    Spacer(Modifier.height(7.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(Constants.ZONES, key = { it.first }) { (z, _) ->
                            MeterSegment(
                                label = romanZone(z),
                                selected = zone == z,
                                accent = colors.cyan,
                                modifier = Modifier.width(52.dp),
                                onClick = { vm.setMeterZone(z) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MeterSegment("▼ SHADOW", false, colors.cyan, Modifier.weight(1f)) {
                            vm.markMeterShadow(baseEV)
                        }
                        MeterSegment("▲ HIGHLIGHT", false, colors.cyan, Modifier.weight(1f)) {
                            vm.markMeterHighlight(baseEV)
                        }
                        if (shadowEv != null || highlightEv != null) {
                            MeterSegment("✕", false, colors.mask, Modifier.width(44.dp)) {
                                vm.clearMeterMarks()
                            }
                        }
                    }
                    contrastLine(shadowEv, highlightEv)?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = FilmTheme.type.data, color = colors.dim,
                            modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }

            // ── Reciprocity failure (long exposures) ──────────────────────────
            if (displaySec >= 1.0) {
                MeterEyebrow("Reciprocity failure")
                val factor = Constants.RECIPROCITY.firstOrNull { it.first == recipFilm }?.second ?: 1.30
                val corrected = Exposure.reciprocityCorrect(displaySec, factor)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .background(colors.film)
                        .border(1.dp, colors.edge)
                        .padding(12.dp)
                ) {
                    Text(
                        "METERED ${GearClamp.formatSeconds(displaySec)}  ▸  EXPOSE ${GearClamp.formatSeconds(corrected)}",
                        style = FilmTheme.type.data, color = colors.yellow,
                    )
                    Spacer(Modifier.height(8.dp))
                    VaultDropdown("Film", recipFilm, Constants.RECIPROCITY.map { it.first },
                        { vm.saveRecipFilm(it) })
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // ── COMMIT BAR ────────────────────────────────────────────────────────
        CommitBar(
            roll = roll, film = film, camera = camera,
            shutter = activeShutter,
            aperture = apSnapped,
            apertureLabel = apertureLabel,
            iso = filmIso,
            ev = effectiveEV,
            blockedLabel = blockedLabel,
            beyondGear = beyondGear,
            offLabel = manualOff?.let { offLabel(it) },
            onLog = { sh, ap ->
                // The frame as shown — same crop as the viewfinder, not the full
                // sensor. Null when the camera is off or no frame has arrived yet.
                val photo = previewView
                    ?.takeIf { cameraOn && hasCamPerm && it.isAttachedToWindow }
                    ?.bitmap
                roll?.let {
                    vm.quickLogShot(it.id, shutter = sh, aperture = ap, iso = filmIso.toString(), photo = photo)
                } ?: photo?.recycle()
            },
            onUseInShot = onUseInShot,
        )
    }

    if (showZoomEdit) ZoomEditSheet(zoomLevels, vm) { showZoomEdit = false }
}

// ─── Status bar ───────────────────────────────────────────────────────────────

/**
 * What is loaded, which frame is next, and the way out.
 *
 * Tapping cycles rolls rather than opening a picker: more than two cameras
 * loaded at once is rare, and a dialog for a two-item list is a dialog too many.
 */
@Composable
private fun MeterStatusBar(
    roll: Roll?,
    film: FilmStock?,
    camera: Camera?,
    rollCount: Int,
    onCycleRoll: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = FilmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.void)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("METER", style = FilmTheme.type.eyebrow, color = colors.dim)
        Spacer(Modifier.width(12.dp))
        val label = if (roll == null) "NO ROLL LOADED" else buildString {
            append(film?.name.orEmpty().ifBlank { "UNNAMED" }.uppercase())
            append(" · FRAME ")
            append("%02d".format(nextFrame(roll)))
            camera?.name?.takeIf { it.isNotBlank() }?.let { append(" · ${it.uppercase()}") }
        }
        Text(
            label,
            style = FilmTheme.type.rebate,
            color = if (roll == null) colors.dim else colors.halide,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(if (rollCount > 1) Modifier.clickable(onClick = onCycleRoll) else Modifier),
        )
        if (rollCount > 1) {
            Text("↻", style = FilmTheme.type.data, color = colors.cyan,
                modifier = Modifier.clickable(onClick = onCycleRoll).padding(horizontal = 6.dp))
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            DyeIcon(FilmIcons.Close, "Close the meter", size = 18.dp, tint = colors.dim)
        }
    }
}

// ─── Clamp banner ─────────────────────────────────────────────────────────────

@Composable
private fun ClampBanner(advice: GearClamp.Advice) {
    val colors = FilmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(colors.void)
            .hazardHatch(colors.mask.copy(alpha = 0.12f), stripe = 5.dp)
            .border(1.dp, colors.mask)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DyeIcon(FilmIcons.Warn, null, size = 20.dp, tint = colors.mask)
        Column {
            Text(advice.headline.uppercase(), style = FilmTheme.type.rebate, color = colors.mask)
            Spacer(Modifier.height(3.dp))
            Text(
                advice.detail,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = colors.halide,
            )
        }
    }
}

// ─── Exposure ladder ──────────────────────────────────────────────────────────

/**
 * Every equivalent exposure for this EV, with the ones the gear cannot be set to
 * hatched out.
 *
 * The hatching is the point: a greyed rung reads as "not chosen", and the user
 * needs "not possible". Hatched rungs still take a tap — a third of a stop wider
 * than the lens is a frame a third under at full aperture, and whether that is
 * acceptable is for the person holding the camera to say. The commit bar marks
 * the frame as beyond the gear when one is chosen.
 */
@Composable
private fun ExposureLadder(
    rungs: List<GearClamp.Rung>,
    selectedShutter: String?,
    onSelect: (GearClamp.Rung) -> Unit,
) {
    val colors = FilmTheme.colors
    val state = rememberLazyListState()
    val selectedIndex = rungs.indexOfFirst { it.shutter == selectedShutter }
    // Keep the chosen rung on screen as the light changes and the ladder shifts
    LaunchedEffect(selectedIndex, rungs.size) {
        if (selectedIndex >= 0) state.animateScrollToItem(selectedIndex, -180)
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(rungs, key = { it.shutter }) { rung ->
            val sel = rung.shutter == selectedShutter
            val border = when {
                sel              -> colors.magenta
                !rung.achievable -> colors.dead
                else             -> colors.edge
            }
            val text = when {
                sel              -> colors.magenta
                !rung.achievable -> colors.dead
                else             -> colors.halide
            }
            Column(
                Modifier
                    .width(74.dp)
                    .clipToBounds()
                    .background(colors.film)
                    .then(
                        if (!rung.achievable) Modifier.hazardHatch(colors.filmRaised, stripe = 4.dp)
                        else Modifier
                    )
                    .border(1.dp, border)
                    .clickable { onSelect(rung) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    GearClamp.formatAperture(rung.aperture),
                    style = FilmTheme.type.data.copy(fontSize = 15.sp),
                    color = text, maxLines = 1, softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                Text(rung.shutter, style = FilmTheme.type.rebate, color = if (sel) text else colors.dim,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

/** "▨ = OUTSIDE ZENIT E (1/30–1/500) OR HELIOS 44-2 (F/2–F/16)" */
private fun hatchLegend(camera: Camera?, lens: Lens?): String {
    val parts = mutableListOf<String>()
    val body = camera?.name?.takeIf { it.isNotBlank() }
    val fast = camera?.fastestShutter.orEmpty()
    val slow = camera?.slowestShutter.orEmpty()
    if (body != null && (fast.isNotBlank() || slow.isNotBlank())) {
        parts += "${body.uppercase()} (${listOf(slow, fast).filter { it.isNotBlank() }.joinToString("–")})"
    }
    val glass = lens?.name?.takeIf { it.isNotBlank() }
    val wide = lens?.maxAperture.orEmpty()
    val narrow = lens?.minAperture.orEmpty()
    if (glass != null && (wide.isNotBlank() || narrow.isNotBlank())) {
        parts += "${glass.uppercase()} (F/${listOf(wide, narrow).filter { it.isNotBlank() }.joinToString("–F/")})"
    }
    return if (parts.isEmpty()) "" else "▨ = OUTSIDE " + parts.joinToString(" OR ")
}

// ─── Commit bar ───────────────────────────────────────────────────────────────

/**
 * Pinned to the bottom, terminating in a sprocket rail so the instrument reads as
 * a length of film rather than a form.
 *
 * With a roll loaded this logs the frame outright — the meter already knows the
 * exposure, the roll and the film, and making the user retype them in a sheet is
 * the reason people stop keeping shot notes. With no roll it hands the reading to
 * the shot sheet instead, which is where it used to go.
 */
@Composable
private fun CommitBar(
    roll: Roll?,
    film: FilmStock?,
    camera: Camera?,
    shutter: String,
    aperture: Double,
    /** How the aperture reads — a dash once the solution leaves the f-stop scale. */
    apertureLabel: String,
    iso: Int,
    ev: Double,
    /** Why the frame cannot be logged, shown as the button's title. Null = it can. */
    blockedLabel: String?,
    /** The chosen exposure is outside what the body or lens can be set to. */
    beyondGear: Boolean,
    /** How far a manual exposure is from the reading. Null outside manual. */
    offLabel: String?,
    onLog: (shutter: String, aperture: String) -> Unit,
    onUseInShot: ((String, String, String) -> Unit)?,
) {
    val colors = FilmTheme.colors
    val apNum = apertureNumString(aperture)
    val full = roll != null && nextFrame(roll) > roll.totalShots
    val enabled = blockedLabel == null && !full && (roll != null || onUseInShot != null)
    var logged by remember { mutableStateOf<String?>(null) }

    val title = when {
        full                 -> "ROLL FULL"
        blockedLabel != null -> blockedLabel
        roll != null         -> "LOG FRAME ${"%02d".format(nextFrame(roll))}"
        else                 -> "USE IN SHOT"
    }
    val subtitle = listOfNotNull(
        "BEYOND GEAR".takeIf { beyondGear && blockedLabel == null },
        offLabel,
        film?.name?.takeIf { it.isNotBlank() }?.uppercase(),
        camera?.name?.takeIf { it.isNotBlank() }?.uppercase(),
        apertureLabel,
        shutter,
        "ISO $iso",
        "EV ${"%.1f".format(ev)}",
    ).joinToString(" · ")

    Column(Modifier.fillMaxWidth().background(colors.void)) {
        // Film-on-void rather than void-on-void: the perforations are only
        // visible if the rail is a different colour from what shows through them.
        SprocketRail(filmColor = colors.film)
        logged?.let {
            Text(
                it,
                style = FilmTheme.type.data,
                color = colors.void,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                    .fillMaxWidth()
                    .background(colors.cyan)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
        Column(
            Modifier
                .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)
                .fillMaxWidth()
                .then(
                    if (enabled) Modifier.hardShadow(colors.violet, 4.dp, 4.dp) else Modifier
                )
                .background(if (enabled) colors.magenta else colors.filmRaised)
                .clickable(enabled = enabled) {
                    if (roll != null) {
                        onLog(shutter, apNum)
                        logged = "FRAME ${"%02d".format(nextFrame(roll))} LOGGED · " +
                            "$apertureLabel · $shutter"
                    } else {
                        onUseInShot?.invoke(shutter, apNum, iso.toString())
                    }
                }
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                title,
                style = FilmTheme.type.stock.copy(fontSize = 21.sp),
                color = if (enabled) colors.void else colors.dim,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = FilmTheme.type.rebate,
                color = if (enabled) colors.void.copy(alpha = 0.72f) else colors.dim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Priority toggle ──────────────────────────────────────────────────────────

/**
 * Which half of the exposure stays put while the light moves, with the value
 * being held written on the control itself. M holds both.
 *
 * Aperture priority opens wide by default; once a rung has stopped it down, a
 * small reset returns it to wide open rather than making the user hunt for the
 * widest rung on a ladder that keeps sliding under their thumb.
 */
@Composable
private fun PriorityToggle(
    priority: MeterPriority,
    heldAperture: Double,
    heldIsWideOpen: Boolean,
    canReopen: Boolean,
    heldShutter: String,
    onPriority: (MeterPriority) -> Unit,
    onWideOpen: () -> Unit,
) {
    val colors = FilmTheme.colors
    Row(
        Modifier.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MeterSegment(
            label = "A · ${GearClamp.formatAperture(heldAperture).uppercase()}" +
                if (heldIsWideOpen) " OPEN" else "",
            selected = priority == MeterPriority.APERTURE,
            accent = colors.cyan,
            modifier = Modifier.weight(1f),
            onClick = { onPriority(MeterPriority.APERTURE) },
        )
        MeterSegment(
            label = "S · $heldShutter",
            selected = priority == MeterPriority.SHUTTER,
            accent = colors.cyan,
            modifier = Modifier.weight(1f),
            onClick = { onPriority(MeterPriority.SHUTTER) },
        )
        MeterSegment(
            label = "M",
            selected = priority == MeterPriority.MANUAL,
            accent = colors.cyan,
            modifier = Modifier.width(52.dp),
            onClick = { onPriority(MeterPriority.MANUAL) },
        )
        if (priority == MeterPriority.APERTURE && canReopen) {
            MeterSegment("↺", false, colors.cyan, Modifier.width(44.dp), onClick = onWideOpen)
        }
    }
}

// ─── Manual mode ──────────────────────────────────────────────────────────────

/**
 * The match needle of a manual body: centred when the dialled exposure is what
 * the light asks for, pushed right when over, left when under.
 *
 * Three stops either way is the whole scale. Past that the nub pins to the end
 * and turns the warning colour; the number still says how far.
 */
@Composable
private fun MatchNeedle(stopsOff: Double) {
    val colors = FilmTheme.colors
    val label = offLabel(stopsOff)
    val onNeedle = abs(stopsOff) < NEEDLE_TOLERANCE
    val pinned = abs(stopsOff) > NEEDLE_RANGE
    val nub = when {
        onNeedle -> colors.cyan
        pinned   -> colors.mask
        else     -> colors.yellow
    }
    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("UNDER ◂  MATCH THE NEEDLE  ▸ OVER", style = FilmTheme.type.rebate, color = colors.dim)
            Text(label, style = FilmTheme.type.data, color = nub)
        }
        Spacer(Modifier.height(7.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clipToBounds()
                .background(colors.film)
                .border(1.dp, colors.edge)
                .semantics { contentDescription = "Exposure against the reading: $label" }
        ) {
            // A tick per third, a taller one per whole stop, the centre brightest.
            val thirds = (NEEDLE_RANGE * 3).toInt()
            for (i in -thirds..thirds) {
                val x = size.width * (0.5f + i / (2f * thirds))
                val whole = i % 3 == 0
                val top = if (whole) 0f else size.height * 0.55f
                drawLine(
                    if (i == 0) colors.dim else colors.dead,
                    Offset(x, top), Offset(x, size.height), 1.dp.toPx(),
                )
            }
            val frac = (0.5 + stopsOff.coerceIn(-NEEDLE_RANGE, NEEDLE_RANGE) / (2 * NEEDLE_RANGE)).toFloat()
            drawLine(nub, Offset(size.width * frac, 0f), Offset(size.width * frac, size.height), 3.dp.toPx())
        }
    }
}

/**
 * One of the two manual dials: a row of settings, the dialled one marked, the
 * ones the gear cannot be set to hatched as they are on the ladder — and, as
 * there, still tappable.
 */
@Composable
private fun <T> ManualDial(
    title: String,
    values: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    achievable: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    val colors = FilmTheme.colors
    val state = rememberLazyListState()
    val selectedIndex = values.indexOfFirst(isSelected)
    LaunchedEffect(selectedIndex, values.size) {
        if (selectedIndex >= 0) state.animateScrollToItem(selectedIndex, -180)
    }
    Text(
        title, style = FilmTheme.type.rebate, color = colors.dim,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 5.dp),
    )
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(values, key = { label(it) }) { v ->
            val sel = isSelected(v)
            val ok = achievable(v)
            val tint = when {
                sel -> colors.magenta
                !ok -> colors.dead
                else -> colors.halide
            }
            Box(
                Modifier
                    .width(64.dp)
                    .clipToBounds()
                    .background(colors.film)
                    .then(if (!ok) Modifier.hazardHatch(colors.filmRaised, stripe = 4.dp) else Modifier)
                    .border(1.dp, if (sel) colors.magenta else if (!ok) colors.dead else colors.edge)
                    .clickable { onSelect(v) }
                    .semantics { this.selected = sel }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(v), style = FilmTheme.type.data.copy(fontSize = 15.sp),
                    color = tint, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** "CORRECT", "+1.3 OVER", "-0.7 UNDER" — the needle as words. */
private fun offLabel(stopsOff: Double): String = when {
    abs(stopsOff) < NEEDLE_TOLERANCE -> "CORRECT"
    stopsOff > 0 -> "${formatStops(stopsOff)} OVER"
    else         -> "${formatStops(stopsOff)} UNDER"
}

/**
 * A sixth of a stop: inside it the needle reads as centred. It is the most a
 * third-stop dial can be off the reading while sitting on its nearest click.
 */
private const val NEEDLE_TOLERANCE = 1.0 / 6
/** Stops either side of centre the needle scale shows. */
private const val NEEDLE_RANGE = 3.0

/**
 * Held aperture when nothing says how wide the lens opens: a fixed-lens body
 * with no range entered, or no roll at all. f/8 because it is on every lens.
 */
private const val FALLBACK_HELD_APERTURE = 8.0

// ─── Small parts ──────────────────────────────────────────────────────────────

@Composable
private fun MeterEyebrow(title: String) {
    val colors = FilmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = FilmTheme.type.eyebrow, color = colors.dim)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = colors.edge)
    }
}

/**
 * A segmented control cell: border, no radius, mono caps.
 *
 * Selection is a glow, a colour change AND a solid bar along the bottom edge.
 * The bar is the one that matters: glow and hue are the same signal to anyone
 * who cannot separate cyan from grey, and a control whose only state cue is a
 * colour has no state cue at all for them.
 */
@Composable
private fun MeterSegment(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = FilmTheme.colors
    Column(
        modifier
            .then(
                if (selected) Modifier.halation(accent, 10.dp, 0.25f, !colors.safelight)
                else Modifier
            )
            .background(colors.film)
            .border(1.dp, if (selected) accent else colors.edge)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = Role.Tab },
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 4.dp),
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
 * A rebate ruler you scrub, rather than a Material slider.
 *
 * The thumb-and-track slider is the one control on this screen that would look
 * like every other app; a strip of ticks with a bright nub is what the exposure
 * compensation scale in a viewfinder actually looks like, and it drags at the
 * same resolution the value has.
 */
@Composable
private fun CalibrationRuler(
    value: Int,
    range: IntRange,
    accent: Color,
    label: String,
    caption: String,
    onValue: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    val colors = FilmTheme.colors
    val span = (range.last - range.first).toFloat()
    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(caption, style = FilmTheme.type.rebate, color = colors.dim)
            Text(label, style = FilmTheme.type.data, color = colors.halide)
        }
        Spacer(Modifier.height(7.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clipToBounds()
                .background(colors.film)
                .border(1.dp, colors.edge)
                // A Canvas driven by drag gestures is invisible to TalkBack and
                // impossible to operate with it. Publishing the range and a
                // setProgress action makes it a real control rather than a
                // picture of one.
                .semantics {
                    contentDescription = "$caption. $label"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.toFloat(),
                        range = range.first.toFloat()..range.last.toFloat(),
                        steps = (range.last - range.first - 1).coerceAtLeast(0),
                    )
                    setProgress { target ->
                        onValue(target.roundToInt().coerceIn(range.first, range.last))
                        onCommit()
                        true
                    }
                }
                .pointerInput(range) {
                    fun set(x: Float) {
                        val frac = (x / size.width).coerceIn(0f, 1f)
                        onValue((range.first + frac * span).roundToInt())
                    }
                    detectHorizontalDragGestures(
                        onDragEnd = { onCommit() },
                        onHorizontalDrag = { change, _ -> change.consume(); set(change.position.x) },
                    )
                }
                .pointerInput(range) {
                    detectTapGestures(onTap = { ofs ->
                        val frac = (ofs.x / size.width).coerceIn(0f, 1f)
                        onValue((range.first + frac * span).roundToInt())
                        onCommit()
                    })
                }
        ) {
            // Ticks every 12dp, a taller one at the centre — the film-rebate
            // pattern the sprocket rails already use elsewhere.
            val step = 12.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(colors.dead, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += step
            }
            drawLine(colors.dim, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
            val frac = ((value - range.first) / span).coerceIn(0f, 1f)
            drawLine(accent, Offset(size.width * frac, 0f), Offset(size.width * frac, size.height), 3.dp.toPx())
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Greys the preview through a hardware layer's colour filter, so the frames
 * never leave the GPU. [contrast] is a multiplier around mid-grey; null takes
 * the filter off. The applied value is kept in the view's tag: `update` runs
 * on every recomposition the preview reads, and rebuilding the layer each time
 * would be wasted work.
 */
private fun PreviewView.applyMonoFilter(contrast: Float?) {
    if (tag == contrast) return
    tag = contrast
    if (contrast == null) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(monoMatrix(contrast)) }
        setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
}

/**
 * Luma, then contrast stretched around mid-grey (128 of 255). Rec. 709
 * weights: green carries most of what reads as brightness, which is close
 * enough to an unfiltered panchromatic stock for judging a scene.
 */
private fun monoMatrix(contrast: Float): FloatArray {
    val r = 0.2126f * contrast
    val g = 0.7152f * contrast
    val b = 0.0722f * contrast
    val shift = 128f * (1f - contrast)
    return floatArrayOf(
        r, g, b, 0f, shift,
        r, g, b, 0f, shift,
        r, g, b, 0f, shift,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** The roll's working speed: a push overrides the box, blank or zero does not. */
private fun rollSpeed(roll: Roll?, film: FilmStock?): Int? {
    if (roll == null) return null
    roll.pushIso.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
    return film?.iso?.takeIf { it > 0 }
}

/**
 * "+1", "-1", "+1.3" — the same shape Home prints, so a roll's push reads
 * identically wherever you meet it. Rounding a two-thirds push to a whole stop
 * would print two thirds of a lie.
 */
private fun formatStops(stops: Double): String {
    val rounded = kotlin.math.round(stops)
    val sign = if (stops > 0) "+" else ""
    return sign + if (abs(stops - rounded) < 0.05) rounded.toLong().toString()
    else "%.1f".format(java.util.Locale.US, stops)
}

/** One past the last exposure — the frame the commit bar would write. */
private fun nextFrame(roll: Roll): Int = roll.shots.size + 1

/** Bare aperture number as stored on shots ("8", "5.6"). */
private fun apertureNumString(a: Double): String =
    if (a == a.toLong().toDouble()) a.toLong().toString() else a.toString()

private fun romanZone(zone: Int): String =
    listOf("0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(zone) { "$zone" }

/** Shadow/highlight marks reduced to a scene contrast range and a dev suggestion. */
private fun contrastLine(shadowEv: Double?, highlightEv: Double?): String? {
    if (shadowEv == null && highlightEv == null) return null
    return buildString {
        shadowEv?.let { append("▼ ${"%.1f".format(it)}") }
        if (shadowEv != null && highlightEv != null) append("   ")
        highlightEv?.let { append("▲ ${"%.1f".format(it)}") }
        if (shadowEv != null && highlightEv != null) {
            val range = highlightEv - shadowEv
            append("   Δ ${"%.1f".format(range)} STOPS · ")
            append(when {
                range <= 5.0 -> "FLAT — N+1 DEV?"
                range <= 7.0 -> "NORMAL — N DEV"
                else         -> "CONTRASTY — N−1 DEV"
            })
        }
    }
}

// ─── Canvas metering overlay ──────────────────────────────────────────────────

private fun DrawScope.drawMeteringOverlay(
    metering: String,
    reticle: Offset,
    reticleColor: Color,
    gridColor: Color,
    grid: Boolean,
    active: Boolean,
) {
    val w = size.width
    val h = size.height
    val cx = w * reticle.x
    val cy = h * reticle.y
    val stroke = Stroke(1.5.dp.toPx())
    val colour = if (active) reticleColor else reticleColor.copy(alpha = 0.45f)

    // Thirds grid under every mode — it is a viewfinder, not just a meter —
    // unless switched off for a clean look at the frame
    if (grid) {
        for (c in 1..2) drawLine(gridColor, Offset(w * c / 3f, 0f), Offset(w * c / 3f, h), 1.dp.toPx())
        for (r in 1..2) drawLine(gridColor, Offset(0f, h * r / 3f), Offset(w, h * r / 3f), 1.dp.toPx())
    }

    when (metering) {
        Constants.METERING_SPOT -> {
            val r = 30.dp.toPx()
            drawRect(colour, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = stroke)
            // Crosshairs run past the box so the exact point is unambiguous
            val over = 9.dp.toPx()
            drawLine(colour, Offset(cx, cy - r - over), Offset(cx, cy + r + over), stroke.width)
            drawLine(colour, Offset(cx - r - over, cy), Offset(cx + r + over, cy), stroke.width)
        }
        Constants.METERING_CENTRE -> {
            val r = minOf(w, h) / 3.2f
            drawCircle(colour, r, Offset(cx, cy), style = stroke)
            val m = 20.dp.toPx(); val p = 10.dp.toPx()
            listOf(
                Offset(p, p) to Offset(p + m, p), Offset(p, p) to Offset(p, p + m),
                Offset(w - p, p) to Offset(w - p - m, p), Offset(w - p, p) to Offset(w - p, p + m),
                Offset(p, h - p) to Offset(p + m, h - p), Offset(p, h - p) to Offset(p, h - p - m),
                Offset(w - p, h - p) to Offset(w - p - m, h - p), Offset(w - p, h - p) to Offset(w - p, h - p - m),
            ).forEach { (a, b) -> drawLine(colour, a, b, stroke.width) }
        }
        Constants.METERING_HIGHLIGHT -> {
            drawRect(colour, Offset(0f, 0f), Size(w, h / 4f), style = stroke)
        }
        else -> { /* Matrix — the thirds grid above, if on, is the whole overlay */ }
    }
}

// ─── Zoom Edit Sheet ──────────────────────────────────────────────────────────

@Composable
fun ZoomEditSheet(zoomLevels: List<ZoomLevel>, vm: MainViewModel, onDismiss: () -> Unit) {
    val colors = FilmTheme.colors
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
                    Text("${z.label}  ${z.mm}mm", color = colors.halide, fontSize = 13.sp)
                    Row {
                        IconButton(onClick = { editingId = z.id; editLabel = z.label; editMm = z.mm.toString() },
                            Modifier.size(28.dp)) {
                            DyeIcon(FilmIcons.Edit, null, size = 14.dp, tint = colors.dim)
                        }
                        IconButton(onClick = { vm.deleteZoomLevel(z) }, Modifier.size(28.dp)) {
                            DyeIcon(FilmIcons.Trash, null, size = 14.dp, tint = colors.mask.copy(alpha = 0.55f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = colors.edge)
        Spacer(Modifier.height(10.dp))
        Text("Add zoom level", color = colors.dim, fontSize = 12.sp)
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

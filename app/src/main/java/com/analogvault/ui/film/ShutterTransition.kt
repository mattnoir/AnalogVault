package com.analogvault.ui.film

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * An iris closing over the whole app, and opening on the meter.
 *
 * The shutter is the one destination that is not a tab — it is the instrument
 * the bar is built around — so it is the one that arrives differently. Every
 * other screen slides a sixth of its width and fades; this one shuts a
 * diaphragm over the screen, swaps underneath while nothing is visible, and
 * opens on what you asked for.
 *
 * Ported from the View implementation in the plan. An overlay rather than a
 * per-screen transition for the reason given there: a shutter has to cover the
 * outgoing screen while it closes and the incoming one while it opens, and
 * per-screen animations can only ever draw half of it twice.
 */

// ─── Config ───────────────────────────────────────────────────────────────────

/** Tunable in the mockup at claudeFiles/ANALOGVAULT/AV-shutter-animation. */
data class ShutterConfig(
    /** 5–12 reads as a real diaphragm. */
    val blades: Int = 8,
    val closeMs: Int = 260,
    /** Held shut. The swap happens on the first frame of this phase. */
    val holdMs: Int = 80,
    val openMs: Int = 340,
    /** Blades rotate this far while closing — the tell that it is an iris. */
    val twistDegrees: Float = 26f,
    /** 0..1 rim intensity. */
    val glow: Float = 0.85f,
    /** 0..1 radial split between the two rim colours. */
    val chromatic: Float = 0.60f,
    val seams: Boolean = true,
    val flash: Boolean = true,
    /**
     * How long the exposure flash lasts.
     *
     * Kept at or under [holdMs] on purpose: a flash still fading while the
     * blades part is not a flash, it is a veil over the opening. The first cut
     * ran for holdMs + 140 and read as a grey screen between the two halves of
     * the transition rather than as a click of light.
     */
    val flashMs: Int = 90,
    val haptics: Boolean = true,
    /** Pure black, which costs nothing on an OLED panel. */
    val bladeColor: Color = Color.Black,
    val rimCyan: Color,
    val rimMagenta: Color,
    val rimCore: Color,
    val flashColor: Color,
)

/**
 * The scheme's own colours, and the scheme's own restraint.
 *
 * Safelight drops the flash and most of the glow. The mode exists to protect
 * dark adaptation, and a full-screen flash is the single brightest thing the app
 * could do; the blades themselves are black on black and cost nothing, so the
 * motion stays.
 */
@Composable
fun rememberShutterConfig(
    blades: Int = 8,
    /**
     * Off by default.
     *
     * The plan's expose phase washes the whole screen while the blades are shut,
     * and with nothing else on screen at that moment a wash is all you see: it
     * reads as a grey plate dropped between the closing and the opening rather
     * than as a click of light. The haptic already marks the exposure, and it
     * marks it in the sense that is actually free at that instant. Turn it back
     * on here if a future screen gives the flash something to light.
     */
    flash: Boolean = false,
): ShutterConfig {
    val colors = FilmTheme.colors
    return remember(colors, blades, flash) {
        ShutterConfig(
            blades = blades,
            glow = if (colors.safelight) 0.35f else 0.85f,
            flash = flash && !colors.safelight,
            rimCyan = colors.cyan,
            rimMagenta = colors.magenta,
            rimCore = if (colors.safelight) colors.halide else colors.yellow,
            // Not a palette colour: this is light coming through a lens, and
            // the palette's near-white is a cool silver that washes the black
            // blades grey rather than reading as an exposure.
            flashColor = Color(0xFFFFF6D8),
        )
    }
}

// ─── Geometry ─────────────────────────────────────────────────────────────────

/**
 * Each blade is a disc of radius [rho] whose centre sits at distance [d] from
 * the middle of the screen. The aperture is everything outside every disc, so
 * its edge is N circular arcs — the rounded polygon a real diaphragm makes.
 *
 *   shut   d == rho     (every arc meets at the centre)
 *   open   d == dMax    (the aperture clears the screen diagonal)
 *
 * Vertex radius, where discs k and k+1 cross:
 *   rv = d·cos(π/N) − √(rho² − (d·sin(π/N))²)
 *
 * Pure arithmetic, no allocation, so it is safe to run every frame.
 */
internal class ShutterGeometry {
    var cx = 0f; var cy = 0f
    var d = 0f; var rho = 0f; var rv = 0f; var dMax = 0f; var rOut = 0f
    var twist = 0f; var half = 0f; var blades = 8

    /** @param progress 0 = fully open, 1 = fully shut. */
    fun update(w: Float, h: Float, blades: Int, progress: Float, twistDegrees: Float) {
        this.blades = blades.coerceIn(3, 16)
        cx = w / 2f; cy = h / 2f
        half = (Math.PI / this.blades).toFloat()
        val halfDiag = hypot(w, h) / 2f
        // 1.12 keeps neighbouring blades overlapping at every progress value, so
        // no light leaks between them; the 0.40 floor stops high blade counts
        // from producing a razor-thin edge. 1.02 is anti-alias headroom.
        val factor = max(sin(half) * 1.12f, 0.40f)
        dMax = halfDiag / (1f - factor) * 1.02f
        rho = dMax * factor
        d = dMax - progress * (dMax - rho)
        rOut = dMax * 1.8f
        twist = Math.toRadians((progress * twistDegrees).toDouble()).toFloat()

        val s = d * sin(half)
        val inner = rho * rho - s * s
        rv = if (inner <= 0f) 0f else d * cos(half) - sqrt(inner)
    }

    val isShut: Boolean get() = rv <= 0.5f
}

// ─── Controller ───────────────────────────────────────────────────────────────

/**
 * Drives the overlay and owns the swap.
 *
 * [play] closes, runs [onSwap] while shut, and opens again. The swap is a state
 * change rather than a fragment transaction, so instead of waiting on a pre-draw
 * listener it waits two frames — long enough for the new screen to have been
 * composed and measured before any of it is uncovered.
 */
class ShutterController internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val animationsEnabled: Boolean,
) {
    internal val progress = Animatable(0f)
    internal var flash by mutableFloatStateOf(0f)
        private set

    /** True for the whole close-swap-open cycle. */
    var running by mutableStateOf(false)
        private set

    fun play(config: ShutterConfig, onSwap: () -> Unit) {
        // Animations off in developer or accessibility settings: no overlay at
        // all. This is also what makes an instrumented test deterministic.
        if (!animationsEnabled) { onSwap(); return }
        // A second tap mid-transition is ignored rather than stacking a second
        // shutter on top of the first.
        if (running) return

        running = true
        scope.launch {
            try {
                progress.animateTo(1f, tween(config.closeMs, easing = AccelerateEasing))
                if (config.haptics) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onSwap()
                if (config.flash) launch { runFlash(config) }
                // Two frames: one for the swap to recompose, one for it to be
                // laid out. Opening onto a screen that has not measured yet is
                // the one thing this transition cannot hide.
                withFrameNanos { }
                withFrameNanos { }
                delay(config.holdMs.toLong())
                progress.animateTo(0f, tween(config.openMs, easing = DecelerateEasing))
            } finally {
                // Cancelled by a rotation, a backgrounding, or the composition
                // going away: the swap already happened, so all that is left is
                // to make sure the screen is not left behind a black disc.
                progress.snapTo(0f)
                flash = 0f
                running = false
            }
        }
    }

    private suspend fun runFlash(config: ShutterConfig) {
        val f = Animatable(1f)
        // Bright immediately, then most of the way gone within a couple of
        // frames — the shape of a light that was let in and shut off, not a
        // fade.
        f.animateTo(0f, tween(config.flashMs, easing = FlashDecay)) { flash = value }
        flash = 0f
    }
}

/** Remembers a controller bound to this composition. */
@Composable
fun rememberShutterController(): ShutterController {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val enabled = remember(context) {
        val cr = context.contentResolver
        val animator = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        animator != 0f && transition != 0f
    }
    return remember(scope, haptics, enabled) { ShutterController(scope, haptics, enabled) }
}

/** AccelerateInterpolator(0.9f) is x^1.8; DecelerateInterpolator(0.85f) is 1−(1−x)^1.7. */
private val AccelerateEasing = Easing { it.coerceIn(0f, 1f).pow(1.8f) }
private val DecelerateEasing = Easing { 1f - (1f - it.coerceIn(0f, 1f)).pow(1.7f) }

/** Most of the drop in the first quarter of the duration. */
private val FlashDecay = Easing { sqrt(it.coerceIn(0f, 1f)) }

// ─── Overlay ──────────────────────────────────────────────────────────────────

/**
 * Draw this last, over everything including the bars.
 *
 * At rest it composes nothing at all, so an idle app pays for none of it.
 */
@Composable
fun ShutterOverlay(
    controller: ShutterController,
    config: ShutterConfig,
    modifier: Modifier = Modifier,
) {
    val progress = controller.progress.value
    val flash = controller.flash
    if (progress <= 0.0005f && flash <= 0.001f) return

    val geo = remember { ShutterGeometry() }
    val aperture = remember { Path() }
    val mask = remember { Path().apply { fillType = PathFillType.EvenOdd } }

    Canvas(
        modifier
            .fillMaxSize()
            // Swallows every touch for the length of the transition, so a tap
            // aimed at the outgoing screen cannot land on the incoming one.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            // Nothing here for TalkBack to read: it announces the screen the
            // shutter is opening onto, not the shutter.
            .clearAndSetSemantics { },
    ) {
        drawShutter(geo, aperture, mask, progress, flash, config)
    }
}

private fun DrawScope.drawShutter(
    geo: ShutterGeometry,
    aperture: Path,
    mask: Path,
    progress: Float,
    flash: Float,
    config: ShutterConfig,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    geo.update(w, h, config.blades, progress, config.twistDegrees)
    buildAperture(geo, aperture)

    // 1 — the blades. One even-odd path: the screen with the aperture punched
    // out of it, which is the whole diaphragm in a single fill.
    mask.reset()
    mask.fillType = PathFillType.EvenOdd
    mask.addRect(Rect(0f, 0f, w, h))
    if (!geo.isShut) mask.addPath(aperture)
    drawPath(mask, config.bladeColor)

    // 2 — seams radiating from each blade vertex.
    if (config.seams && !geo.isShut) {
        val step = (2.0 * Math.PI / geo.blades).toFloat()
        val lean = 0.16f + geo.twist * 0.5f
        for (k in 0 until geo.blades) {
            val b = geo.twist + (k + 0.5f) * step - HALF_PI
            drawLine(
                color = config.rimCyan.copy(alpha = 0.13f),
                start = Offset(geo.cx + geo.rv * cos(b), geo.cy + geo.rv * sin(b)),
                end = Offset(geo.cx + geo.rOut * cos(b + lean), geo.cy + geo.rOut * sin(b + lean)),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }

    // 3 — the rim: the two dyes split radially, warm core on top.
    if (!geo.isShut && config.glow > 0f) {
        val e = config.chromatic * 0.011f * (0.35f + 0.65f * progress)
        rim(aperture, geo, config.rimMagenta, 3.dp.toPx(), 0.55f * config.glow, 1f + e, 9.dp.toPx())
        rim(aperture, geo, config.rimCyan, 3.dp.toPx(), 0.55f * config.glow, 1f - e, 9.dp.toPx())
        rim(aperture, geo, config.rimCore, 1.2f.dp.toPx(), 0.95f, 1f, 3.dp.toPx())
    }

    // 4 — the exposure.
    if (flash > 0.001f) {
        drawRect(config.flashColor.copy(alpha = flash * 0.30f), Offset.Zero, Size(w, h))
    }
}

/**
 * A blur would be the obvious way to draw the glow and the wrong one:
 * BlurMaskFilter is unreliable on a hardware canvas and RenderEffect is API 31,
 * so this is three stacked strokes — cheap, and identical on every device.
 */
private fun DrawScope.rim(
    aperture: Path,
    geo: ShutterGeometry,
    color: Color,
    width: Float,
    alpha: Float,
    scaleBy: Float,
    spread: Float,
) {
    scale(scaleBy, Offset(geo.cx, geo.cy)) {
        drawPath(aperture, color.copy(alpha = alpha * 0.18f), style = Stroke(width + spread * 2f))
        drawPath(aperture, color.copy(alpha = alpha * 0.40f), style = Stroke(width + spread))
        drawPath(aperture, color.copy(alpha = alpha), style = Stroke(width))
    }
}

private fun buildAperture(geo: ShutterGeometry, aperture: Path) {
    aperture.reset()
    if (geo.isShut) return
    val step = (2.0 * Math.PI / geo.blades).toFloat()
    for (k in 0 until geo.blades) {
        val ca = geo.twist + k * step - HALF_PI
        val bx = geo.cx + geo.d * cos(ca)
        val by = geo.cy + geo.d * sin(ca)
        val b0 = ca - step / 2f
        val b1 = ca + step / 2f
        val w0x = geo.cx + geo.rv * cos(b0); val w0y = geo.cy + geo.rv * sin(b0)
        val w1x = geo.cx + geo.rv * cos(b1); val w1y = geo.cy + geo.rv * sin(b1)
        val a0 = Math.toDegrees(atan2((w0y - by).toDouble(), (w0x - bx).toDouble())).toFloat()
        val a1 = Math.toDegrees(atan2((w1y - by).toDouble(), (w1x - bx).toDouble())).toFloat()
        var sweep = a1 - a0
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f
        aperture.arcTo(
            rect = Rect(bx - geo.rho, by - geo.rho, bx + geo.rho, by + geo.rho),
            startAngleDegrees = a0,
            sweepAngleDegrees = sweep,
            forceMoveTo = k == 0,
        )
    }
    aperture.close()
}

private const val HALF_PI = (Math.PI / 2.0).toFloat()

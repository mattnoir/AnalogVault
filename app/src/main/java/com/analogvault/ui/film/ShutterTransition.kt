package com.analogvault.ui.film

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmColors
import com.analogvault.ui.theme.FilmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/* ------------------------------------------------------------------------
 * IRIS SHUTTER — the transition the LOG button earns
 *
 * An N-blade diaphragm closes over the whole window, the screen underneath is
 * swapped while nothing is visible, then it opens again. 680 ms end to end.
 *
 * It is an overlay rather than a screen animation because a shutter has to
 * cover BOTH screens at once — the outgoing one while it shuts and the
 * incoming one while it opens. AnimatedContent animates each side separately,
 * so matching them would mean drawing half a diaphragm twice and keeping the
 * two halves in phase. One overlay above the Scaffold sidesteps all of it, and
 * gets three things for free: it swallows touches for the whole transition, it
 * is oblivious to what is being swapped underneath, and at progress 0 it draws
 * literally nothing, so it costs nothing when idle.
 *
 * This is the ONE destination that gets it. Every other tab change stays on
 * the 180 ms slide in MainActivity — the shutter is 680 ms, which is a long
 * time to wait for a screen you did not choose deliberately. Spending it here
 * is the point; spending it everywhere would make the app feel slow.
 * ---------------------------------------------------------------------- */

@Immutable
data class ShutterConfig(
    /** 5–12 reads as a real diaphragm. 8 is the safe default. */
    val blades: Int = 8,
    val closeMs: Int = 260,
    /** Held fully shut. The screen swap happens on the first frame of this. */
    val holdMs: Int = 80,
    val openMs: Int = 340,
    /** Blades rotate this far while closing — the give-away that it is an iris. */
    val twistDegrees: Float = 26f,
    /** 0..1 rim intensity. */
    val glow: Float = 0.85f,
    /** 0..1 radial chromatic split on the rim — the dye layers separating. */
    val chromatic: Float = 0.60f,
    val seams: Boolean = true,
    val flash: Boolean = true,
    val haptics: Boolean = true,
    /**
     * Pure black — the same void every other surface sits on, so the shut
     * blades read as the panel switching off rather than as a grey card. The
     * root film grain still lands on top of them, exactly as it does on every
     * other black in the app, which is what keeps the closed frame looking
     * like film and not like a dead pixel.
     */
    val bladeColor: Color = Color.Black,
    val rimCyan: Color = Color(0xFF00F0FF),
    val rimMagenta: Color = Color(0xFFFF1E7A),
    val rimCore: Color = Color(0xFFFFE01B),
    val flashColor: Color = Color(0xFFFFF6D8),
)

/**
 * The config wired to the current scheme, which is the only correct way to
 * build one inside the app.
 *
 * Under safelight the flash is off and the glow is cut: the exposure flash is a
 * near-white full-screen fill, and firing that at someone standing over a
 * developing tank undoes the dark adaptation the whole scheme exists to
 * protect. The blades still close — the motion is not the problem, the light is.
 */
@Composable
fun rememberShutterConfig(blades: Int = 8): ShutterConfig {
    val colors: FilmColors = FilmTheme.colors
    return remember(colors, blades) {
        ShutterConfig(
            blades = blades,
            rimCyan = colors.cyan,
            rimMagenta = colors.magenta,
            rimCore = colors.yellow,
            glow = if (colors.safelight) 0.35f else 0.85f,
            chromatic = if (colors.safelight) 0f else 0.60f,
            flash = !colors.safelight,
        )
    }
}

// AccelerateInterpolator(0.9f) and DecelerateInterpolator(0.85f), which are
// pow(t, 2 * factor) and 1 - pow(1 - t, 2 * factor). The blades snap shut the
// way a leaf shutter does and ease open the way one does not — the asymmetry is
// what makes it read as mechanical rather than as a mask fading in and out.
private val CloseEasing = Easing { t -> t.pow(1.8f) }
private val OpenEasing = Easing { t -> 1f - (1f - t).pow(1.7f) }

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

@Stable
class ShutterState internal constructor(private val scope: CoroutineScope) {

    // Plain fields, not snapshot state: they are pushed in from composition on
    // every pass and read from the draw phase, where a snapshot write would be
    // a backwards write into a value composition already read this frame. The
    // overlay redraws every frame it is visible anyway, so a plain read is
    // current by the next blade position.
    internal var config: ShutterConfig = ShutterConfig()
    internal var reduceMotion: Boolean = false
    internal var onShut: () -> Unit = {}

    /**
     * The config the in-flight transition is drawing with — fixed at [fire] so
     * that a scheme change (safelight) cannot recolour the blades or retime the
     * phases halfway through a run.
     */
    internal var runConfig: ShutterConfig = config
        private set

    /** 0 = fully open (nothing drawn), 1 = fully shut. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** 0..1 exposure flash over the closed blades. */
    var flash by mutableFloatStateOf(0f)
        private set

    /** True from the moment [fire] is accepted until the blades are open again. */
    var isRunning by mutableStateOf(false)
        private set

    /**
     * Shuts the blades, runs [swap] while nothing is visible, opens again.
     *
     * [swap] is guaranteed to run exactly once even if the transition is torn
     * down half way — you can never end up with a black screen and no
     * navigation, which is the one failure mode that would be unrecoverable.
     *
     * A second call while one is in flight is ignored rather than queued: the
     * LOG button is the largest target in the app and gets double-tapped.
     */
    fun fire(swap: () -> Unit) {
        // Animations off system-wide: navigate, draw nothing. This also keeps
        // instrumented tests deterministic, since they run with scales at 0.
        if (reduceMotion) {
            swap()
            return
        }
        if (isRunning) return
        isRunning = true
        runConfig = config
        val config = runConfig
        scope.launch {
            var swapped = false
            try {
                animate(0f, 1f, animationSpec = tween(config.closeMs, easing = CloseEasing)) { v, _ ->
                    progress = v
                }
                progress = 1f
                onShut()
                swapped = true
                swap()
                if (config.flash) {
                    launch {
                        animate(1f, 0f, animationSpec = tween(config.holdMs + 140)) { v, _ -> flash = v }
                    }
                }
                // Two frames, not one: the first lets the composition holding the
                // incoming screen be applied, the second lets it actually draw.
                // Without the gate a slow screen opens onto an empty container —
                // the same reason the View version waits on a pre-draw listener.
                withFrameNanos {}
                withFrameNanos {}
                delay(config.holdMs.toLong())
                animate(1f, 0f, animationSpec = tween(config.openMs, easing = OpenEasing)) { v, _ ->
                    progress = v
                }
            } finally {
                // Cancelled mid-close (the host left composition) lands here with
                // the swap still owed.
                if (!swapped) swap()
                progress = 0f
                flash = 0f
                isRunning = false
            }
        }
    }
}

@Composable
fun rememberShutterState(config: ShutterConfig = rememberShutterConfig()): ShutterState {
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val view = LocalView.current
    val state = remember(scope) { ShutterState(scope) }
    // Pushed into the existing state rather than keying a new one. Keying would
    // hand the overlay a fresh, idle state the moment the scheme changed, and a
    // transition already in flight would lose its blades mid-close.
    SideEffect {
        state.config = config
        state.reduceMotion = reduceMotion
        state.onShut = {
            // Not LocalHapticFeedback: this Compose version only exposes
            // LongPress and TextHandleMove, and neither is the short dry click a
            // shutter makes. KEYBOARD_TAP is, and LongPress is already spoken
            // for by the safelight gesture on the same button.
            if (config.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    return state
}

// ---------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------

/**
 * Each blade is a disc of radius [rho] centred at distance [d] from the middle
 * of the screen. The aperture is everything outside all N discs, so its edge is
 * N circular arcs — the rounded polygon a real diaphragm makes, not a polygon
 * with straight sides.
 *
 *     closed   d == rho    (all arcs meet at the centre)
 *     open     d == dMax   (aperture inradius clears the screen diagonal)
 *
 * Vertex radius, where discs k and k+1 cross:
 *
 *     rv = d·cos(π/N) − √(rho² − (d·sin(π/N))²)
 */
internal class ShutterGeometry {
    var cx = 0f
    var cy = 0f
    var d = 0f
    var rho = 0f
    var rv = 0f
    var rOut = 0f
    var twist = 0f
    var blades = 8

    /** @param progress 0 = fully open, 1 = fully shut. */
    fun update(w: Float, h: Float, blades: Int, progress: Float, twistDegrees: Float) {
        this.blades = blades.coerceIn(3, 16)
        cx = w / 2f
        cy = h / 2f
        val half = (PI / this.blades).toFloat()
        val halfDiag = hypot(w, h) / 2f
        // 1.12 keeps neighbouring blades overlapping at every progress value, so
        // no light leaks between them; the 0.40 floor stops a 12-blade config
        // from producing a hairline blade edge; 1.02 is anti-alias headroom.
        val factor = max(sin(half) * 1.12f, 0.40f)
        val dMax = halfDiag / (1f - factor) * 1.02f
        rho = dMax * factor
        d = dMax - progress * (dMax - rho)
        rOut = dMax * 1.8f
        twist = (progress * twistDegrees * PI / 180.0).toFloat()

        val s = d * sin(half)
        val inner = rho * rho - s * s
        rv = if (inner <= 0f) 0f else d * cos(half) - sqrt(inner)
    }

    val isShut: Boolean get() = rv <= 0.5f

    /** Angular step between blades. */
    val step: Float get() = (2.0 * PI / blades).toFloat()

    fun buildAperture(path: Path) {
        if (isShut) return
        val arc = step
        for (k in 0 until blades) {
            val ca = twist + k * arc - HALF_PI
            val bx = cx + d * cos(ca)
            val by = cy + d * sin(ca)
            val b0 = ca - arc / 2f
            val b1 = ca + arc / 2f
            val w0x = cx + rv * cos(b0); val w0y = cy + rv * sin(b0)
            val w1x = cx + rv * cos(b1); val w1y = cy + rv * sin(b1)
            val a0 = Math.toDegrees(atan2((w0y - by).toDouble(), (w0x - bx).toDouble())).toFloat()
            val a1 = Math.toDegrees(atan2((w1y - by).toDouble(), (w1x - bx).toDouble())).toFloat()
            var sweep = a1 - a0
            while (sweep > 180f) sweep -= 360f
            while (sweep < -180f) sweep += 360f
            path.arcTo(Rect(bx - rho, by - rho, bx + rho, by + rho), a0, sweep, k == 0)
        }
        path.close()
    }
}

// ---------------------------------------------------------------------------
// Overlay
// ---------------------------------------------------------------------------

/**
 * Draws [state]. Put it last inside a full-window Box so it sits above
 * everything, including the system bar areas under edge-to-edge.
 *
 * It leaves the composition entirely between transitions, so an idle app pays
 * nothing for it — no node, no draw, no touch handling.
 */
@Composable
fun ShutterOverlay(state: ShutterState, modifier: Modifier = Modifier) {
    if (!state.isRunning) return

    val density = LocalDensity.current
    val aperture = remember { Path() }
    val mask = remember { Path() }
    val geo = remember { ShutterGeometry() }
    // Stroke widths are fixed in dp, so the six of them are built once rather
    // than per frame. Everything else in the draw path is a reused Path.
    val rimGlow = remember(density) { RimPass.of(density, width = 3f, spread = 9f) }
    val coreGlow = remember(density) { RimPass.of(density, width = 1.2f, spread = 3f) }

    Canvas(
        modifier
            .fillMaxSize()
            // Swallow every touch for the duration. Consuming on the Initial pass
            // means a tap landing on the button underneath never reaches it, so
            // the transition cannot be re-entered through the thing that started it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            // TalkBack reads the destination, not the curtain in front of it.
            .clearAndSetSemantics {}
    ) {
        val config = state.runConfig
        val progress = state.progress
        val flash = state.flash
        if (progress <= 0.0005f && flash <= 0.001f) return@Canvas

        geo.update(size.width, size.height, config.blades, progress, config.twistDegrees)
        aperture.reset()
        geo.buildAperture(aperture)

        // 1 — the blades. One even-odd path: screen rect with the aperture
        // punched out of it. One fill, no Path.op, and no seam where two fills
        // would have met.
        mask.reset()
        mask.fillType = PathFillType.EvenOdd
        mask.addRect(Rect(0f, 0f, size.width, size.height))
        if (!geo.isShut) mask.addPath(aperture)
        drawPath(mask, config.bladeColor)

        // 2 — the seams between blades, leaning as they twist.
        if (config.seams && !geo.isShut) {
            val lean = 0.16f + geo.twist * 0.5f
            for (k in 0 until geo.blades) {
                val b = geo.twist + (k + 0.5f) * geo.step - HALF_PI
                drawLine(
                    color = config.rimCyan,
                    start = Offset(geo.cx + geo.rv * cos(b), geo.cy + geo.rv * sin(b)),
                    end = Offset(geo.cx + geo.rOut * cos(b + lean), geo.cy + geo.rOut * sin(b + lean)),
                    strokeWidth = 1.dp.toPx(),
                    alpha = 0.13f,
                )
            }
        }

        // 3 — the rim: magenta and cyan split radially with the warm core on top,
        // which is the dye-layer separation this app paints everywhere else.
        //
        // Stacked strokes rather than a blur. BlurMaskFilter is unreliable on a
        // hardware canvas and Modifier.blur is API 31, and both would force an
        // offscreen pass on the one frame budget that cannot afford it.
        if (!geo.isShut && config.glow > 0f) {
            val pivot = Offset(geo.cx, geo.cy)
            val split = config.chromatic * 0.011f * (0.35f + 0.65f * progress)
            rim(aperture, config.rimMagenta, 0.55f * config.glow, 1f + split, rimGlow, pivot)
            rim(aperture, config.rimCyan, 0.55f * config.glow, 1f - split, rimGlow, pivot)
            rim(aperture, config.rimCore, 0.95f, 1f, coreGlow, pivot)
        }

        // 4 — the exposure flash, over the top of everything.
        if (flash > 0.001f) {
            drawRect(config.flashColor, alpha = flash * 0.30f)
        }
    }
}

/** Pre-built stroke widths for one three-pass glow. */
private class RimPass(val wide: Stroke, val mid: Stroke, val tight: Stroke) {
    companion object {
        fun of(density: Density, width: Float, spread: Float): RimPass {
            val w = with(density) { width.dp.toPx() }
            val s = with(density) { spread.dp.toPx() }
            fun stroke(px: Float) = Stroke(width = px, cap = StrokeCap.Round, join = StrokeJoin.Round)
            return RimPass(stroke(w + s * 2f), stroke(w + s), stroke(w))
        }
    }
}

private fun DrawScope.rim(
    path: Path,
    color: Color,
    alpha: Float,
    scaleFactor: Float,
    pass: RimPass,
    pivot: Offset,
) {
    scale(scaleFactor, scaleFactor, pivot) {
        drawPath(path, color, alpha = (alpha * 0.18f).coerceIn(0f, 1f), style = pass.wide)
        drawPath(path, color, alpha = (alpha * 0.40f).coerceIn(0f, 1f), style = pass.mid)
        drawPath(path, color, alpha = alpha.coerceIn(0f, 1f), style = pass.tight)
    }
}

private val HALF_PI = (PI / 2.0).toFloat()

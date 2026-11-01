package com.analogvault.ui.film

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

private const val WARP_IN_MS = 560
private const val WARP_OUT_MS = 340

// Phase boundaries on the 0..1 warp progress.
private const val BEAM_END = 0.30f     // a spark, then a line shooting out sideways
private const val OPEN_END = 0.72f     // the line tears open into the content, glitching

/**
 * Shows and hides [content] by teleporting it in and out, instead of sliding.
 *
 * In: a spark at the centre stretches into a neon beam, the beam tears open
 * vertically with the content inside it in sideways-shifted glitch slices and
 * a cyan/magenta misregistered outline, then the content flickers twice and
 * locks in. Out runs the same thing backwards, faster.
 *
 * The space it takes still grows and shrinks (so a sheet around it resizes),
 * but without clipping — the content is never revealed edge-first, which is
 * what made the old expand read as a slide.
 *
 * Under reduce-motion or safelight it is a plain fade: the fringe collapses to
 * one red smudge under safelight (see [ChromaticText]), and flicker is exactly
 * what reduce-motion is for.
 *
 * @param padding space around the content that appears and disappears with it,
 *   kept outside the effect so the beam is centred on the content itself.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WarpVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    val colors = FilmTheme.colors
    if (rememberReduceMotion() || colors.safelight) {
        AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.padding(padding)) { content() }
        }
        return
    }
    val beam = colors.cyan
    val fringeA = colors.cyan
    val fringeB = colors.magenta
    val spark = colors.halide

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            tween(160), expandFrom = Alignment.CenterVertically, clip = false,
        ),
        // Collapse the space only once the warp-out has all but finished.
        exit = shrinkVertically(
            tween(140, delayMillis = WARP_OUT_MS - 140),
            shrinkTowards = Alignment.CenterVertically, clip = false,
        ),
    ) {
        val progress = transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) tween(WARP_IN_MS, easing = LinearEasing)
                else tween(WARP_OUT_MS, easing = LinearEasing)
            },
            label = "warp",
        ) { if (it == EnterExitState.Visible) 1f else 0f }

        Box(Modifier.padding(padding)) {
            Box(Modifier.drawWithContent { drawWarp(progress, beam, fringeA, fringeB, spark) }) {
                content()
            }
        }
    }
}

private fun ContentDrawScope.drawWarp(
    progress: State<Float>,
    beam: Color,
    fringeA: Color,
    fringeB: Color,
    spark: Color,
) {
    val p = progress.value
    if (p >= 1f) { drawContent(); return }
    if (p <= 0f) return

    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val line = 1.5.dp.toPx()
    // New jitter roughly every frame, but deterministic for a given progress.
    val seed = (p * 40f).toInt()

    when {
        p < BEAM_END -> {
            val a = p / BEAM_END
            val half = w * 0.55f * easeOutCubic(a)
            // Glow: wide and faint under thin and bright.
            for ((width, alpha) in GLOW) {
                drawLine(
                    beam, Offset(cx - half, cy), Offset(cx + half, cy),
                    strokeWidth = line * width, alpha = alpha, blendMode = BlendMode.Plus,
                )
            }
            drawCircle(spark, radius = line * (2.5f - 1.5f * a), center = Offset(cx, cy), alpha = 1f - a * 0.6f)
        }

        p < OPEN_END -> {
            val b = (p - BEAM_END) / (OPEN_END - BEAM_END)
            val halfOpen = cy * easeOutBack(b).coerceAtMost(1.12f)
            val top = cy - halfOpen
            val bottom = cy + halfOpen
            val settle = 1f - b

            // Content, only inside the opening, in horizontal slices knocked sideways.
            clipRect(-w, top, 2 * w, bottom) {
                val maxShift = 14.dp.toPx() * settle
                for (i in 0 until SLICES) {
                    val y0 = h * i / SLICES
                    val y1 = h * (i + 1) / SLICES
                    val dx = (noise(i, seed) - 0.5f) * 2f * maxShift
                    clipRect(-w, y0, 2 * w, y1) {
                        translate(dx, 0f) { this@drawWarp.drawContent() }
                    }
                }
            }
            // Misregistered dye outlines, converging as it settles.
            val split = 8.dp.toPx() * settle
            drawFringe(fringeA, -split, top, bottom, line, settle)
            drawFringe(fringeB, split, top, bottom, line, settle)
            // The two halves of the beam, riding the edges of the opening.
            for (y in floatArrayOf(top, bottom)) {
                drawLine(beam, Offset(-w * 0.05f, y), Offset(w * 1.05f, y),
                    strokeWidth = line * 3f, alpha = 0.3f * settle, blendMode = BlendMode.Plus)
                drawLine(beam, Offset(-w * 0.05f, y), Offset(w * 1.05f, y),
                    strokeWidth = line, alpha = settle, blendMode = BlendMode.Plus)
            }
        }

        else -> {
            val c = (p - OPEN_END) / (1f - OPEN_END)
            // Two drop-outs while the signal locks.
            val dropped = c in 0.15f..0.27f || c in 0.52f..0.58f
            if (dropped) {
                drawFringe(fringeA, -2.dp.toPx(), 0f, h, line, 0.8f)
                drawFringe(fringeB, 2.dp.toPx(), 0f, h, line, 0.8f)
            } else {
                drawContent()
                val residue = 1f - c
                drawFringe(fringeA, -2.dp.toPx() * residue, 0f, h, line, 0.6f * residue)
                drawFringe(fringeB, 2.dp.toPx() * residue, 0f, h, line, 0.6f * residue)
            }
        }
    }
}

private fun ContentDrawScope.drawFringe(
    color: Color, dx: Float, top: Float, bottom: Float, stroke: Float, alpha: Float,
) {
    if (alpha <= 0f) return
    drawRect(
        color,
        topLeft = Offset(dx, top),
        size = Size(size.width, bottom - top),
        style = Stroke(stroke),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus,
    )
}

private const val SLICES = 6
private val GLOW = listOf(7f to 0.10f, 3.5f to 0.25f, 1f to 1f)

private fun easeOutCubic(t: Float): Float { val u = 1f - t; return 1f - u * u * u }

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

/** Cheap deterministic 0..1 hash, so each slice gets its own shove per frame. */
private fun noise(i: Int, seed: Int): Float {
    var x = i * 374761393 + seed * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

package com.analogvault.ui.film

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/* ------------------------------------------------------------------------
 * Everything here exists because of minSdk 26.
 *
 *   Modifier.blur()  is API 31+   -> radial gradients instead
 *   RuntimeShader    is API 33+   -> a tiled noise bitmap instead
 *   Coloured elevation shadows API 28+ -> we draw offset rects ourselves,
 *                                        which the design wants anyway
 * ---------------------------------------------------------------------- */

/**
 * Hard offset shadow — a solid rectangle behind the content, no blur.
 *
 * The content must have its own opaque background or the shadow shows through.
 * Draw order: shadow, then background, then content.
 */
fun Modifier.hardShadow(
    color: Color,
    offsetX: Dp = 5.dp,
    offsetY: Dp = 5.dp,
): Modifier = drawBehind {
    drawRect(
        color = color,
        topLeft = Offset(offsetX.toPx(), offsetY.toPx()),
        size = size,
    )
}

/** Two stacked offset shadows, e.g. violet then a translucent magenta. */
fun Modifier.hardShadowStack(
    near: Color,
    far: Color,
    nearOffset: Dp = 5.dp,
    farOffset: Dp = 10.dp,
): Modifier = drawBehind {
    drawRect(far, Offset(farOffset.toPx(), farOffset.toPx()), size)
    drawRect(near, Offset(nearOffset.toPx(), nearOffset.toPx()), size)
}

/**
 * Halation — the glow that bleeds around a bright highlight on film.
 *
 * A radial gradient painted behind the element. Reads as soft light without
 * Modifier.blur, and it is cheaper: one gradient fill versus an offscreen
 * render pass. Suppressed automatically under safelight.
 */
fun Modifier.halation(
    color: Color,
    radius: Dp = 24.dp,
    intensity: Float = 0.45f,
    enabled: Boolean = true,
): Modifier = if (!enabled) this else drawBehind {
    val r = radius.toPx()
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = intensity), Color.Transparent),
            center = center,
            radius = maxOf(size.width, size.height) / 2f + r,
        ),
        topLeft = Offset(-r, -r),
        size = Size(size.width + r * 2, size.height + r * 2),
    )
}

/**
 * Diagonal hazard hatching — "this one is not available to you".
 *
 * Used on exposure-ladder rungs the mounted gear cannot be set to, and behind
 * the clamp banner. It is deliberately a texture and not just a dimmer colour:
 * greyed-out is the same signal the design already spends on dead stock, and a
 * rung that is unreachable because of your lens is a different fact from one
 * that is merely unselected.
 */
fun Modifier.hazardHatch(
    color: Color,
    stripe: Dp = 5.dp,
    alpha: Float = 1f,
): Modifier = drawBehind {
    val w = stripe.toPx()
    val step = w * 2
    // Slanted 45°, so each stripe is drawn as a thick line running off both
    // edges; the clip of the layout bounds does the rest.
    var x = -size.height
    while (x < size.width + size.height) {
        drawLine(
            color = color,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = w,
            alpha = alpha,
        )
        x += step
    }
}

/**
 * Film grain over the whole app.
 *
 * Generated once at runtime rather than shipped as an asset, tiled with an
 * ImageShader. Apply to the root container only — one draw call for the whole
 * tree. Do NOT animate it: moving grain reads as broken TV, and static grain
 * is what film actually looks like on a print.
 */
@Composable
fun Modifier.filmGrain(
    alpha: Float = 0.055f,
    tileSize: Int = 128,
): Modifier {
    val brush = remember(tileSize) {
        val pixels = IntArray(tileSize * tileSize) {
            val v = Random.nextInt(256)
            android.graphics.Color.argb(255, v, v, v)
        }
        val bitmap = Bitmap.createBitmap(pixels, tileSize, tileSize, Bitmap.Config.ARGB_8888)
        ShaderBrush(
            ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)
        )
    }
    // drawWithCache so the DrawScope lambda is not re-allocated per frame; the
    // grain is static, so there is nothing to recompute between draws.
    return this.drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, alpha = alpha, blendMode = BlendMode.Screen)
        }
    }
}

/**
 * True when the user has turned animations off system-wide.
 *
 * Compose has no reduced-motion API, so read the platform setting directly.
 * Gate every infinite/pulsing animation on this — the frame counter's blinking
 * "next frame" marker especially, since it never stops on its own.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

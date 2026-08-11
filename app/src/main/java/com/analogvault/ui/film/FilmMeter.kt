package com.analogvault.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * How much film is left on a bulk roll, as film rather than as a bar.
 *
 * A progress bar answers "how far through are you", which is the wrong
 * question: nobody loads a bulk canister wanting to know what percentage is
 * gone. This reads left-to-right as the length of stock still on the spool,
 * drawn as discrete frames so the quantity is countable rather than estimated,
 * and it empties from the right as the canister is used.
 *
 * @param remaining frames still available
 * @param total frames the canister held when full
 */
@Composable
fun FilmMeter(
    remaining: Int,
    total: Int,
    modifier: Modifier = Modifier,
    accent: Color = FilmTheme.colors.cyan,
    meterHeight: Dp = 12.dp,
    framePitch: Dp = 7.dp,
) {
    val colors = FilmTheme.colors
    val safeTotal = total.coerceAtLeast(1)
    val fraction = (remaining.toFloat() / safeTotal).coerceIn(0f, 1f)
    // Below a fifth left the meter turns to the mask colour — the same signal
    // expiry uses, because both mean "this stock is running out on you".
    val fill = if (fraction <= 0.2f) colors.mask else accent

    Canvas(
        modifier
            .fillMaxWidth()
            .height(meterHeight)
            .semantics {
                contentDescription = "$remaining of $total frames left"
            }
    ) {
        drawRect(colors.void)

        val pitch = framePitch.toPx()
        val frameWidth = pitch * 0.62f
        val filledWidth = size.width * fraction

        var x = 1f
        while (x < size.width - 1f) {
            val lit = x + frameWidth <= filledWidth
            drawRect(
                color = if (lit) fill else colors.edge.copy(alpha = 0.5f),
                topLeft = Offset(x, 2f),
                size = Size(frameWidth, size.height - 4f),
            )
            x += pitch
        }

        drawRect(
            color = colors.edge,
            topLeft = Offset.Zero,
            size = size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
        )
    }
}

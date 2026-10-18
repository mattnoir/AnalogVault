package com.analogvault.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pulls this colour toward the grey of the same apparent brightness.
 *
 * [factor] 1 leaves it alone; 0 removes the hue entirely.
 *
 * Not HSV. Setting HSV saturation to zero keeps *value*, and every dye in this
 * palette is a full-value colour — cyan #00E5FF, magenta #FF2BD1, yellow
 * #FFE01B all have V = 1 — so at zero they all become pure white. That is not a
 * muted palette, it is five white slabs: the filled shutter disc turned into a
 * blank white circle, and every accent lost the brightness difference that told
 * it apart from the others.
 *
 * Weighting by luminance instead keeps those differences. Cyan stays bright,
 * magenta lands mid-grey, violet stays dark, and anything drawn on top of an
 * accent keeps roughly the contrast it had.
 *
 * The weights are the sRGB luma coefficients applied to the encoded components
 * rather than to linearised ones — the cheap version, and the one that matches
 * how the eye reads a UI at a glance.
 */
fun Color.withSaturation(factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    if (f == 1f) return this
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(
        red = grey + (red - grey) * f,
        green = grey + (green - grey) * f,
        blue = grey + (blue - grey) * f,
        alpha = alpha,
        colorSpace = colorSpace,
    )
}

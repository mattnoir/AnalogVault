package com.analogvault.util

/**
 * The meter's black-and-white look as a 4×5 colour matrix, shared by the live
 * viewfinder, every place a logged frame is shown, and the PDF contact sheet,
 * so a frame looks the same grey everywhere it appears.
 *
 * Luma, then contrast stretched around mid-grey (128 of 255). Rec. 709
 * weights: green carries most of what reads as brightness, which is close
 * enough to an unfiltered panchromatic stock for judging a scene.
 * [contrast] is a multiplier around mid-grey, 1 being plain luma.
 */
fun monoMatrix(contrast: Float): FloatArray {
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

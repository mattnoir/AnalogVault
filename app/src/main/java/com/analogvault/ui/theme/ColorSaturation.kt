package com.analogvault.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Scales this color's HSV saturation by [factor], leaving hue and value
 * unchanged. 0f is fully desaturated (gray of the same brightness), 1f is
 * unchanged. Saturation is clamped to [0f, 1f] since HSV cannot exceed it.
 */
fun Color.withSaturation(factor: Float): Color {
    if (factor == 1f) return this
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(this.toArgb(), hsv)
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor((alpha * 255).toInt(), hsv))
}

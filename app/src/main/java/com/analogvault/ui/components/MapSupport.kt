package com.analogvault.ui.components

import android.content.Context
import org.osmdroid.config.Configuration

/**
 * What is left of the old inline map: the two pieces that are not about drawing.
 *
 * The map itself now lives in ShotMapScreen, which owns a whole screen. The
 * composables that used to be here drew it inside a pager and inside a scrolling
 * list, and both lost the gesture to their parent.
 */

/** Must be called before any MapView is created — done in MainActivity.onCreate. */
fun initOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = context.packageName
    }
}

/**
 * A stored `"lat, lon"` string, or null if it is anything else.
 *
 * Shot locations are free text — a place name is as valid a thing to type as a
 * coordinate — so everything that maps a shot goes through this to find out
 * whether there is a fix to draw.
 */
fun parseLatLon(location: String): Pair<Double, Double>? {
    if (location.isBlank()) return null
    val parts = location.split(",").map { it.trim() }
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
    return lat to lon
}

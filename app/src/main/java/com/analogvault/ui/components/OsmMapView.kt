package com.analogvault.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.analogvault.data.model.Shot
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Wraps an osmdroid MapView in Compose.
 * Renders one marker per shot that has a parseable "lat, lon" location string.
 * No API key required — tiles served by OpenStreetMap.
 */
@Composable
fun OsmMapView(
    shots: List<Shot>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // osmdroid needs the app context configured once
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val validShots = remember(shots) {
        shots.mapNotNull { shot ->
            parseLatLon(shot.location)?.let { (lat, lon) -> shot to GeoPoint(lat, lon) }
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)       // standard OSM tiles
                setMultiTouchControls(true)
                controller.setZoom(12.0)
                // Centre on first valid point or default
                val centre = validShots.firstOrNull()?.second ?: GeoPoint(48.8566, 2.3522)
                controller.setCenter(centre)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            validShots.forEach { (shot, point) ->
                val marker = Marker(mapView).apply {
                    position = point
                    title = shot.lens.ifBlank { "Shot" }
                    snippet = buildString {
                        if (shot.shutter.isNotBlank()) append("${shot.shutter} ")
                        if (shot.aperture.isNotBlank()) append("f/${shot.aperture} ")
                        if (shot.date.isNotBlank()) append("· ${formatDate(shot.date)}")
                    }.trim()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(marker)
            }
            // Auto-fit zoom if multiple points
            if (validShots.size > 1) {
                val box = org.osmdroid.util.BoundingBox.fromGeoPoints(validShots.map { it.second })
                mapView.zoomToBoundingBox(box.increaseByScale(1.3f), true)
            }
            mapView.invalidate()
        },
        modifier = modifier.fillMaxSize()
    )
}

/** Parse "lat, lon" or "lat,lon" strings */
private fun parseLatLon(location: String): Pair<Double, Double>? {
    if (location.isBlank()) return null
    val parts = location.split(",").map { it.trim() }
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    return lat to lon
}

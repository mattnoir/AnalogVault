package com.analogvault.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.analogvault.data.model.Shot
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class MapShot(
    val shot: Shot,
    val point: GeoPoint,
    val rollName: String = ""
)

/**
 * Below this, in degrees, a set of points is one place rather than an area.
 *
 * Roughly a metre of latitude — finer than any phone's fix and coarser than the
 * rounding in a stored coordinate string.
 */
private const val DEGENERATE_SPAN = 1e-5

/** Breathing room around a fitted bounding box, in pixels. */
private const val MAP_FIT_PADDING_PX = 80

/** Must call this before any MapView is created — do it in Application.onCreate or Activity.onCreate */
fun initOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = context.packageName
    }
}

@Composable
fun OsmMapView(
    shots: List<Shot>,
    rollName: String = "",
    modifier: Modifier = Modifier
) {
    val mapShots = remember(shots) {
        shots.mapNotNull { shot ->
            parseLatLon(shot.location)?.let { (lat, lon) ->
                MapShot(shot, GeoPoint(lat, lon), rollName)
            }
        }
    }
    OsmMapViewInternal(mapShots, modifier)
}

@Composable
fun OsmMapViewMulti(
    mapShots: List<MapShot>,
    modifier: Modifier = Modifier
) {
    OsmMapViewInternal(mapShots, modifier)
}

@Composable
private fun OsmMapViewInternal(
    mapShots: List<MapShot>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Init osmdroid synchronously before MapView is created
    remember(context) { initOsmdroid(context); true }

    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(13.0)
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()
            if (mapShots.isEmpty()) {
                mv.invalidate()
                return@AndroidView
            }

            mapShots.forEach { ms ->
                Marker(mv).apply {
                    position = ms.point
                    title = buildString {
                        if (ms.rollName.isNotBlank()) append(ms.rollName)
                        if (ms.shot.shutter.isNotBlank() || ms.shot.aperture.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            if (ms.shot.shutter.isNotBlank()) append(ms.shot.shutter)
                            if (ms.shot.aperture.isNotBlank()) append(" f/${ms.shot.aperture}")
                            if (ms.shot.iso.isNotBlank()) append(" ISO ${ms.shot.iso}")
                        }
                    }.ifBlank { "Shot" }
                    snippet = buildString {
                        if (ms.shot.lens.isNotBlank()) append(ms.shot.lens)
                        if (ms.shot.date.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(ms.shot.date.take(10))
                        }
                        if (ms.shot.notes.isNotBlank()) {
                            append("\n")
                            append(ms.shot.notes.take(80))
                        }
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mv.overlays.add(this)
                }
            }

            val lats = mapShots.map { it.point.latitude }
            val lons = mapShots.map { it.point.longitude }
            val latSpan = lats.max() - lats.min()
            val lonSpan = lons.max() - lons.min()

            when {
                // One marker, or several that are effectively the same place.
                //
                // A zero-span bounding box is what hung the app: osmdroid solves
                // for the zoom that fits the box, a box of zero degrees has no
                // such zoom, and Projection.getCloserPixel then loops on the
                // resulting infinity until the system kills the activity for not
                // responding. It is easy to hit on real data — a roll shot on one
                // afternoon in one garden is a single GPS fix repeated thirty
                // times — so it is checked before the box is ever built.
                //
                // The threshold is about a metre of latitude, well below what any
                // phone fix resolves and well above the rounding in the stored
                // string.
                mapShots.size == 1 || (latSpan < DEGENERATE_SPAN && lonSpan < DEGENERATE_SPAN) -> {
                    mv.controller.setCenter(mapShots.first().point)
                    mv.controller.setZoom(16.0)
                }
                else -> {
                    val box = BoundingBox(lats.max(), lons.max(), lats.min(), lons.min())
                        .increaseByScale(1.4f)
                    mv.post {
                        // Fitting a box needs the view's size, and post() can
                        // still land before the first layout — osmdroid divides
                        // by that size, so a zero here is the same infinity by
                        // another route.
                        if (mv.width == 0 || mv.height == 0) {
                            mv.controller.setCenter(box.centerWithDateLine)
                            mv.controller.setZoom(13.0)
                            return@post
                        }
                        try {
                            mv.zoomToBoundingBox(box, true, MAP_FIT_PADDING_PX)
                        } catch (e: Exception) {
                            // The catch used to sit outside post(), where it
                            // could only ever have caught the arithmetic above.
                            mv.controller.setCenter(box.centerWithDateLine)
                            mv.controller.setZoom(13.0)
                        }
                    }
                }
            }
            mv.invalidate()
        },
        modifier = modifier.fillMaxSize()
    )
}

fun parseLatLon(location: String): Pair<Double, Double>? {
    if (location.isBlank()) return null
    val parts = location.split(",").map { it.trim() }
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
    return lat to lon
}

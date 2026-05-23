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

            when {
                mapShots.size == 1 -> {
                    mv.controller.setCenter(mapShots.first().point)
                    mv.controller.setZoom(16.0)
                }
                else -> {
                    try {
                        val lats = mapShots.map { it.point.latitude }
                        val lons = mapShots.map { it.point.longitude }
                        val box = BoundingBox(
                            lats.max(), lons.max(), lats.min(), lons.min()
                        )
                        mv.post {
                            mv.zoomToBoundingBox(box.increaseByScale(1.4f), true, 80)
                        }
                    } catch (e: Exception) {
                        mv.controller.setCenter(mapShots.first().point)
                        mv.controller.setZoom(13.0)
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

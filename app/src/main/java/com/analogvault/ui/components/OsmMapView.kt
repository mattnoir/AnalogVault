package com.analogvault.ui.components

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

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Keep MapView instance alive across recompositions
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
        }
    }

    // Configure osmdroid on first run
    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    AndroidView(
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()
            if (mapShots.isEmpty()) return@AndroidView

            mapShots.forEach { ms ->
                val marker = Marker(mv).apply {
                    position = ms.point
                    title = buildString {
                        if (ms.rollName.isNotBlank()) append("${ms.rollName}\n")
                        if (ms.shot.shutter.isNotBlank()) append("${ms.shot.shutter} ")
                        if (ms.shot.aperture.isNotBlank()) append("f/${ms.shot.aperture} ")
                        if (ms.shot.iso.isNotBlank()) append("ISO ${ms.shot.iso}")
                    }.trim()
                    snippet = buildString {
                        if (ms.shot.lens.isNotBlank()) append(ms.shot.lens)
                        if (ms.shot.date.isNotBlank()) append(" · ${ms.shot.date.take(10)}")
                        if (ms.shot.notes.isNotBlank()) append("\n${ms.shot.notes.take(60)}")
                    }.trim()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mv.overlays.add(marker)
            }

            if (mapShots.size == 1) {
                mv.controller.setCenter(mapShots.first().point)
                mv.controller.setZoom(15.0)
            } else {
                try {
                    val box = BoundingBox.fromGeoPoints(mapShots.map { it.point })
                    mv.post { mv.zoomToBoundingBox(box.increaseByScale(1.4f), true, 80) }
                } catch (e: Exception) {
                    mv.controller.setCenter(mapShots.first().point)
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

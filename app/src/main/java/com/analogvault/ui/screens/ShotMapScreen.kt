package com.analogvault.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.analogvault.data.model.Roll
import com.analogvault.data.model.Shot
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.VaultButton
import com.analogvault.ui.components.initOsmdroid
import com.analogvault.ui.components.parseLatLon
import com.analogvault.ui.film.ChromaticText
import com.analogvault.ui.film.DyeIcon
import com.analogvault.ui.film.FilmChip
import com.analogvault.ui.film.FilmChipRow
import com.analogvault.ui.film.FilmIcons
import com.analogvault.ui.film.stockAccentFor
import com.analogvault.ui.theme.FilmTheme
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One frame that knows where it was taken.
 *
 * Carries the roll's identity and colour so the map can say what it is showing
 * without looking anything up again while drawing.
 */
data class MapFrame(
    val shot: Shot,
    val point: GeoPoint,
    val rollId: String,
    val filmName: String,
    val cameraName: String,
    /** 1-based, as printed on the rebate and shown in the shot log. */
    val frameNumber: Int,
    val accent: Color,
)

/** Frames close enough together, at the current zoom, to be one marker. */
data class MapCluster(
    val point: GeoPoint,
    val frames: List<MapFrame>,
    /**
     * True when the members are one spot on the ground rather than one spot on
     * the screen — no amount of zooming will separate them.
     */
    val singlePlace: Boolean,
) {
    val accent: Color get() = frames.first().accent
}

/** About a metre. Below this, two fixes are the same place. */
private const val SAME_PLACE = 1e-5

/**
 * Marker size.
 *
 * Big enough to read a two-digit frame number at arm's length and to hit with a
 * thumb, which the first pass at 26dp was neither.
 */
private val MARKER_SIZE = 38.dp

/**
 * How close two markers may sit before they merge, in dp.
 *
 * Kept above [MARKER_SIZE]: a radius smaller than the markers themselves lets
 * two ungrouped pins overlap, which looks like a bug in the grouping.
 */
private const val CLUSTER_RADIUS_DP = 52f

/** Individual buildings are legible from here down. */
private const val STREET_ZOOM = 16.5

/**
 * The shot map, full screen.
 *
 * It used to be a tab inside Stats and a 300dp panel inside the roll detail,
 * and in both places it was an unwinnable gesture fight: a drag on a map is a
 * pan, and the same drag was also a page swipe and a list scroll. A map wants
 * every direction, so it gets a screen where nothing else is claiming one.
 *
 * @param initialRollId opens filtered to one roll — the entry point from the
 *   roll detail, where the question is "where did I shoot *this*".
 */
@Composable
fun ShotMapScreen(
    vm: MainViewModel,
    initialRollId: String? = null,
    onBack: () -> Unit,
    onOpenRoll: (String) -> Unit,
) {
    val colors = FilmTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val rolls by vm.rolls.collectAsState()
    val films by vm.films.collectAsState()
    val cameras by vm.cameras.collectAsState()

    var rollFilter by remember { mutableStateOf(initialRollId) }
    var selected by remember { mutableStateOf<MapCluster?>(null) }

    val frames = remember(rolls, films, cameras, colors) {
        buildFrames(rolls, films.associateBy { it.id }, cameras.associate { it.id to it.name }, colors)
    }
    val rollsOnMap = remember(frames) {
        frames.groupBy { it.rollId }
            .map { (id, f) -> id to f.first().filmName }
            .sortedBy { it.second }
    }
    // A filter pointing at a roll with no GPS frames would leave the screen
    // empty with no way back to everything, so it falls back to all.
    val activeFilter = rollFilter?.takeIf { id -> rollsOnMap.any { it.first == id } }
    val shown = remember(frames, activeFilter) {
        if (activeFilter == null) frames else frames.filter { it.rollId == activeFilter }
    }

    // Whole levels only. A pinch reports a continuous zoom, and reclustering on
    // every fractional step would rebuild every marker several times a gesture
    // for a grouping that has not changed.
    var zoomLevel by remember { mutableIntStateOf(13) }
    val radiusPx = with(density) { CLUSTER_RADIUS_DP.dp.toPx() }
    val clusters = remember(shown, zoomLevel) { clusterFrames(shown, zoomLevel, radiusPx) }

    // Dropping the filter has to drop a callout belonging to the roll that just
    // left the map, or it sits there describing a frame no longer drawn.
    var pendingFit by remember { mutableStateOf(true) }
    LaunchedEffect(activeFilter) { selected = null; pendingFit = true }

    remember(context) { initOsmdroid(context); true }
    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    // The map goes inside a FrameLayout that clips, because Compose's host view
    // does not: AndroidComposeView sets clipChildren = false so its own drawing
    // works, which lets a native child paint outside the bounds Compose gave it.
    // osmdroid draws whole tiles, so the edge ones spilled past the map — over
    // the header, and up behind the transparent status bar. A container with
    // clipChildren = true is the only thing that stops it, since the spill is
    // the view system drawing, not anything Compose can clip.
    val mapContainer = remember(mapView) {
        FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            addView(
                mapView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onZoom(event: ZoomEvent?): Boolean {
                zoomLevel = mapView.zoomLevelDouble.roundToInt()
                return false
            }
            override fun onScroll(event: ScrollEvent?): Boolean = false
        }
        mapView.addMapListener(listener)
        onDispose {
            mapView.removeMapListener(listener)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // The map is the bottom layer and everything else sits on top of it.
    //
    // Not a header above a map in a Column, which is the obvious shape and the
    // wrong one: an AndroidView is a real Android view, and a real view is
    // drawn after — over — every piece of Compose composed before it, whatever
    // the layout says about who is above whom. The counts and the chips were
    // laid out correctly, could be read out by TalkBack, and were invisible
    // under the tiles. Composing them after the map puts them back on top.
    Box(Modifier.fillMaxSize().background(colors.void)) {
        if (clusters.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO GPS DATA YET", style = FilmTheme.type.data, color = colors.dead)
                    Spacer(Modifier.height(8.dp))
                    Text("WHEN LOGGING A FRAME, TAP THE PIN TO CAPTURE LOCATION",
                        style = FilmTheme.type.rebate, color = colors.dim)
                }
            }
        } else {
            AndroidView(
                factory = { mapContainer },
                update = {
                    val mv = mapView
                    mv.overlays.clear()

                    // A single roll gets its frames joined in the order they
                    // were shot. That line is the afternoon: where you started,
                    // how far you walked, where you turned back. Drawing it
                    // across every roll at once would just be scribble, so it
                    // only appears when one roll is selected.
                    if (activeFilter != null && shown.size > 1) {
                        mv.overlays.add(
                            Polyline(mv).apply {
                                setPoints(shown.sortedBy { it.frameNumber }.map { it.point })
                                outlinePaint.color = shown.first().accent.copy(alpha = 0.7f).toArgb()
                                outlinePaint.strokeWidth = with(density) { 2.dp.toPx() }
                            }
                        )
                    }

                    clusters.forEach { cluster ->
                        mv.overlays.add(
                            Marker(mv).apply {
                                position = cluster.point
                                icon = frameMarker(
                                    res = context.resources,
                                    label = if (cluster.frames.size > 1) "${cluster.frames.size}"
                                            else "${cluster.frames.first().frameNumber}",
                                    accent = cluster.accent,
                                    onAccent = colors.void,
                                    sizePx = with(density) { MARKER_SIZE.toPx() }.toInt(),
                                    stacked = cluster.frames.size > 1,
                                )
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                // osmdroid's own info window is a white rounded
                                // bubble with its own type. The callout below is
                                // the same information in this app's language.
                                infoWindow = null
                                setOnMarkerClickListener { _, _ ->
                                    // A group opens by zooming into it — that is
                                    // what the grouping means.
                                    //
                                    // Two cases have to answer with the frames
                                    // instead, or the tap leads nowhere: frames
                                    // shot from one spot, which no zoom will
                                    // ever separate, and anything still grouped
                                    // at street level, where the markers are
                                    // already metres apart and further zooming
                                    // is just work.
                                    if (cluster.singlePlace ||
                                        mv.zoomLevelDouble >= STREET_ZOOM
                                    ) {
                                        selected = cluster
                                    } else {
                                        selected = null
                                        mv.controller.animateTo(
                                            cluster.point, mv.zoomLevelDouble + 1.5, 400L,
                                        )
                                    }
                                    true
                                }
                            }
                        )
                    }

                    // Only when there is a new set of markers to frame. Fitting
                    // on every update would undo the user's own zoom a moment
                    // after they finished making it — including the zoom a tap
                    // on a group just asked for.
                    if (pendingFit) {
                        pendingFit = false
                        mv.post {
                            fit(mv, clusters)
                            zoomLevel = mv.zoomLevelDouble.roundToInt()
                        }
                    }
                    mv.invalidate()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Chrome. Solid rather than floating over the tiles: mono caps on a
        // road map is unreadable, and a scrim heavy enough to fix that is a
        // header with extra steps.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                // Background first, inset second, so the void is painted behind
                // the status bar and the content starts below it. The app draws
                // edge to edge, and the map is the first screen with something
                // opaque at the top — every other one starts with black, which
                // is why nothing needed this until now.
                .background(colors.void)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    DyeIcon(FilmIcons.ChevronRight, "Back", size = 20.dp, tint = colors.cyan,
                        modifier = Modifier.graphicsLayer { scaleX = -1f })
                }
                Box(Modifier.weight(1f)) {
                    ChromaticText("SHOT MAP", maxLines = 1)
                }
                if (clusters.isNotEmpty()) {
                    VaultButton(
                        "Fit", small = true, ghost = true, icon = FilmIcons.Map,
                        onClick = {
                            fit(mapView, clusters)
                            zoomLevel = mapView.zoomLevelDouble.roundToInt()
                        },
                    )
                }
            }
            Text(
                buildString {
                    append("${shown.size} FRAME${if (shown.size == 1) "" else "S"}")
                    append(" · ${clusters.size} PLACE${if (clusters.size == 1) "" else "S"}")
                    val without = rolls.sumOf { it.shots.size } - frames.size
                    if (activeFilter == null && without > 0) append(" · $without WITHOUT GPS")
                },
                style = FilmTheme.type.eyebrow, color = colors.dim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
            if (rollsOnMap.size > 1) {
                FilmChipRow(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
                ) {
                    FilmChip(
                        text = "All ${frames.size}",
                        color = if (activeFilter == null) colors.cyan else colors.dim,
                        filled = activeFilter == null,
                        onClick = { rollFilter = null },
                    )
                    rollsOnMap.forEach { (id, name) ->
                        val count = frames.count { it.rollId == id }
                        FilmChip(
                            text = "$name $count",
                            color = if (activeFilter == id) colors.cyan else colors.dim,
                            filled = activeFilter == id,
                            onClick = { rollFilter = id },
                        )
                    }
                }
            }
        }

        selected?.let { cluster ->
            FrameCallout(
                cluster = cluster,
                onOpenRoll = { onOpenRoll(cluster.frames.first().rollId) },
                onClose = { selected = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * What was shot here.
 *
 * Pinned to the bottom edge rather than presented as a bottom sheet: a sheet is
 * dragged, and a drag on this screen belongs to the map.
 */
@Composable
private fun FrameCallout(
    cluster: MapCluster,
    onOpenRoll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FilmTheme.colors
    val lead = cluster.frames.first()
    Column(
        modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(colors.film)
            .border(1.dp, lead.accent)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (cluster.frames.size > 1) "${cluster.frames.size} FRAMES HERE"
                else "FRAME %02d".format(lead.frameNumber),
                style = FilmTheme.type.rebate, color = lead.accent,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                DyeIcon(FilmIcons.Close, "Close", size = 14.dp, tint = colors.dim)
            }
        }
        Spacer(Modifier.height(6.dp))

        // Every frame at one spot, not just the first: standing still and
        // shooting six exposures is the normal case, and "6 frames here" that
        // then describes one of them would be answering a different question.
        cluster.frames.sortedBy { it.frameNumber }.forEach { f ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (f.shot.photoThumbPath.isNotBlank()) {
                    AsyncImage(
                        model = File(f.shot.photoThumbPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp).border(1.dp, colors.edge),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        buildString {
                            if (cluster.frames.size > 1) append("%02d · ".format(f.frameNumber))
                            append(f.shot.date.take(10).ifBlank { "NO DATE" })
                        },
                        style = FilmTheme.type.data, color = colors.halide,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            f.filmName.ifBlank { null },
                            f.shot.lens.ifBlank { null } ?: f.cameraName.ifBlank { null },
                        ).joinToString(" · "),
                        style = FilmTheme.type.rebate, color = colors.dim,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val exposure = listOfNotNull(
                        f.shot.shutter.ifBlank { null },
                        f.shot.aperture.ifBlank { null }?.let { "f/$it" },
                        f.shot.iso.ifBlank { null }?.let { "ISO $it" },
                    ).joinToString("  ")
                    if (exposure.isNotBlank()) {
                        Text(exposure, style = FilmTheme.type.rebate, color = colors.dim)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        VaultButton("Open roll", small = true, ghost = true, icon = FilmIcons.Rolls,
            onClick = onOpenRoll)
    }
}

// ─── Plumbing ─────────────────────────────────────────────────────────────────

private fun buildFrames(
    rolls: List<Roll>,
    filmsById: Map<String, com.analogvault.data.model.FilmStock>,
    cameraNames: Map<String, String>,
    colors: com.analogvault.ui.theme.FilmColors,
): List<MapFrame> = rolls.flatMap { roll ->
    val film = filmsById[roll.filmId]
    val accent = stockAccentFor(
        colors,
        name = film?.name.orEmpty(),
        type = film?.type.orEmpty(),
        override = film?.stockAccent.orEmpty(),
    ).solid
    roll.shots.mapIndexedNotNull { i, shot ->
        parseLatLon(shot.location)?.let { (lat, lon) ->
            MapFrame(
                shot = shot,
                point = GeoPoint(lat, lon),
                rollId = roll.id,
                filmName = film?.name ?: "Unknown film",
                cameraName = cameraNames[roll.cameraId].orEmpty(),
                frameNumber = i + 1,
                accent = accent,
            )
        }
    }
}

/**
 * Group markers that would overlap on screen at this zoom.
 *
 * Clustering by distance on the ground would be the wrong unit: forty metres is
 * a pile of overlapping pins from orbit and forty separate ones from a street.
 * The radius is a screen distance instead, converted to degrees at the zoom
 * being drawn, so zooming in is what pulls a group apart — a pile of pins
 * resolves into the frames it is made of as you push into it.
 *
 * Mercator at zoom z is 256·2^z pixels around the world, so a pixel is
 * 360/(256·2^z) degrees of longitude anywhere, and that many degrees of
 * latitude multiplied by cos(latitude) — which is why the two axes get
 * different cell sizes rather than one square grid.
 */
private fun clusterFrames(frames: List<MapFrame>, zoom: Int, radiusPx: Float): List<MapCluster> {
    if (frames.isEmpty()) return emptyList()
    val worldPx = 256.0 * 2.0.pow(zoom.coerceIn(1, 22))
    val cellLon = (radiusPx * 360.0 / worldPx).coerceAtLeast(SAME_PLACE)

    return frames
        .groupBy { f ->
            val cosLat = cos(Math.toRadians(f.point.latitude)).coerceAtLeast(0.01)
            val cellLat = (cellLon * cosLat).coerceAtLeast(SAME_PLACE)
            Math.round(f.point.latitude / cellLat) to Math.round(f.point.longitude / cellLon)
        }
        .map { (_, group) ->
            val lats = group.map { it.point.latitude }
            val lons = group.map { it.point.longitude }
            MapCluster(
                // Centred on the group rather than on whichever frame happened
                // to be first, so a marker sits where its frames are.
                point = GeoPoint(lats.average(), lons.average()),
                frames = group,
                singlePlace = (lats.max() - lats.min()) < SAME_PLACE &&
                    (lons.max() - lons.min()) < SAME_PLACE,
            )
        }
}

/**
 * Frame the markers.
 *
 * Both guards here are load-bearing: osmdroid solves for the zoom that fits a
 * box, and it answers a box of zero area or a view of zero size by looping on
 * an infinity until Android kills the activity.
 */
private fun fit(mv: MapView, clusters: List<MapCluster>) {
    if (clusters.isEmpty()) return
    if (mv.width == 0 || mv.height == 0) return
    val lats = clusters.map { it.point.latitude }
    val lons = clusters.map { it.point.longitude }
    val latSpan = lats.max() - lats.min()
    val lonSpan = lons.max() - lons.min()
    if (clusters.size == 1 || (latSpan < SAME_PLACE && lonSpan < SAME_PLACE)) {
        mv.controller.setCenter(clusters.first().point)
        mv.controller.setZoom(16.0)
        return
    }
    val box = BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()).increaseByScale(1.35f)
    try {
        mv.zoomToBoundingBox(box, false, 90)
    } catch (e: Exception) {
        mv.controller.setCenter(box.centerWithDateLine)
        mv.controller.setZoom(13.0)
    }
}

/**
 * A marker in the design's language: a square, hairline-bordered, carrying the
 * frame number or the count of frames stacked at that spot.
 *
 * Drawn rather than bundled because the fill is the film's own accent, so the
 * marker says which roll it belongs to before anything is tapped — and a static
 * asset cannot follow the safelight swap.
 */
private fun frameMarker(
    /**
     * The device's resources, not null.
     *
     * A BitmapDrawable built with null resources targets density 160, so its
     * intrinsic size comes out scaled by 160/deviceDensity — on this phone a
     * bitmap asked to be 38dp wide reported itself as about 14dp, which is why
     * the pins were tiny however large the bitmap was drawn.
     */
    res: android.content.res.Resources,
    label: String,
    accent: Color,
    onAccent: Color,
    sizePx: Int,
    /**
     * Draws a second square behind the first.
     *
     * Without it the marker is ambiguous in the one way that matters: "7" on a
     * lone frame means frame seven, and "7" on a group means seven frames, and
     * nothing on the marker said which. A stack of frames looks like a stack of
     * frames.
     */
    stacked: Boolean,
): BitmapDrawable {
    val offset = if (stacked) sizePx * 0.18f else 0f
    val bmpSize = (sizePx + offset).toInt()
    val bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val inset = sizePx * 0.08f

    val fill = Paint().apply {
        isAntiAlias = false
        color = accent.toArgb()
    }
    val edge = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.06f
        color = onAccent.toArgb()
    }

    fun square(left: Float, top: Float) {
        canvas.drawRect(left, top, left + sizePx - inset * 2, top + sizePx - inset * 2, fill)
        canvas.drawRect(left, top, left + sizePx - inset * 2, top + sizePx - inset * 2, edge)
    }

    // The one behind first, so the front square overlaps it.
    if (stacked) square(inset + offset, inset)
    square(inset, inset + offset)

    val text = Paint().apply {
        isAntiAlias = true
        color = onAccent.toArgb()
        textSize = sizePx * 0.46f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
    }
    val cx = inset + (sizePx - inset * 2) / 2f
    val cy = inset + offset + (sizePx - inset * 2) / 2f
    canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    return BitmapDrawable(res, bmp)
}

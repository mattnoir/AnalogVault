package com.analogvault.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dye Layer icon set. Generated from the icon sheet — edit there, not here.
 *
 * Every icon is drawn on a 24x24 grid with a 1.6 unit stroke, butt caps and mitre joins.
 * [stroke] and [fill] are the body; [accentStroke] and [accentFill] are the single dye-coloured
 * detail. Pass the body colour as the accent to collapse an icon to one tone — which is what
 * safelight mode should do, rather than filtering the whole screen.
 */
@Immutable
data class FilmIconSpec(
    val stroke: List<String> = emptyList(),
    val fill: List<String> = emptyList(),
    val accentStroke: List<String> = emptyList(),
    val accentFill: List<String> = emptyList(),
)

private const val GRID = 24f
private const val STROKE_UNITS = 1.6f

@Composable
fun FilmIcon(
    spec: FilmIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    accent: Color = tint,
) {
    val body = remember(spec) { spec.stroke.map { it.toPath() } }
    val solid = remember(spec) { spec.fill.map { it.toPath() } }
    val accentBody = remember(spec) { spec.accentStroke.map { it.toPath() } }
    val accentSolid = remember(spec) { spec.accentFill.map { it.toPath() } }
    val line = remember { Stroke(width = STROKE_UNITS, cap = StrokeCap.Butt, join = StrokeJoin.Miter) }
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription?.let { this.contentDescription = it } }
    ) {
        val s = this.size.minDimension / GRID
        scale(s, pivot = Offset.Zero) {
            body.forEach { drawPath(it, tint, style = line) }
            solid.forEach { drawPath(it, tint) }
            accentBody.forEach { drawPath(it, accent, style = line) }
            accentSolid.forEach { drawPath(it, accent) }
        }
    }
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

object FilmIcons {

    // ---- Navigation
    /** Home — viewfinder with the split-image wedge out of register. Accent: magenta. */
    val Home = FilmIconSpec(
        stroke = listOf(
            "M3 8.2V3.4h4.8",
            "M16.2 3.4H21v4.8",
            "M21 15.8v4.8h-4.8",
            "M7.8 20.6H3v-4.8",
            "M15.8 12a3.8 3.8 0 1 1-7.6 0 3.8 3.8 0 0 1 7.6 0z",
        ),
        accentStroke = listOf(
            "M8.2 10.6h3.8",
            "M12 13.4h3.8",
        ),
    )
    /** Stash — 35mm cassette, leader tongue cut asymmetrically. Accent: cyan. */
    val Stash = FilmIconSpec(
        stroke = listOf(
            "M5.6 5.6h9.2v13H5.6z",
            "M8.6 2.8h3.2v2.8H8.6z",
            "M14.8 9.8h6.4v5.4h-5z",
        ),
        accentFill = listOf(
            "M8.2 10.4h4v3.2h-4z",
        ),
    )
    /** Rolls / Loaded. Accent: cyan. */
    val Rolls = FilmIconSpec(
        stroke = listOf(
            "M2.6 5.6h18.8v12.8H2.6z",
            "M2.6 9.1h18.8",
            "M2.6 14.9h18.8",
            "M9 9.1v5.8",
            "M15 9.1v5.8",
        ),
        fill = listOf(
            "M4 6.6h1.5v1.5H4z",
            "M7.3 6.6h1.5v1.5H7.3z",
            "M10.6 6.6h1.5v1.5h-1.5z",
            "M13.9 6.6h1.5v1.5h-1.5z",
            "M17.2 6.6h1.5v1.5h-1.5z",
            "M4 15.9h1.5v1.5H4z",
            "M7.3 15.9h1.5v1.5H7.3z",
            "M10.6 15.9h1.5v1.5h-1.5z",
            "M13.9 15.9h1.5v1.5h-1.5z",
            "M17.2 15.9h1.5v1.5h-1.5z",
        ),
    )
    /** More — three sprocket holes in the rail. Accent: cyan. */
    val More = FilmIconSpec(
        stroke = listOf(
            "M2.6 7.2h18.8",
            "M2.6 16.8h18.8",
        ),
        fill = listOf(
            "M4.6 10.4h3.2v3.2H4.6z",
            "M10.4 10.4h3.2v3.2h-3.2z",
            "M16.2 10.4h3.2v3.2h-3.2z",
        ),
    )
    /** Shutter — six-blade iris, the opening picked out. Accent: magenta. */
    val Aperture = FilmIconSpec(
        stroke = listOf(
            "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z",
            "M12 6.6 17.54 4.91",
            "M7.32 9.3 8.63 3.66",
            "M7.32 14.7 3.09 10.75",
            "M12 17.4 6.46 19.09",
            "M16.68 14.7 15.37 20.34",
            "M16.68 9.3 20.91 13.25",
        ),
        accentStroke = listOf(
            "M12 6.6 7.32 9.3v5.4L12 17.4l4.68-2.7V9.3z",
        ),
    )

    // ---- Destinations
    /** Meter — a moving-coil needle, which is what a meter actually is. Accent: magenta. */
    val Meter = FilmIconSpec(
        stroke = listOf(
            "M3.6 16.8a8.4 8.4 0 0 1 16.8 0",
            "M3.6 16.8h16.8",
            "M12 4.4v2.2",
            "M5.9 9.4 7.6 10.7",
            "M18.1 9.4 16.4 10.7",
        ),
        accentStroke = listOf(
            "M12 16.2 15.8 8.4",
        ),
        accentFill = listOf(
            "M11 15.2h2v2h-2z",
        ),
    )
    /** Light right now. Accent: yellow. */
    val Weather = FilmIconSpec(
        stroke = listOf(
            "M15.6 7.2a3.6 3.6 0 1 0-6.4 2.4",
            "M15.6 2.8v1.6",
            "M19.8 7.2H18.2",
            "M18.6 4.2 17.4 5.4",
            "M5.2 18.8A3.6 3.6 0 0 1 8.3 12.4 4.5 4.5 0 0 1 15.7 13 3.2 3.2 0 0 1 18 18.8z",
        ),
        accentFill = listOf(
            "M17.8 7.2a2.2 2.2 0 1 1-4.4 0 2.2 2.2 0 0 1 4.4 0z",
        ),
    )
    /** Darkroom — dev tank at working level. Accent: violet. */
    val Darkroom = FilmIconSpec(
        stroke = listOf(
            "M9.6 2.6h4.8l1.2 3H8.4z",
            "M6.8 5.6h10.4V21H6.8z",
        ),
        accentStroke = listOf(
            "M6.8 13.4h10.4",
        ),
    )
    /** Stats — the characteristic curve, with the metered point on the straight line. Accent: cyan. */
    val Stats = FilmIconSpec(
        stroke = listOf(
            "M4 3v17h17",
            "M5.6 18.4c2.4 0 3-.4 4.2-2.8s2.4-6.6 4.4-8.2c1.2-1 2.6-1.2 4.8-1.3",
        ),
        accentFill = listOf(
            "M10.6 10.1h2.2v2.2h-2.2z",
        ),
    )
    /** Backup — out and back in. Accent: cyan. */
    val Backup = FilmIconSpec(
        stroke = listOf(
            "M3.4 13.4h17.2v7.2H3.4z",
            "M7.6 10.6V3.4",
            "M5 6 7.6 3.4 10.2 6",
            "M16.4 3.4v7.2",
            "M13.8 8 16.4 10.6 19 8",
        ),
    )
    /** Settings — a detented scale and an index, like every dial on the camera. Accent: yellow. */
    val Settings = FilmIconSpec(
        stroke = listOf(
            "M2.8 15.2h18.4",
            "M5 15.2V12",
            "M8.4 15.2v-4.8",
            "M11.8 15.2V12",
            "M15.2 15.2v-4.8",
            "M18.6 15.2V12",
        ),
        accentFill = listOf(
            "M15.2 16.4 17.8 20.8h-5.2z",
        ),
    )

    // ---- Metering and optics
    /** Spot. Accent: magenta. */
    val MeterSpot = FilmIconSpec(
        stroke = listOf(
            "M18 12a6 6 0 1 1-12 0 6 6 0 0 1 12 0z",
            "M12 2.4v3.2",
            "M12 18.4v3.2",
            "M2.4 12h3.2",
            "M18.4 12h3.2",
        ),
        accentFill = listOf(
            "M11 11h2v2h-2z",
        ),
    )
    /** Centre-weighted. Accent: magenta. */
    val MeterCentre = FilmIconSpec(
        stroke = listOf(
            "M2.6 5.4h18.8v13.2H2.6z",
            "M17.4 12a5.4 5.4 0 1 1-10.8 0 5.4 5.4 0 0 1 10.8 0z",
            "M14.6 12a2.6 2.6 0 1 1-5.2 0 2.6 2.6 0 0 1 5.2 0z",
        ),
        accentFill = listOf(
            "M11.2 11.2h1.6v1.6h-1.6z",
        ),
    )
    /** Matrix / evaluative. Accent: magenta. */
    val MeterMatrix = FilmIconSpec(
        stroke = listOf(
            "M2.6 5.4h18.8v13.2H2.6z",
            "M8.9 5.4v13.2",
            "M15.1 5.4v13.2",
            "M2.6 9.8h18.8",
            "M2.6 14.2h18.8",
        ),
        accentFill = listOf(
            "M9.4 10.3h5.2v3.4H9.4z",
        ),
    )
    /** Lenses. Accent: cyan. */
    val Lens = FilmIconSpec(
        stroke = listOf(
            "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z",
            "M16.4 12a4.4 4.4 0 1 1-8.8 0 4.4 4.4 0 0 1 8.8 0z",
            "M12 3v2.2",
            "M12 18.8V21",
            "M3 12h2.2",
            "M18.8 12H21",
        ),
        accentFill = listOf(
            "M11 11h2v2h-2z",
        ),
    )
    /** One exposure / empty frame. Accent: cyan. */
    val FilmFrame = FilmIconSpec(
        stroke = listOf(
            "M2.6 4.6h18.8v14.8H2.6z",
            "M2.6 8.1h18.8",
            "M2.6 15.9h18.8",
        ),
        fill = listOf(
            "M4 5.6h1.5v1.5H4z",
            "M7.3 5.6h1.5v1.5H7.3z",
            "M10.6 5.6h1.5v1.5h-1.5z",
            "M13.9 5.6h1.5v1.5h-1.5z",
            "M17.2 5.6h1.5v1.5h-1.5z",
            "M4 16.9h1.5v1.5H4z",
            "M7.3 16.9h1.5v1.5H7.3z",
            "M10.6 16.9h1.5v1.5h-1.5z",
            "M13.9 16.9h1.5v1.5h-1.5z",
            "M17.2 16.9h1.5v1.5h-1.5z",
        ),
    )
    /** Contact sheet. Accent: magenta. */
    val ContactSheet = FilmIconSpec(
        stroke = listOf(
            "M3.4 3.4h17.2v17.2H3.4z",
            "M9.1 3.4v17.2",
            "M14.9 3.4v17.2",
            "M3.4 9.1h17.2",
            "M3.4 14.9h17.2",
        ),
        accentFill = listOf(
            "M9.6 9.6h4.8v4.8H9.6z",
        ),
    )

    // ---- Objects
    /** Camera — pentaprism hump and rewind knob, i.e. the Zenit. Accent: cyan. */
    val Camera = FilmIconSpec(
        stroke = listOf(
            "M3 8h18v11.6H3z",
            "M8.6 8 9.8 4.4h4.4L15.4 8z",
            "M4.2 6h3.2v2H4.2z",
            "M16.2 13.8a4.2 4.2 0 1 1-8.4 0 4.2 4.2 0 0 1 8.4 0z",
        ),
        accentFill = listOf(
            "M13.8 13.8a1.8 1.8 0 1 1-3.6 0 1.8 1.8 0 0 1 3.6 0z",
        ),
    )
    /** Shelf — a film box on its edge. Accent: orange mask. */
    val Box = FilmIconSpec(
        stroke = listOf(
            "M3.4 5.6h17.2v12.8H3.4z",
            "M6.6 12.8h7.6",
            "M6.6 15.6h4.8",
        ),
        accentFill = listOf(
            "M3.4 5.6h17.2v3.6H3.4z",
        ),
    )
    /** Tip — a flashbulb, filament and all. Accent: yellow. */
    val Bulb = FilmIconSpec(
        stroke = listOf(
            "M17.4 9.2a5.4 5.4 0 1 1-10.8 0 5.4 5.4 0 0 1 10.8 0z",
            "M9.2 14.4h5.6v2.4H9.2z",
            "M10 16.8h4v3.4h-4z",
        ),
        accentStroke = listOf(
            "M10.2 9.8 11 7.8l1 2.2 1-2.2.8 2",
        ),
    )
    /** Here, now — fetch weather at your location. Accent: orange mask. */
    val Tripod = FilmIconSpec(
        stroke = listOf(
            "M12 7.2v5.6",
            "M12 12.8 6.4 20.8",
            "M12 12.8 17.6 20.8",
            "M12 12.8v8",
            "M8.8 17.4h6.4",
        ),
        accentFill = listOf(
            "M7.4 4.6h9.2v2.6H7.4z",
        ),
    )
    /** A mapped frame. Accent: orange mask. */
    val Pin = FilmIconSpec(
        stroke = listOf(
            "M12 21.6 5.6 12.4V4.4h12.8v8z",
        ),
        accentFill = listOf(
            "M10.2 7.6h3.6v3.6h-3.6z",
        ),
    )
    /** Bulk roll. Accent: orange mask. */
    val BulkFilm = FilmIconSpec(
        stroke = listOf(
            "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z",
            "M12 3v2.2",
            "M12 18.8V21",
            "M6.2 8.6A6.6 6.6 0 0 1 15.4 6.6",
            "M17.8 15.4a6.6 6.6 0 0 1-9.2 2",
        ),
        accentFill = listOf(
            "M14.8 12a2.8 2.8 0 1 1-5.6 0 2.8 2.8 0 0 1 5.6 0z",
        ),
    )
    /** To scan. Accent: cyan. */
    val Scan = FilmIconSpec(
        stroke = listOf(
            "M4 8.4V4h4.4",
            "M15.6 4H20v4.4",
            "M20 15.6V20h-4.4",
            "M8.4 20H4v-4.4",
        ),
        accentStroke = listOf(
            "M2.8 12h18.4",
        ),
    )
    /** Dev timer — the arc is the agitation window. Accent: magenta. */
    val Timer = FilmIconSpec(
        stroke = listOf(
            "M20.4 13.4a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
            "M9.4 2.2h5.2",
            "M12 2.2V5",
            "M12 8.6v4.8h3.4",
        ),
        accentStroke = listOf(
            "M12 5a8.4 8.4 0 0 1 7.3 4.2",
        ),
    )
    /** Temperature. Accent: orange mask. */
    val Thermometer = FilmIconSpec(
        stroke = listOf(
            "M10.2 14.4V4.4h3.6v10",
            "M15.6 17.4a3.6 3.6 0 1 1-7.2 0 3.6 3.6 0 0 1 7.2 0z",
            "M14.6 7h2.2",
            "M14.6 10.2h2.2",
        ),
        accentFill = listOf(
            "M11 11h2v6h-2z",
        ),
    )
    /** Agitation / inversion. Accent: violet. */
    val Invert = FilmIconSpec(
        stroke = listOf(
            "M4.6 10.4A7.6 7.6 0 0 1 17 6",
            "M19.4 13.6A7.6 7.6 0 0 1 7 18",
            "M13.4 6H17V2.4",
            "M10.6 18H7v3.6",
        ),
    )
    /** Shot map. Accent: magenta. */
    val Map = FilmIconSpec(
        stroke = listOf(
            "M3 6.4 9 4l6 2.4L21 4v13.6L15 20l-6-2.4L3 20z",
            "M9 4v13.6",
            "M15 6.4V20",
        ),
        accentFill = listOf(
            "M10.6 10.4h2.8v2.8h-2.8z",
        ),
    )

    // ---- Actions
    /** Edit — a chinagraph, paper wrap and blunt tip. Accent: yellow. */
    val Edit = FilmIconSpec(
        stroke = listOf(
            "M4.4 20.4h4L18.4 6.4l-4-4L4.4 16.4z",
            "M14.4 2.4 18.4 6.4",
        ),
        accentStroke = listOf(
            "M8.2 12.6 12.2 16.6",
            "M10.4 10.4 14.4 14.4",
        ),
        accentFill = listOf(
            "M4.4 16.4 8.4 20.4H4.4z",
        ),
    )
    /** Delete — the grease-pencil cross you put through a dead frame. Accent: safelight red. */
    val Reject = FilmIconSpec(
        stroke = listOf(
            "M2.6 6.2h18.8v11.6H2.6z",
        ),
        accentFill = listOf(
            "M5.4 8.6 6.9 7.4 18.6 15.4 17.1 16.6z",
            "M17.1 7.4 18.6 8.6 6.9 16.6 5.4 15.4z",
        ),
    )
    /** Delete, conventional. Accent: safelight red. */
    val Trash = FilmIconSpec(
        stroke = listOf(
            "M4.4 6.4h15.2",
            "M6.8 6.4 7.8 21h8.4l1-14.6",
            "M9.5 6.4V3.6h5v2.8",
            "M10.6 10.2v6.6",
            "M13.4 10.2v6.6",
        ),
    )
    /** Warning — the same hatching as an unreachable rung. Accent: orange mask. */
    val Warn = FilmIconSpec(
        stroke = listOf(
            "M12 3.2 22 20.8H2z",
        ),
        accentStroke = listOf(
            "M6.4 19.4 9.6 16.2",
            "M10.4 19.4 14.4 15.4",
            "M14.4 19.4 17.6 16.2",
        ),
    )
    /** Add. Accent: cyan. */
    val Plus = FilmIconSpec(
        stroke = listOf(
            "M12 4v16",
            "M4 12h16",
        ),
    )
    /** Close. Accent: dim. */
    val Close = FilmIconSpec(
        stroke = listOf(
            "M5.2 5.2 18.8 18.8",
            "M18.8 5.2 5.2 18.8",
        ),
    )
    /** Done. Accent: green. */
    val Check = FilmIconSpec(
        stroke = listOf(
            "M4 12.6 9.6 18.2 20 6.6",
        ),
    )
    /** Drill in. Accent: dim. */
    val ChevronRight = FilmIconSpec(
        stroke = listOf(
            "M9.4 4.4 17 12l-7.6 7.6",
        ),
    )
    /** Expand. Accent: dim. */
    val ChevronDown = FilmIconSpec(
        stroke = listOf(
            "M4.4 9.4 12 17l7.6-7.6",
        ),
    )
    /** Collapse. Accent: dim. */
    val ChevronUp = FilmIconSpec(
        stroke = listOf(
            "M4.4 14.6 12 7l7.6 7.6",
        ),
    )
    /** Filter. Accent: cyan. */
    val Filter = FilmIconSpec(
        stroke = listOf(
            "M3 6.8h18",
            "M6 12h12",
            "M9.4 17.2h5.2",
        ),
    )
    /** EV lock on. Accent: yellow. */
    val Lock = FilmIconSpec(
        stroke = listOf(
            "M5.4 10.4h13.2V21H5.4z",
            "M8.4 10.4V6.4h7.2v4",
        ),
        accentFill = listOf(
            "M11.2 13.8h1.6v3.8h-1.6z",
        ),
    )
    /** EV lock off. Accent: dim. */
    val Unlock = FilmIconSpec(
        stroke = listOf(
            "M5.4 10.4h13.2V21H5.4z",
            "M11 10.4V6.2h6.8v2.4",
        ),
        accentFill = listOf(
            "M11.2 13.8h1.6v3.8h-1.6z",
        ),
    )
    /** Load roll. Accent: cyan. */
    val LoadRoll = FilmIconSpec(
        stroke = listOf(
            "M6.4 13h11.2v8H6.4z",
            "M6.4 16.6h11.2",
            "M12 2.6v7.8",
            "M8.4 6.8 12 10.4l3.6-3.6",
        ),
        accentFill = listOf(
            "M7.8 18.2h1.5v1.5H7.8z",
            "M11.2 18.2h1.5v1.5h-1.5z",
            "M14.6 18.2h1.5v1.5h-1.5z",
        ),
    )
    /** Push. Accent: magenta. */
    val Push = FilmIconSpec(
        stroke = listOf(
            "M10.4 20.6V6.4",
            "M6.8 10 10.4 6.4 14 10",
        ),
        accentStroke = listOf(
            "M15.6 6.4h4.8",
            "M18 4v4.8",
        ),
    )
    /** Pull. Accent: cyan. */
    val Pull = FilmIconSpec(
        stroke = listOf(
            "M10.4 4.4v14.2",
            "M6.8 15 10.4 18.6 14 15",
        ),
        accentStroke = listOf(
            "M15.6 6.4h4.8",
        ),
    )
    /** Safelight. Accent: safelight red. */
    val Safelight = FilmIconSpec(
        stroke = listOf(
            "M3.2 12.6h17.6",
            "M6.6 15.6 4.8 18.4",
            "M12 15.6v3.2",
            "M17.4 15.6l1.8 2.8",
        ),
        accentFill = listOf(
            "M19 12.6a7 7 0 0 0-14 0z",
        ),
    )

    // ---- Light phases
    /** Daylight — long cardinals, short diagonals. Accent: yellow. */
    val Day = FilmIconSpec(
        stroke = listOf(
            "M12 1.6v3.2",
            "M12 19.2v3.2",
            "M1.6 12h3.2",
            "M19.2 12h3.2",
            "M16.5 7.5 17.5 6.5",
            "M6.5 17.5 7.5 16.5",
            "M16.5 16.5 17.5 17.5",
            "M6.5 6.5 7.5 7.5",
        ),
        accentFill = listOf(
            "M16.4 12a4.4 4.4 0 1 1-8.8 0 4.4 4.4 0 0 1 8.8 0z",
        ),
    )
    /** Sunrise. Accent: yellow. */
    val Sunrise = FilmIconSpec(
        stroke = listOf(
            "M2.6 18.6h18.8",
            "M12 9.8V3.4",
            "M9.2 6.2 12 3.4l2.8 2.8",
            "M4.4 13.4 6.3 15.3",
            "M19.6 13.4 17.7 15.3",
        ),
        accentFill = listOf(
            "M16.5 18.6a4.5 4.5 0 0 0-9 0z",
        ),
    )
    /** Sunset. Accent: orange mask. */
    val Sunset = FilmIconSpec(
        stroke = listOf(
            "M2.6 18.6h18.8",
            "M12 3.4v6.4",
            "M9.2 7 12 9.8l2.8-2.8",
            "M4.4 13.4 6.3 15.3",
            "M19.6 13.4 17.7 15.3",
        ),
        accentFill = listOf(
            "M16.5 18.6a4.5 4.5 0 0 0-9 0z",
        ),
    )
    /** Golden hour. Accent: orange mask. */
    val GoldenHour = FilmIconSpec(
        stroke = listOf(
            "M2.6 19h18.8",
            "M1.8 13.4h3.4",
            "M18.8 13.4h3.4",
            "M12 5.8v2.8",
            "M6.2 7.4 8.2 9.4",
            "M17.8 7.4 15.8 9.4",
        ),
        accentFill = listOf(
            "M15.6 13.4a3.6 3.6 0 1 1-7.2 0 3.6 3.6 0 0 1 7.2 0z",
        ),
    )
    /** Blue hour. Accent: violet. */
    val BlueHour = FilmIconSpec(
        stroke = listOf(
            "M2.6 16.4h18.8",
            "M8.8 16.4a3.2 3.2 0 0 0 6.4 0",
        ),
        accentStroke = listOf(
            "M4.6 12.2h4.6",
            "M14.8 12.2h4.6",
            "M9.6 8.6h4.8",
        ),
    )
    /** Night. Accent: violet. */
    val Night = FilmIconSpec(
        stroke = listOf(
            "M17.8 15.6A7.8 7.8 0 0 1 8.4 4.4a8.2 8.2 0 1 0 9.4 11.2z",
        ),
        accentFill = listOf(
            "M18.6 4.2 19.4 6.2 21.4 7 19.4 7.8 18.6 9.8 17.8 7.8 15.8 7 17.8 6.2z",
            "M6 3.2 6.5 4.5 7.8 5 6.5 5.5 6 6.8 5.5 5.5 4.2 5 5.5 4.5z",
        ),
    )

    // ---- Moon phases
    /** New moon. Accent: halide. */
    val MoonNew = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
    )
    /** Waxing crescent. Accent: halide. */
    val MoonWaxCrescent = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M12 3.5999999999999996A8.4 8.4 0 0 1 12 20.4A4.4 8.4 0 0 0 12 3.5999999999999996z",
        ),
    )
    /** First quarter. Accent: halide. */
    val MoonFirstQuarter = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M12 3.5999999999999996A8.4 8.4 0 0 1 12 20.4z",
        ),
    )
    /** Waxing gibbous. Accent: halide. */
    val MoonWaxGibbous = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M12 3.5999999999999996A8.4 8.4 0 0 1 12 20.4A4.4 8.4 0 0 1 12 3.5999999999999996z",
        ),
    )
    /** Full moon. Accent: halide. */
    val MoonFull = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
    )
    /** Waning gibbous. Accent: halide. */
    val MoonWaneGibbous = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M12 3.5999999999999996A8.4 8.4 0 0 0 12 20.4A4.4 8.4 0 0 0 12 3.5999999999999996z",
        ),
    )
    /** Last quarter. Accent: halide. */
    val MoonLastQuarter = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M12 3.5999999999999996A8.4 8.4 0 0 0 12 20.4z",
        ),
    )
    /** Waning crescent. Accent: halide. */
    val MoonWaneCrescent = FilmIconSpec(
        stroke = listOf(
            "M20.4 12a8.4 8.4 0 1 1-16.8 0 8.4 8.4 0 0 1 16.8 0z",
        ),
        accentFill = listOf(
            "M12 3.5999999999999996A8.4 8.4 0 0 0 12 20.4A4.4 8.4 0 0 1 12 3.5999999999999996z",
        ),
    )

    // ---- Conditions
    /** Broken / overcast. Accent: dim. */
    val Cloudy = FilmIconSpec(
        stroke = listOf(
            "M5.2 17.8A3.8 3.8 0 0 1 8.4 11.1 4.7 4.7 0 0 1 16.2 11.8 3.3 3.3 0 0 1 18.6 17.8z",
            "M8.6 8.6A4.4 4.4 0 0 1 16 7.4",
        ),
    )
    /** Rain. Accent: cyan. */
    val Rain = FilmIconSpec(
        stroke = listOf(
            "M5.2 14.6A3.8 3.8 0 0 1 8.4 7.9 4.7 4.7 0 0 1 16.2 8.6 3.3 3.3 0 0 1 18.6 14.6z",
        ),
        accentStroke = listOf(
            "M8.4 17.2 7.2 20.6",
            "M12 17.2l-1.2 3.4",
            "M15.6 17.2l-1.2 3.4",
        ),
    )
    /** Drizzle. Accent: cyan. */
    val Drizzle = FilmIconSpec(
        stroke = listOf(
            "M5.2 14.6A3.8 3.8 0 0 1 8.4 7.9 4.7 4.7 0 0 1 16.2 8.6 3.3 3.3 0 0 1 18.6 14.6z",
        ),
        accentStroke = listOf(
            "M9 17.2 8.4 19",
            "M12.6 17.2 12 19",
            "M10.6 20 10.2 21.6",
            "M14.2 20 13.8 21.6",
        ),
    )
    /** Thunderstorm. Accent: yellow. */
    val Thunder = FilmIconSpec(
        stroke = listOf(
            "M5.2 14.6A3.8 3.8 0 0 1 8.4 7.9 4.7 4.7 0 0 1 16.2 8.6 3.3 3.3 0 0 1 18.6 14.6z",
        ),
        accentFill = listOf(
            "M13.2 15.6h3.6l-6 6.6 1.4-4.4H8.6l4.6-5.2z",
        ),
    )
    /** Snow. Accent: cyan. */
    val Snow = FilmIconSpec(
        stroke = listOf(
            "M5.2 14.6A3.8 3.8 0 0 1 8.4 7.9 4.7 4.7 0 0 1 16.2 8.6 3.3 3.3 0 0 1 18.6 14.6z",
        ),
        accentStroke = listOf(
            "M7 18.6h3.2",
            "M7.4 17.2 9.8 20",
            "M9.8 17.2 7.4 20",
            "M13.8 18.6H17",
            "M14.2 17.2 16.6 20",
            "M16.6 17.2 14.2 20",
        ),
    )
    /** Mist / fog. Accent: dim. */
    val Fog = FilmIconSpec(
        stroke = listOf(
            "M5.2 14.6A3.8 3.8 0 0 1 8.4 7.9 4.7 4.7 0 0 1 16.2 8.6 3.3 3.3 0 0 1 18.6 14.6z",
        ),
        accentStroke = listOf(
            "M4 17.4h13",
            "M7 20.2h13",
        ),
    )
    /** Wind. Accent: dim. */
    val Wind = FilmIconSpec(
        stroke = listOf(
            "M3 9h10.6a2.6 2.6 0 1 0-2.6-2.6",
            "M3 14h12.8a2.8 2.8 0 1 1-2.8 2.8",
            "M3 19h7.4",
        ),
    )
    /** Humidity. Accent: cyan. */
    val Humidity = FilmIconSpec(
        stroke = listOf(
            "M12 3.4 6.4 12.8a6.2 6.2 0 1 0 11.2 0z",
        ),
        accentFill = listOf(
            "M12 9.4 9.2 14.2a3.2 3.2 0 1 0 5.6 0z",
        ),
    )

    /** For data-driven lookups — weather codes, sun and moon phases. */
    val byName: Map<String, FilmIconSpec> = mapOf(
        "home" to Home,
        "stash" to Stash,
        "rolls" to Rolls,
        "more" to More,
        "aperture" to Aperture,
        "meter" to Meter,
        "weather" to Weather,
        "darkroom" to Darkroom,
        "stats" to Stats,
        "backup" to Backup,
        "settings" to Settings,
        "meterSpot" to MeterSpot,
        "meterCentre" to MeterCentre,
        "meterMatrix" to MeterMatrix,
        "lens" to Lens,
        "filmFrame" to FilmFrame,
        "contactSheet" to ContactSheet,
        "camera" to Camera,
        "box" to Box,
        "bulb" to Bulb,
        "tripod" to Tripod,
        "pin" to Pin,
        "bulkFilm" to BulkFilm,
        "scan" to Scan,
        "timer" to Timer,
        "thermometer" to Thermometer,
        "invert" to Invert,
        "map" to Map,
        "edit" to Edit,
        "reject" to Reject,
        "trash" to Trash,
        "warn" to Warn,
        "plus" to Plus,
        "close" to Close,
        "check" to Check,
        "chevronRight" to ChevronRight,
        "chevronDown" to ChevronDown,
        "chevronUp" to ChevronUp,
        "filter" to Filter,
        "lock" to Lock,
        "unlock" to Unlock,
        "loadRoll" to LoadRoll,
        "push" to Push,
        "pull" to Pull,
        "safelight" to Safelight,
        "day" to Day,
        "sunrise" to Sunrise,
        "sunset" to Sunset,
        "goldenHour" to GoldenHour,
        "blueHour" to BlueHour,
        "night" to Night,
        "moonNew" to MoonNew,
        "moonWaxCrescent" to MoonWaxCrescent,
        "moonFirstQuarter" to MoonFirstQuarter,
        "moonWaxGibbous" to MoonWaxGibbous,
        "moonFull" to MoonFull,
        "moonWaneGibbous" to MoonWaneGibbous,
        "moonLastQuarter" to MoonLastQuarter,
        "moonWaneCrescent" to MoonWaneCrescent,
        "cloudy" to Cloudy,
        "rain" to Rain,
        "drizzle" to Drizzle,
        "thunder" to Thunder,
        "snow" to Snow,
        "fog" to Fog,
        "wind" to Wind,
        "humidity" to Humidity,
    )
}

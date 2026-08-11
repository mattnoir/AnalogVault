package com.analogvault.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.analogvault.data.model.BulkRoll
import com.analogvault.data.model.Camera
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Roll
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.WeatherState
import com.analogvault.ui.film.*
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.util.Constants
import com.analogvault.util.SunClock
import com.analogvault.util.daysUntilDate
import com.analogvault.util.formatCountdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Tab indices matching the mapping in MainActivity's DashboardScreen callback.
private const val TAB_STASH   = 1
private const val TAB_ACTIVE  = 2
private const val TAB_METER   = 4
private const val TAB_WEATHER = 5

/** A roll is "latent" once its last exposure is this old — shadows start drifting. */
private const val LATENT_IMAGE_DAYS = 21
/** Stock inside this window is a shoot-or-freeze decision, not a someday. */
private const val EXPIRING_SOON_DAYS = 90
/** Below this, a bulk canister will not survive another few loads. */
private const val BULK_LOW_FRAMES = 40

/**
 * Home: what is loaded, what the light is doing, what needs a decision.
 *
 * The screen this replaced was a greeting, three zeroes and a shortcut grid
 * duplicating the navigation bar. Every section here answers a question the
 * user actually arrives with, and the nudges are the part with real value —
 * they surface things that are true right now and would otherwise be found out
 * too late.
 */
@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onNavigate: (tabIndex: Int, activeSubTab: Int, rollId: String?) -> Unit
) {
    val rolls        by vm.rolls.collectAsState()
    val films        by vm.films.collectAsState()
    val cameras      by vm.cameras.collectAsState()
    val bulkRolls    by vm.bulkRolls.collectAsState()
    val weatherState by vm.weatherState.collectAsState()
    val owmKey       by vm.owmKey.collectAsState()

    // Fetch our own weather when nothing else has.
    //
    // Weather stopped being a destination in Phase 2, so the screens that used
    // to trigger this are no longer somewhere the user passes through. Guarded
    // on Idle so it happens once per process, and it never prompts: a location
    // dialog on the home screen at launch is not a trade worth making for a
    // card, so a user who has not granted location simply keeps the fallback.
    val context = LocalContext.current
    LaunchedEffect(owmKey, weatherState) {
        if (owmKey.isBlank() || weatherState !is WeatherState.Idle) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect
        getWeatherLocation(context)?.let { (lat, lon) -> vm.fetchWeather(lat, lon) }
    }

    val shooting  = rolls.filter { !it.finished && !it.developed }
    val awaitDev  = rolls.filter { it.finished && !it.developed }
    val awaitScan = rolls.filter { it.developed && !it.scanned }
    val archived  = rolls.filter { it.scanned }

    val weather = (weatherState as? WeatherState.Success)?.data
    val light = remember(weather) { weather?.let { analyzeLightConditions(it) } }
    val sun = remember(weather) {
        weather?.let {
            SunClock.from(
                sunriseEpoch = it.sys?.sunrise ?: 0L,
                sunsetEpoch = it.sys?.sunset ?: 0L,
                timezoneOffsetSeconds = it.timezone,
                nowEpoch = if (it.dt > 0) it.dt else System.currentTimeMillis() / 1000,
            )
        }
    }

    val nudges = remember(rolls, films, bulkRolls) { buildNudges(rolls, films, bulkRolls) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "header") {
            HomeHeader(sun = sun, weather = weather, loadedCount = shooting.size)
        }

        if (shooting.isNotEmpty()) {
            item(key = "hero") {
                LoadedRollHero(
                    rolls = shooting,
                    films = films,
                    cameras = cameras,
                    onLogFrame = { rollId -> vm.quickLogShot(rollId) },
                    onOpenRoll = { rollId -> onNavigate(TAB_ACTIVE, 0, rollId) },
                )
            }
        } else {
            item(key = "hero_empty") {
                EmptyLoadedHero(onLoad = { onNavigate(TAB_ACTIVE, 0, null) })
            }
        }

        item(key = "light") {
            LightRightNow(
                sun = sun,
                hasKey = owmKey.isNotBlank(),
                evAtIso100 = light?.evEstimate,
                loadedIso = shooting.firstOrNull()?.let { roll ->
                    roll.pushIso.toIntOrNull()?.takeIf { it > 0 }
                        ?: films.find { it.id == roll.filmId }?.iso?.takeIf { it > 0 }
                } ?: 100,
                onOpenMeter = { onNavigate(TAB_METER, 0, null) },
                onOpenWeather = { onNavigate(TAB_WEATHER, 0, null) },
            )
        }

        item(key = "pipeline") {
            Pipeline(
                counts = listOf(
                    "Shooting" to shooting.size,
                    "To dev" to awaitDev.size,
                    "To scan" to awaitScan.size,
                    "Archived" to archived.size,
                ),
                onStage = { index -> onNavigate(TAB_ACTIVE, index, null) },
            )
        }

        items(nudges.size, key = { "nudge_$it" }) { i ->
            val nudge = nudges[i]
            NudgeCard(
                title = nudge.title,
                body = nudge.body,
                onClick = { onNavigate(nudge.tab, nudge.subTab, nudge.rollId) },
            )
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(sun: SunClock?, weather: com.analogvault.data.network.WeatherResponse?, loadedCount: Int) {
    val colors = FilmTheme.colors

    // The headline states the most time-sensitive true thing, in that order:
    // light about to change, then light happening now, then what is loaded.
    val headline = when {
        sun?.goldenMinutesLeft != null -> "Golden hour now"
        sun?.minutesToGolden != null && sun.minutesToGolden!! <= 180 ->
            "Golden in ${formatCountdown(sun.minutesToGolden!!)}"
        sun != null && !sun.isDaylight -> "After dark"
        loadedCount > 0 -> "$loadedCount roll${if (loadedCount == 1) "" else "s"} loaded"
        else -> "Nothing loaded"
    }

    val date = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date()) }
    val subline = buildList {
        add(date.uppercase())
        sun?.let { add("SUNSET ${it.sunsetLabel}") }
        weather?.let { w ->
            val desc = w.weather.firstOrNull()?.description?.uppercase()
            if (!desc.isNullOrBlank()) add("$desc, ${w.main.temp.roundToInt()}°")
        }
    }.joinToString(" · ")

    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        ChromaticText(headline.uppercase(), maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Text(subline, style = FilmTheme.type.eyebrow, color = colors.dim,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Hero: the loaded roll ────────────────────────────────────────────────────

@Composable
private fun LoadedRollHero(
    rolls: List<Roll>,
    films: List<FilmStock>,
    cameras: List<Camera>,
    onLogFrame: (String) -> Unit,
    onOpenRoll: (String) -> Unit,
) {
    val colors = FilmTheme.colors
    val pagerState = rememberPagerState { rolls.size }

    Column {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { page ->
            val roll = rolls[page]
            val film = films.find { it.id == roll.filmId }
            val camera = cameras.find { it.id == roll.cameraId }
            val total = roll.totalShots.takeIf { it > 0 } ?: film?.frameCount ?: 36
            val exposed = roll.shots.size

            FilmStripCard(filmColor = colors.film) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (film?.name ?: "Unknown film").uppercase(),
                                style = FilmTheme.type.stock,
                                color = colors.cyan,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(camera?.name, film?.type)
                                    .joinToString(" · ").uppercase(),
                                style = FilmTheme.type.eyebrow,
                                color = colors.dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "%02d".format(exposed),
                                    style = FilmTheme.type.readout,
                                    color = colors.halide,
                                )
                                Text(
                                    "/$total",
                                    style = FilmTheme.type.data,
                                    color = colors.dim,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            Text("EXPOSED", style = FilmTheme.type.rebate, color = colors.dim)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    FrameCounter(
                        total = total,
                        exposed = exposed,
                        // Tapping the next frame logs it; tapping a shot frame
                        // opens the roll so its exposure notes can be edited.
                        onFrameClick = { index ->
                            if (index == exposed) onLogFrame(roll.id) else onOpenRoll(roll.id)
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    FilmChipRow {
                        // Both ISOs must be positive before either chip means
                        // anything: a stored pushIso of "0" otherwise renders as
                        // "Shot at 0", and log2(0 / box) as "Pull -Infinity".
                        val boxIso = film?.iso?.takeIf { it > 0 }
                        val pushIso = roll.pushIso.toIntOrNull()?.takeIf { it > 0 }
                        val shotIso = pushIso ?: boxIso
                        if (shotIso != null) FilmChip("Shot at $shotIso")
                        if (boxIso != null && pushIso != null && pushIso != boxIso) {
                            val stops = kotlin.math.log2(pushIso.toDouble() / boxIso.toDouble())
                            FilmChip(
                                if (stops > 0) "Push +${formatStops(stops)}"
                                else "Pull −${formatStops(-stops)}",
                                color = if (stops > 0) colors.magenta else colors.cyan,
                            )
                        }
                        daysSince(roll.startDate)?.let { FilmChip("Loaded ${it}d") }
                    }
                }

                Text(
                    rebateLine(
                        camera?.name,
                        film?.brand,
                        formatEdgeDate(roll.startDate),
                    ),
                    style = FilmTheme.type.rebate,
                    color = colors.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                )
            }
        }

        if (rolls.size > 1) {
            Text(
                "%02d / %02d".format(pagerState.currentPage + 1, rolls.size),
                style = FilmTheme.type.rebate,
                color = colors.dim,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyLoadedHero(onLoad: () -> Unit) {
    val colors = FilmTheme.colors
    Box(Modifier.padding(horizontal = 16.dp)) {
        FilmStripCard(filmColor = colors.void, onClick = onLoad) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 22.dp)) {
                // Empty states get a verb. "No rolls in camera" is a fact;
                // "Load one" is the thing to do about it.
                Text("NO ROLLS IN CAMERA", style = FilmTheme.type.stock, color = colors.dead)
                Spacer(Modifier.height(6.dp))
                Text("LOAD ONE", style = FilmTheme.type.eyebrow, color = colors.cyan)
            }
        }
    }
}

// ─── Light right now ──────────────────────────────────────────────────────────

@Composable
private fun LightRightNow(
    sun: SunClock?,
    hasKey: Boolean,
    evAtIso100: Int?,
    loadedIso: Int,
    onOpenMeter: () -> Unit,
    onOpenWeather: () -> Unit,
) {
    val colors = FilmTheme.colors

    SectionCard(
        title = "Light right now",
        onClick = if (sun == null) onOpenWeather else onOpenMeter,
    ) {
        if (sun == null) {
            // Two different dead ends, and telling them apart is the difference
            // between the user fixing it and the user assuming it is broken.
            Text(
                if (hasKey) "Waiting on a location fix. Tap to fetch the forecast."
                else "Add an OpenWeatherMap key in Settings to see the sun arc and a suggested exposure.",
                style = FilmTheme.type.data,
                color = colors.dim,
            )
            return@SectionCard
        }

        SunArc(sun)

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("EV", style = FilmTheme.type.rebate, color = colors.dim)
                Text(
                    evAtIso100?.toString() ?: "—",
                    style = FilmTheme.type.readout,
                    color = colors.yellow,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (evAtIso100 != null) {
                val shutter = "1/125"
                val aperture = Constants.nearestStandardAperture(
                    Constants.calcAperture(loadedIso, shutter, evAtIso100.toDouble())
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "f/${formatStop(aperture)} · $shutter",
                        style = FilmTheme.type.data,
                        color = colors.halide,
                    )
                    Text("AT ISO $loadedIso", style = FilmTheme.type.rebate, color = colors.dim)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            val goldenLabel = when {
                sun.goldenMinutesLeft != null ->
                    "GOLDEN FOR ${formatCountdown(sun.goldenMinutesLeft!!)}"
                sun.minutesToGolden != null ->
                    "GOLDEN IN ${formatCountdown(sun.minutesToGolden!!)}"
                else -> "GOLDEN HOUR PASSED"
            }
            Text(goldenLabel, style = FilmTheme.type.rebate, color = colors.yellow)
            Spacer(Modifier.weight(1f))
            Text("BLUE ${sun.blueWindowLabel}", style = FilmTheme.type.rebate, color = colors.cyan)
        }
    }
}

/**
 * The sun's path across the day, with a marker at now.
 *
 * The arc is the daylight span only — sunrise at the left edge, sunset at the
 * right — so the marker's horizontal position reads directly as "how much day
 * is left", which is the question being asked.
 */
@Composable
private fun SunArc(sun: SunClock) {
    val colors = FilmTheme.colors
    val fraction = sun.dayFraction.coerceIn(0f, 1f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        val w = size.width
        val h = size.height
        val baseline = h - 6f
        val peak = 10f

        val path = Path().apply {
            moveTo(0f, baseline)
            quadraticBezierTo(w / 2f, peak - (baseline - peak) * 0.35f, w, baseline)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(colors.cyan, colors.yellow, colors.magenta)
            ),
            style = Stroke(width = 2f),
        )

        // Quadratic Bezier evaluated at t = fraction: the marker sits on the
        // curve rather than on a straight line between its ends.
        val t = fraction
        val cx = 2f * (1 - t) * t * (w / 2f) + t * t * w
        val cy = (1 - t) * (1 - t) * baseline +
            2f * (1 - t) * t * (peak - (baseline - peak) * 0.35f) +
            t * t * baseline

        if (sun.isDaylight) {
            // Dotted drop line to the horizon, so the marker reads as a position
            // in the day and not as a decoration floating on the curve.
            drawLine(
                color = colors.dim,
                start = Offset(cx, cy),
                end = Offset(cx, baseline),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)),
            )
            drawCircle(colors.yellow.copy(alpha = 0.25f), radius = 16f, center = Offset(cx, cy))
            drawCircle(colors.yellow, radius = 7f, center = Offset(cx, cy))
        }

        drawLine(
            color = colors.edge,
            start = Offset(0f, baseline),
            end = Offset(w, baseline),
            strokeWidth = 1f,
        )
    }
}

// ─── Pipeline ─────────────────────────────────────────────────────────────────

@Composable
private fun Pipeline(counts: List<Pair<String, Int>>, onStage: (Int) -> Unit) {
    val colors = FilmTheme.colors
    // Cyan, yellow, violet, dead: live work, waiting, processing, finished.
    val stageColors = listOf(colors.cyan, colors.yellow, colors.violet, colors.dead)

    SectionCard(title = "Pipeline") {
        Row(Modifier.fillMaxWidth()) {
            counts.forEachIndexed { i, (label, count) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onStage(i) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        count.toString(),
                        style = FilmTheme.type.stock,
                        color = if (count == 0) colors.dead else stageColors[i],
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label.uppercase(),
                        style = FilmTheme.type.rebate,
                        color = colors.dim,
                        maxLines = 1,
                    )
                }
                if (i < counts.lastIndex) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(52.dp)
                            .background(colors.edge)
                    )
                }
            }
        }
    }
}

// ─── Nudges ───────────────────────────────────────────────────────────────────

private data class Nudge(
    val title: String,
    val body: String,
    val tab: Int,
    val subTab: Int = 0,
    val rollId: String? = null,
)

/**
 * The three things worth interrupting someone about.
 *
 * All are cheap to compute and all are true of the data rather than guessed:
 * a latent image really does drift, expiring stock really is a decision, and a
 * bulk roll really does run out mid-load if nobody is counting.
 */
private fun buildNudges(
    rolls: List<Roll>,
    films: List<FilmStock>,
    bulkRolls: List<BulkRoll>,
): List<Nudge> = buildList {
    rolls.filter { !it.developed && it.shots.isNotEmpty() }
        .mapNotNull { roll -> daysSince(roll.shots.last().date)?.let { roll to it } }
        .filter { (_, days) -> days > LATENT_IMAGE_DAYS }
        .sortedByDescending { (_, days) -> days }
        .take(2)
        .forEach { (roll, days) ->
            val name = films.find { it.id == roll.filmId }?.name ?: "A roll"
            add(
                Nudge(
                    title = "Latent image",
                    body = "$name has been sitting exposed for $days days. " +
                        "Shadows start drifting past a month — worth souping this week.",
                    tab = TAB_ACTIVE,
                    subTab = if (roll.finished) 1 else 0,
                    rollId = roll.id,
                )
            )
        }

    films.filter { it.quantity > 0 }
        .mapNotNull { film -> daysUntilDate(film.expiryDate)?.let { film to it } }
        .filter { (_, days) -> days in 0..EXPIRING_SOON_DAYS }
        .sortedBy { (_, days) -> days }
        .take(2)
        .forEach { (film, days) ->
            add(
                Nudge(
                    title = "Shoot or freeze",
                    body = "${film.name} expires in $days days. " +
                        "Shoot it soon, or move it to the freezer and stop the clock.",
                    tab = TAB_STASH,
                )
            )
        }

    bulkRolls.map { it to (it.totalFrames - it.usedFrames).coerceAtLeast(0) }
        .filter { (bulk, remaining) -> bulk.totalFrames > 0 && remaining < BULK_LOW_FRAMES }
        .sortedBy { (_, remaining) -> remaining }
        .take(1)
        .forEach { (bulk, remaining) ->
            add(
                Nudge(
                    title = "Bulk running low",
                    body = "${bulk.name} has $remaining frames left — about one more load. " +
                        "Time to reorder.",
                    tab = TAB_STASH,
                )
            )
        }
}

@Composable
private fun NudgeCard(title: String, body: String, onClick: () -> Unit) {
    val colors = FilmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(colors.yellow.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
    ) {
        // A left rule rather than a full border: a nudge is an aside, and a
        // boxed one competes with the cards above it for the same attention.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.yellow)
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title.uppercase(), style = FilmTheme.type.rebate, color = colors.yellow)
            Spacer(Modifier.height(4.dp))
            Text(body, style = FilmTheme.type.data, color = colors.halide)
        }
    }
}

// ─── Shared card shell ────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FilmTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, colors.edge)
            .background(colors.film)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = FilmTheme.type.eyebrow, color = colors.dim)
            Spacer(Modifier.width(10.dp))
            HorizontalDivider(color = colors.edge)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** "8" for whole stops, "5.6" otherwise. */
private fun formatStop(a: Double): String =
    if (a == a.toLong().toDouble()) a.toLong().toString()
    else "%.1f".format(Locale.US, a)

/**
 * "2" for a clean push, "1.3" for a third-stop one.
 *
 * Rounding everything to whole stops would print a 640-from-400 push as "+1",
 * which is two thirds of a stop of lie.
 */
private fun formatStops(stops: Double): String {
    val rounded = kotlin.math.round(stops)
    return if (kotlin.math.abs(stops - rounded) < 0.05) rounded.toLong().toString()
    else "%.1f".format(Locale.US, stops)
}

/** Edge printing wants "06 AUG 2026", not the stored "2026-08-06". */
private fun formatEdgeDate(raw: String): String? {
    if (raw.isBlank()) return null
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.take(10)) ?: return raw
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed)
    } catch (_: Exception) {
        raw
    }
}

/**
 * Whole days since a stored date, or null when it cannot be parsed.
 *
 * Shots carry "yyyy-MM-dd HH:mm" and rolls carry "yyyy-MM-dd", so the first ten
 * characters are the common ground.
 */
private fun daysSince(raw: String): Int? {
    if (raw.isBlank()) return null
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.take(10)) ?: return null
        ((Date().time - parsed.time) / 86_400_000L).toInt().coerceAtLeast(0)
    } catch (_: Exception) {
        null
    }
}

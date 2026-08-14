package com.analogvault.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.Stats
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.FilmTheme
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(vm: MainViewModel) {
    val colors   = FilmTheme.colors
    val stats    by vm.stats.collectAsState()
    val currency by vm.currency.collectAsState()

    val tabs = listOf("Numbers", "Habits", "Map")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(colors.void)) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = colors.void, contentColor = colors.cyan,
            indicator = { positions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(positions[pagerState.currentPage])
                        .height(2.dp)
                        .background(colors.cyan)
                )
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(t.uppercase(), style = FilmTheme.type.eyebrow) },
                    selectedContentColor = colors.cyan, unselectedContentColor = colors.dim)
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0    -> StatsNumbers(stats, currency)
                1    -> StatsHabits(stats)
                else -> StatsMap(vm)
            }
        }
    }
}

// ─── Numbers tab ──────────────────────────────────────────────────────────────

@Composable
fun StatsNumbers(stats: Stats, currency: String = "€") {
    val colors = FilmTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatBox("Total rolls", stats.totalRolls.toString(), colors.halide, Modifier.weight(1f))
                StatBox("Developed", stats.developed.toString(), colors.cyan, Modifier.weight(1f))
                StatBox("Shooting", stats.shooting.toString(), colors.magenta, Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatBox("Total frames", stats.totalShots.toString(), colors.halide, Modifier.weight(1f))
                StatBox("Avg per roll", "%.1f".format(stats.avgShots), colors.dim, Modifier.weight(1f))
                StatBox("Awaiting dev", stats.finished.toString(), colors.yellow, Modifier.weight(1f))
            }
        }

        if (stats.byMonth.isNotEmpty()) {
            item { StatsEyebrow("Frames by month") }
            item { MonthBarChart(stats) }
        }

        val totalCost = stats.totalFilmCost + stats.totalDevCost + stats.totalScanCost
        if (stats.totalRolls > 0) {
            item { StatsEyebrow("Cost") }
            item {
                StatsCard {
                    val fmt = { v: Double -> if (v > 0.0) "$currency%.2f".format(v) else "—" }
                    CostRow("Film on rolls", fmt(stats.filmCostOnRolls))
                    if (stats.uncutBulkValue > 0.0) {
                        CostRow("Bulk still on the spool", fmt(stats.uncutBulkValue))
                    }
                    val devLabel = when {
                        stats.selfDevRolls > 0 && stats.labDevRolls > 0 ->
                            "Development · ${stats.selfDevRolls} self / ${stats.labDevRolls} lab"
                        stats.selfDevRolls > 0 -> "Development · self, ${stats.selfDevRolls} rolls"
                        stats.labDevRolls > 0  -> "Development · lab, ${stats.labDevRolls} rolls"
                        else -> "Development"
                    }
                    CostRow(devLabel, fmt(stats.totalDevCost))
                    CostRow("Scanning", fmt(stats.totalScanCost))
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = colors.edge)
                    Spacer(Modifier.height(4.dp))
                    CostRow(
                        "Total",
                        if (totalCost > 0) "$currency%.2f".format(totalCost) else "None recorded",
                        accent = if (totalCost > 0) colors.yellow else colors.dim,
                    )
                    // Cost per frame is the number that actually changes
                    // behaviour, so it is the one printed large — but it is
                    // computed from what those frames consumed, not from the
                    // total above. Bulk still on the spool is stock you own, not
                    // money these frames cost, and dividing it by frames shot
                    // would make every fresh 30 m roll look like a spending
                    // spree.
                    val spent = stats.filmCostOnRolls + stats.totalDevCost + stats.totalScanCost
                    if (stats.totalShots > 0 && spent > 0.0) {
                        Spacer(Modifier.height(12.dp))
                        Text("PER FRAME · FILM ON ROLLS, DEV AND SCAN",
                            style = FilmTheme.type.rebate, color = colors.dim)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "$currency%.3f".format(spent / stats.totalShots),
                            style = FilmTheme.type.readout.copy(fontSize = 34.sp),
                            color = colors.yellow,
                        )
                    }
                }
            }

            if (stats.rollCosts.isNotEmpty()) {
                item { StatsEyebrow("Cost per roll") }
                item {
                    StatsCard {
                        Row(Modifier.fillMaxWidth()) {
                            Text("FILM", style = FilmTheme.type.rebate, color = colors.dim,
                                modifier = Modifier.weight(1f))
                            Text("TOTAL", style = FilmTheme.type.rebate, color = colors.dim,
                                modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                            Text("/FRAME", style = FilmTheme.type.rebate, color = colors.dim,
                                modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                        }
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = colors.edge)
                        stats.rollCosts.forEach { rc ->
                            Spacer(Modifier.height(9.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rc.filmName.uppercase(), style = FilmTheme.type.data,
                                        color = colors.halide, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    val breakdown = buildList {
                                        if (rc.filmCost > 0) add("FILM $currency%.2f".format(rc.filmCost))
                                        if (rc.devCost > 0) add("DEV $currency%.2f".format(rc.devCost))
                                        if (rc.scanCost > 0) add("SCAN $currency%.2f".format(rc.scanCost))
                                    }
                                    if (breakdown.isNotEmpty()) {
                                        Text(breakdown.joinToString("  ▸  "),
                                            style = FilmTheme.type.rebate, color = colors.dim)
                                    }
                                }
                                Text("$currency%.2f".format(rc.totalCost), style = FilmTheme.type.data,
                                    color = colors.yellow, modifier = Modifier.width(58.dp),
                                    textAlign = TextAlign.End)
                                Text(if (rc.costPerShot > 0) "$currency%.3f".format(rc.costPerShot) else "—",
                                    style = FilmTheme.type.data, color = colors.dim,
                                    modifier = Modifier.width(58.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        if (stats.byFilm.isNotEmpty()) {
            item { StatsEyebrow("Top film stocks") }
            item {
                StatsCard {
                    stats.byFilm.forEachIndexed { i, (name, count) ->
                        if (i > 0) Spacer(Modifier.height(9.dp))
                        RankRow(name, count, stats.byFilm.maxOfOrNull { it.value } ?: 1, colors.cyan)
                    }
                }
            }
        }

        if (stats.byCam.isNotEmpty()) {
            item { StatsEyebrow("Top cameras") }
            item {
                StatsCard {
                    stats.byCam.forEachIndexed { i, (name, count) ->
                        if (i > 0) Spacer(Modifier.height(9.dp))
                        RankRow(name, count, stats.byCam.maxOfOrNull { it.value } ?: 1, colors.magenta)
                    }
                }
            }
        }

        if (stats.byProc.isNotEmpty()) {
            item { StatsEyebrow("Dev processes") }
            item {
                StatsCard {
                    stats.byProc.forEachIndexed { i, (name, count) ->
                        if (i > 0) Spacer(Modifier.height(9.dp))
                        RankRow(name, count, stats.byProc.maxOfOrNull { it.value } ?: 1, colors.violet)
                    }
                }
            }
        }

        if (stats.totalRolls == 0) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("NO ROLLS SHOT YET", style = FilmTheme.type.data, color = colors.dead)
                }
            }
        }
    }
}

// ─── Habits tab ───────────────────────────────────────────────────────────────

/**
 * How this person actually shoots, as opposed to what their gear can do.
 *
 * Both charts run along the photographic scale rather than sorted by count, so
 * the shape means something: a spike at f/8 and 1/125 is a daylight shooter, a
 * long tail into the slow speeds is someone who uses a tripod.
 */
@Composable
fun StatsHabits(stats: Stats) {
    val colors = FilmTheme.colors
    if (stats.byAperture.isEmpty() && stats.byShutter.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOTHING TO READ YET", style = FilmTheme.type.data, color = colors.dead)
                Spacer(Modifier.height(8.dp))
                Text("LOG FRAMES WITH AN APERTURE AND A SHUTTER SPEED",
                    style = FilmTheme.type.rebate, color = colors.dim)
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (stats.byAperture.isNotEmpty()) {
            item { StatsEyebrow("Apertures") }
            item { HabitChart(stats.byAperture, colors.cyan, prefix = "f/") }
            item { HabitSummary(stats.byAperture, "MOST USED", "f/") }
        }
        if (stats.byShutter.isNotEmpty()) {
            item { StatsEyebrow("Shutter speeds") }
            item { HabitChart(stats.byShutter, colors.magenta) }
            item { HabitSummary(stats.byShutter, "MOST USED") }
        }
    }
}

/**
 * A histogram along the stop scale.
 *
 * Empty buckets inside the range are drawn as empty, not skipped: the gap
 * between the fast glass you own and the aperture you actually use is the
 * interesting part, and closing it up would hide it.
 */
@Composable
private fun HabitChart(
    buckets: List<Pair<String, Int>>,
    accent: Color,
    prefix: String = "",
) {
    val colors = FilmTheme.colors
    val max = (buckets.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(colors.film)
            .border(1.dp, colors.edge)
            .padding(12.dp)
    ) {
        // Decorative: the labelled row underneath is the real content,
        // and a bare Canvas would otherwise be an unlabelled node.
        Canvas(Modifier.fillMaxWidth().height(130.dp).clearAndSetSemantics { }) {
            val n = buckets.size
            if (n == 0) return@Canvas
            val gap = 4.dp.toPx()
            val barW = (size.width - (n - 1) * gap) / n
            buckets.forEachIndexed { i, (_, count) ->
                val x = i * (barW + gap)
                // Track first, so an unused stop still occupies its width and
                // the scale stays evenly spaced.
                drawRect(colors.filmRaised, Offset(x, 0f), Size(barW, size.height))
                if (count > 0) {
                    // Floor the height so one frame out of thirty still draws as
                    // something rather than a hairline nobody can tell from the
                    // empty buckets either side of it.
                    val h = maxOf(size.height * (count.toFloat() / max), 3.dp.toPx())
                    drawRect(accent, Offset(x, size.height - h), Size(barW, h))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            buckets.forEach { (label, count) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$prefix$label", style = FilmTheme.type.rebate.copy(letterSpacing = 0.sp),
                        color = if (count > 0) colors.halide else colors.dead,
                        maxLines = 1, softWrap = false)
                    Text(if (count > 0) "$count" else "·", style = FilmTheme.type.rebate,
                        color = if (count > 0) accent else colors.dead)
                }
            }
        }
    }
}

@Composable
private fun HabitSummary(buckets: List<Pair<String, Int>>, label: String, prefix: String = "") {
    val colors = FilmTheme.colors
    val total = buckets.sumOf { it.second }
    val top = buckets.maxByOrNull { it.second } ?: return
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Written out rather than built from FilmChip, which uppercases: an
        // f-number is written with a lowercase f, and "F/2.8" is the kind of
        // detail this app is otherwise careful about.
        HabitStat("$label $prefix${top.first}", colors.halide)
        HabitStat("${top.second * 100 / total.coerceAtLeast(1)}% OF FRAMES", colors.dim)
        HabitStat("$total LOGGED", colors.dim)
    }
}

@Composable
private fun HabitStat(text: String, color: Color) {
    Box(Modifier.border(1.dp, color).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, style = FilmTheme.type.data, color = color, maxLines = 1)
    }
}

// ─── Map tab ──────────────────────────────────────────────────────────────────

@Composable
fun StatsMap(vm: MainViewModel) {
    val colors = FilmTheme.colors
    val rolls by vm.rolls.collectAsState()
    val films by vm.films.collectAsState()

    // Build all MapShot entries from all rolls
    val allMapShots = remember(rolls, films) {
        rolls.flatMap { roll ->
            val filmName = films.find { it.id == roll.filmId }?.name ?: "Unknown"
            roll.shots.mapNotNull { shot ->
                com.analogvault.ui.components.parseLatLon(shot.location)?.let { (lat, lon) ->
                    com.analogvault.ui.components.MapShot(
                        shot = shot,
                        point = org.osmdroid.util.GeoPoint(lat, lon),
                        rollName = filmName
                    )
                }
            }
        }
    }

    val totalWithGps = allMapShots.size
    val totalShots   = rolls.sumOf { it.shots.size }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(colors.void).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$totalWithGps FRAMES WITH GPS", style = FilmTheme.type.rebate, color = colors.halide)
            if (totalShots > 0) {
                Text("${totalWithGps * 100 / totalShots}%", style = FilmTheme.type.rebate, color = colors.dim)
            }
        }

        if (allMapShots.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO GPS DATA YET", style = FilmTheme.type.data, color = colors.dead)
                    Spacer(Modifier.height(8.dp))
                    Text("WHEN LOGGING A FRAME, TAP 📍 TO CAPTURE LOCATION",
                        style = FilmTheme.type.rebate, color = colors.dim)
                }
            }
        } else {
            OsmMapViewMulti(mapShots = allMapShots, modifier = Modifier.fillMaxSize())
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun StatsEyebrow(title: String) {
    val colors = FilmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = FilmTheme.type.eyebrow, color = colors.dim)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = colors.edge)
    }
}

@Composable
private fun StatsCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = FilmTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(colors.film)
            .border(1.dp, colors.edge)
            .padding(12.dp),
        content = content,
    )
}

@Composable
private fun CostRow(label: String, value: String, accent: Color? = null) {
    val colors = FilmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label.uppercase(), style = FilmTheme.type.rebate, color = accent ?: colors.dim,
            modifier = Modifier.weight(1f))
        Text(value, style = FilmTheme.type.data, color = accent ?: colors.halide)
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = FilmTheme.colors
    Column(
        modifier
            .background(colors.film)
            .border(1.dp, colors.edge)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = FilmTheme.type.readout.copy(fontSize = 26.sp), color = color,
            maxLines = 1, softWrap = false)
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = FilmTheme.type.rebate, color = colors.dim,
            textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun MonthBarChart(stats: Stats) {
    if (stats.byMonth.isEmpty()) return
    val colors = FilmTheme.colors
    val maxVal = (stats.byMonth.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
    val bar = colors.yellow
    val track = colors.filmRaised

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(colors.film)
            .border(1.dp, colors.edge)
            .padding(12.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(110.dp).clearAndSetSemantics { }) {
            val n = stats.byMonth.size
            if (n == 0) return@Canvas
            val gap = 4.dp.toPx()
            val barW = (size.width - (n - 1) * gap) / n
            stats.byMonth.forEachIndexed { i, (_, count) ->
                val x = i * (barW + gap)
                drawRect(track, Offset(x, 0f), Size(barW, size.height))
                val h = size.height * (count.toFloat() / maxVal)
                if (h > 0) drawRect(bar, Offset(x, size.height - h), Size(barW, h))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            stats.byMonth.forEach { (month, count) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        month.substringAfter("-").let {
                            listOf("", "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                                "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
                                .getOrNull(it.toIntOrNull() ?: 0) ?: it
                        },
                        style = FilmTheme.type.rebate.copy(letterSpacing = 0.sp),
                        color = colors.dim, maxLines = 1, softWrap = false,
                    )
                    Text("$count", style = FilmTheme.type.rebate, color = bar)
                }
            }
        }
    }
}

@Composable
fun RankRow(name: String, count: Int, max: Int, color: Color) {
    val colors = FilmTheme.colors
    val frac = (count.toFloat() / max).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name.uppercase(), style = FilmTheme.type.data, color = colors.halide,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", style = FilmTheme.type.data, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.filmRaised)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(color))
        }
    }
}

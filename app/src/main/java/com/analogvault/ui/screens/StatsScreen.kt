package com.analogvault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.analogvault.util.Constants
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.analogvault.ui.MainViewModel
import kotlinx.coroutines.launch
import com.analogvault.ui.Stats
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*

@Composable
fun StatsScreen(vm: MainViewModel) {
    val stats    by vm.stats.collectAsState()
    val rolls    by vm.rolls.collectAsState()
    val films    by vm.films.collectAsState()
    val currency by vm.currency.collectAsState()

    val tabs = listOf("Numbers", "Films", "Map", "Photos")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Bg2, contentColor = Amber,
            indicator = { positions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(positions[pagerState.currentPage])
                        .height(2.dp)
                        .background(Amber, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                )
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(t, fontSize = 13.sp) },
                    selectedContentColor = Amber, unselectedContentColor = TextTertiary)
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0    -> StatsNumbers(stats, currency)
                1    -> StatsFilms(vm, currency)
                2    -> StatsMap(vm)
                else -> StatsPhotos(vm)
            }
        }
    }
}

// ─── Numbers tab ──────────────────────────────────────────────────────────────

@Composable
fun StatsNumbers(stats: Stats, currency: String = "€") {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Total Rolls", stats.totalRolls.toString(), Amber, Modifier.weight(1f))
                StatBox("Developed",   stats.developed.toString(),   GreenOk, Modifier.weight(1f))
                StatBox("Shooting",    stats.shooting.toString(),    BlueInfo, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Total Shots", stats.totalShots.toString(),      Amber, Modifier.weight(1f))
                StatBox("Avg/Roll",    "%.1f".format(stats.avgShots),    TextSecondary, Modifier.weight(1f))
                StatBox("Awaiting Dev",stats.finished.toString(),        OrangeWarn, Modifier.weight(1f))
            }
        }

        if (stats.byMonth.isNotEmpty()) {
            item { SectionTitle("Shots by Month") }
            item { MonthBarChart(stats) }
        }

        // Cost breakdown — shown whenever there are rolls
        val totalCost = stats.totalFilmCost + stats.totalDevCost + stats.totalScanCost
        if (stats.totalRolls > 0) {
            item { SectionTitle("Cost Breakdown") }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .drawBehind { drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())) }
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fmt = { v: Double -> if (v > 0.0) "${currency}%.2f".format(v) else "—" }
                    val perShot = if (stats.totalShots > 0 && totalCost > 0.0)
                        "${currency}%.3f/shot".format(totalCost / stats.totalShots) else null

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Film",         color = TextSecondary, fontSize = 13.sp)
                        Text(fmt(stats.totalFilmCost), color = TextPrimary, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val devLabel = when {
                            stats.selfDevRolls > 0 && stats.labDevRolls > 0 ->
                                "Development (${stats.selfDevRolls} self / ${stats.labDevRolls} lab)"
                            stats.selfDevRolls > 0 -> "Development (self, ${stats.selfDevRolls} rolls)"
                            stats.labDevRolls > 0  -> "Development (lab, ${stats.labDevRolls} rolls)"
                            else -> "Development"
                        }
                        Text(devLabel, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(fmt(stats.totalDevCost), color = TextPrimary, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Scanning",     color = TextSecondary, fontSize = 13.sp)
                        Text(fmt(stats.totalScanCost), color = TextPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", color = if (totalCost > 0) Amber else TextSecondary,
                            fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(if (totalCost > 0) "${currency}%.2f".format(totalCost) else "No costs recorded",
                            color = if (totalCost > 0) Amber else TextTertiary,
                            fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    if (perShot != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Avg cost/shot", color = TextSecondary, fontSize = 12.sp)
                            Text(perShot, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            // Per-roll breakdown
            if (stats.rollCosts.isNotEmpty()) {
                item { SectionTitle("Cost per Roll") }
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .drawBehind { drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())) }
                            .border(1.dp, Border, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header
                        Row(Modifier.fillMaxWidth()) {
                            Text("Film", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("Total", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text("/shot", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        HorizontalDivider(color = Border)
                        stats.rollCosts.forEach { rc ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rc.filmName, color = TextPrimary, fontSize = 13.sp,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    val breakdown = buildList {
                                        if (rc.filmCost > 0) add("film ${currency}%.2f".format(rc.filmCost))
                                        if (rc.devCost > 0) add("dev ${currency}%.2f".format(rc.devCost))
                                        if (rc.scanCost > 0) add("scan ${currency}%.2f".format(rc.scanCost))
                                    }
                                    if (breakdown.isNotEmpty())
                                        Text(breakdown.joinToString(" · "), color = TextTertiary, fontSize = 10.sp)
                                }
                                Text("${currency}%.2f".format(rc.totalCost),
                                    color = Amber, fontSize = 13.sp,
                                    modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text(if (rc.costPerShot > 0) "${currency}%.3f".format(rc.costPerShot) else "—",
                                    color = TextSecondary, fontSize = 12.sp,
                                    modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        if (stats.byFilm.isNotEmpty()) {
            item { SectionTitle("Top Film Stocks") }
            item {
                Column(Modifier.fillMaxWidth().drawBehind {
                        drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    }.border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.byFilm.forEach { (name, count) ->
                        RankRow(name, count, stats.byFilm.maxOfOrNull { it.value } ?: 1, Amber)
                    }
                }
            }
        }

        if (stats.byCam.isNotEmpty()) {
            item { SectionTitle("Top Cameras") }
            item {
                Column(Modifier.fillMaxWidth().drawBehind {
                        drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    }.border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.byCam.forEach { (name, count) ->
                        RankRow(name, count, stats.byCam.maxOfOrNull { it.value } ?: 1, BlueInfo)
                    }
                }
            }
        }

        // Exposure habits — which settings actually get used
        if (stats.byShutter.isNotEmpty()) {
            item { SectionTitle("Most-Used Shutter Speeds") }
            item {
                Column(Modifier.fillMaxWidth().drawBehind {
                        drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    }.border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.byShutter.forEach { (name, count) ->
                        RankRow(name, count, stats.byShutter.maxOfOrNull { it.value } ?: 1, AmberBright)
                    }
                }
            }
        }
        if (stats.byAperture.isNotEmpty()) {
            item { SectionTitle("Most-Used Apertures") }
            item {
                Column(Modifier.fillMaxWidth().drawBehind {
                        drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    }.border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.byAperture.forEach { (name, count) ->
                        RankRow("f/$name", count, stats.byAperture.maxOfOrNull { it.value } ?: 1, OrangeWarn)
                    }
                }
            }
        }

        if (stats.byProc.isNotEmpty()) {
            item { SectionTitle("Dev Processes") }
            item {
                Column(Modifier.fillMaxWidth().drawBehind {
                        drawRoundRect(color = Bg2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    }.border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.byProc.forEach { (name, count) ->
                        RankRow(name, count, stats.byProc.maxOfOrNull { it.value } ?: 1, GreenOk)
                    }
                }
            }
        }

        if (stats.totalRolls == 0) {
            item { EmptyState("No rolls shot yet") }
        }
    }
}

// ─── Films (library) tab ──────────────────────────────────────────────────────

private data class FilmGroup(val name: String, val rolls: List<com.analogvault.data.model.Roll>)

@Composable
fun StatsFilms(vm: MainViewModel, currency: String) {
    val rolls by vm.rolls.collectAsState()
    val films by vm.films.collectAsState()
    var expanded by remember { mutableStateOf<String?>(null) }

    val groups = remember(rolls, films) {
        rolls.groupBy { roll -> films.find { it.id == roll.filmId }?.name ?: "Unknown" }
            .map { (name, rs) -> FilmGroup(name, rs.sortedByDescending { it.startDate }) }
            .sortedByDescending { it.rolls.size }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Film Library", "${groups.size} stocks shot") }
        if (groups.isEmpty()) item { EmptyState("No rolls shot yet") }
        items(groups, key = { it.name }) { g ->
            val shots  = g.rolls.sumOf { it.shots.size }
            val cost   = g.rolls.sumOf { roll ->
                (films.find { it.id == roll.filmId }?.costPerRoll ?: 0.0) + roll.devCost + roll.scanCost
            }
            val thumbs = g.rolls.flatMap { it.shots }
                .mapNotNull { it.photoThumbPath.ifBlank { null } }.take(6)
            val dates  = g.rolls.mapNotNull { it.startDate.ifBlank { null } }.sorted()
            val isOpen = expanded == g.name

            VaultCard(onClick = { expanded = if (isOpen) null else g.name }) {
                Text(g.name, color = TextPrimary, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                TagRow() {
                    VaultTag("${g.rolls.size} roll${if (g.rolls.size != 1) "s" else ""}", textColor = AmberBright)
                    VaultTag("$shots shots")
                    if (cost > 0) VaultTag("${currency}%.2f".format(cost), textColor = GreenOk)
                }
                if (dates.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text("${formatDate(dates.first())} — ${formatDate(dates.last())}",
                        color = TextTertiary, fontSize = 10.sp)
                }
                if (thumbs.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        thumbs.forEach { path ->
                            coil.compose.AsyncImage(
                                model = java.io.File(path), contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
                if (isOpen) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(6.dp))
                    g.rolls.forEach { roll ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDate(roll.startDate), color = TextSecondary, fontSize = 12.sp)
                            Text("${roll.shots.size} shots", color = TextTertiary, fontSize = 11.sp)
                            val (tag, tagColor) = when {
                                roll.scanned   -> "Scanned"   to GreenOk
                                roll.developed -> "Developed" to GreenOk
                                roll.finished  -> "Finished"  to Amber
                                else           -> "Shooting"  to BlueInfo
                            }
                            VaultTag(tag, textColor = tagColor)
                        }
                    }
                }
            }
        }
    }
}

// ─── Photos (wall) tab ────────────────────────────────────────────────────────

private data class PhotoWallItem(val path: String, val filmName: String, val date: String)

@Composable
fun StatsPhotos(vm: MainViewModel) {
    val rolls by vm.rolls.collectAsState()
    val films by vm.films.collectAsState()
    var lightbox by remember { mutableStateOf<PhotoWallItem?>(null) }

    val wall = remember(rolls, films) {
        rolls.flatMap { roll ->
            val filmName = films.find { it.id == roll.filmId }?.name ?: ""
            roll.shots.mapNotNull { s ->
                s.photoThumbPath.ifBlank { null }?.let { PhotoWallItem(it, filmName, s.date) }
            }
        }.sortedByDescending { it.date }
    }

    if (wall.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("No shot photos yet — attach photos when logging shots")
        }
    } else {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(wall.size) { i ->
                val item = wall[i]
                coil.compose.AsyncImage(
                    model = java.io.File(item.path), contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { lightbox = item }
                )
            }
        }
    }

    lightbox?.let { item ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { lightbox = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.95f))
                .clickable { lightbox = null }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    coil.compose.AsyncImage(
                        model = java.io.File(item.path), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                    Text(
                        listOf(item.filmName, item.date).filter { it.isNotBlank() }.joinToString(" · "),
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─── Map tab ──────────────────────────────────────────────────────────────────

@Composable
fun StatsMap(vm: MainViewModel) {
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
        // Stats bar
        Row(
            Modifier.fillMaxWidth().background(Bg2).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$totalWithGps shots with GPS", color = TextSecondary, fontSize = 12.sp)
            if (totalShots > 0) {
                Text("(${(totalWithGps * 100 / totalShots)}%)", color = TextTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            if (totalWithGps == 0) {
                Text("Log shots with GPS to see the map", color = TextTertiary, fontSize = 11.sp)
            }
        }

        if (allMapShots.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No GPS data yet", color = TextTertiary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("When logging shots, tap 📍 to capture location", color = TextTertiary, fontSize = 12.sp)
                }
            }
        } else {
            OsmMapViewMulti(
                mapShots = allMapShots,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
fun StatBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 26.sp)
            Text(label, color = TextTertiary, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MonthBarChart(stats: Stats) {
    if (stats.byMonth.isEmpty()) return
    val maxVal = (stats.byMonth.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
    val amberColor = Amber
    val bgColor = Bg3
    val textColor = TextTertiary

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg2)
            .padding(12.dp)
    ) {
        Column {
            Text("Shots per month", color = TextTertiary, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            // Use Canvas for crisp bars — avoids Compose layout overhead per bar
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                val n = stats.byMonth.size
                if (n == 0) return@Canvas
                val barW = (size.width - (n - 1) * 4.dp.toPx()) / n
                stats.byMonth.forEachIndexed { i, (_, count) ->
                    val frac = count.toFloat() / maxVal
                    val barH = (size.height - 20.dp.toPx()) * frac
                    val x = i * (barW + 4.dp.toPx())
                    val y = size.height - 20.dp.toPx() - barH
                    // Bar background (empty)
                    drawRoundRect(
                        color = bgColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barW, size.height - 20.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                    // Filled bar
                    if (barH > 0) {
                        drawRoundRect(
                            color = amberColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barW, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // Labels row
            Row(Modifier.fillMaxWidth()) {
                stats.byMonth.forEach { (month, count) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            // Show MMM from YYYY-MM
                            month.substringAfter("-").let {
                                listOf("","Jan","Feb","Mar","Apr","May","Jun",
                                    "Jul","Aug","Sep","Oct","Nov","Dec")
                                    .getOrNull(it.toIntOrNull() ?: 0) ?: it
                            },
                            color = textColor, fontSize = 8.sp
                        )
                        Text("$count", color = Amber, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RankRow(name: String, count: Int, max: Int, color: androidx.compose.ui.graphics.Color) {
    val frac = (count.toFloat() / max).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = TextPrimary, fontSize = 12.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", color = color, fontSize = 12.sp)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Bg4)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

package com.analogvault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.Stats
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*

@Composable
fun StatsScreen(vm: MainViewModel) {
    val stats by vm.stats.collectAsState()
    val rolls by vm.rolls.collectAsState()
    val films by vm.films.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Numbers", "Map")

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Bg2, contentColor = Amber,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Amber)
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(t, fontSize = 13.sp) },
                    selectedContentColor = Amber, unselectedContentColor = TextTertiary)
            }
        }

        when (tab) {
            0 -> StatsNumbers(stats)
            1 -> StatsMap(vm)
        }
    }
}

// ─── Numbers tab ──────────────────────────────────────────────────────────────

@Composable
fun StatsNumbers(stats: Stats) {
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

        if (stats.byFilm.isNotEmpty()) {
            item { SectionTitle("Top Film Stocks") }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Bg2).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
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
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Bg2).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.byCam.forEach { (name, count) ->
                        RankRow(name, count, stats.byCam.maxOfOrNull { it.value } ?: 1, BlueInfo)
                    }
                }
            }
        }

        if (stats.byProc.isNotEmpty()) {
            item { SectionTitle("Dev Processes") }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Bg2).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
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
    val maxVal = stats.byMonth.maxOfOrNull { it.value } ?: 1
    val chartH = 100.dp
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(chartH),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom) {
                stats.byMonth.forEach { (_, count) ->
                    val frac = count.toFloat() / maxVal
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom) {
                        Text("$count", color = TextTertiary, fontSize = 6.sp)
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.fillMaxWidth().height(chartH * frac)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(Amber))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                stats.byMonth.forEach { (month, _) ->
                    Text(month.takeLast(2), color = TextTertiary, fontSize = 6.sp,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
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

package com.analogvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.Stats
import com.analogvault.ui.components.EmptyState
import com.analogvault.ui.components.SectionTitle
import com.analogvault.ui.theme.*

@Composable
fun StatsScreen(vm: MainViewModel) {
    val stats by vm.stats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionTitle("Statistics")

        // Top stat boxes row 1
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Total Rolls", stats.totalRolls.toString(), Amber, Modifier.weight(1f))
            StatBox("Developed", stats.developed.toString(), GreenOk, Modifier.weight(1f))
            StatBox("Shooting", stats.shooting.toString(), BlueInfo, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Total Shots", stats.totalShots.toString(), Amber, Modifier.weight(1f))
            StatBox("Avg/Roll", "%.1f".format(stats.avgShots), TextSecondary, Modifier.weight(1f))
            StatBox("Awaiting Dev", stats.finished.toString(), OrangeWarn, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Shots by month chart
        if (stats.byMonth.isNotEmpty()) {
            Text("Shots by Month", color = Amber, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            MonthBarChart(stats)
            Spacer(Modifier.height(20.dp))
        }

        // Film stock ranking
        if (stats.byFilm.isNotEmpty()) {
            Text("Top Film Stocks", color = Amber, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            stats.byFilm.forEach { (name, count) ->
                RankRow(name, count, stats.byFilm.maxOfOrNull { it.value } ?: 1, Amber)
            }
            Spacer(Modifier.height(20.dp))
        }

        // Camera ranking
        if (stats.byCam.isNotEmpty()) {
            Text("Top Cameras", color = Amber, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            stats.byCam.forEach { (name, count) ->
                RankRow(name, count, stats.byCam.maxOfOrNull { it.value } ?: 1, BlueInfo)
            }
            Spacer(Modifier.height(20.dp))
        }

        // Dev process breakdown
        if (stats.byProc.isNotEmpty()) {
            Text("Dev Processes", color = Amber, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            stats.byProc.forEach { (name, count) ->
                RankRow(name, count, stats.byProc.maxOfOrNull { it.value } ?: 1, GreenOk)
            }
        }

        if (stats.totalRolls == 0) {
            EmptyState("No rolls shot yet — load a film to start tracking")
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 28.sp)
            Text(label, color = TextTertiary, fontSize = 9.sp)
        }
    }
}

@Composable
fun MonthBarChart(stats: Stats) {
    val maxVal = stats.byMonth.maxOfOrNull { it.value } ?: 1
    val chartHeight = 120.dp

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(chartHeight),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                stats.byMonth.forEach { (month, count) ->
                    val frac = count.toFloat() / maxVal
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text("$count", color = TextTertiary, fontSize = 7.sp)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight * frac)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(Amber)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stats.byMonth.forEach { (month, _) ->
                    Text(
                        month.takeLast(2), // just the month number
                        color = TextTertiary, fontSize = 7.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun RankRow(name: String, count: Int, max: Int, color: androidx.compose.ui.graphics.Color) {
    val frac = (count.toFloat() / max).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("$count", color = color, fontSize = 12.sp)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Bg4)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(frac).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

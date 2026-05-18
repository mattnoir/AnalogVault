package com.analogvault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.Roll
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.WeatherState
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*

private val tips = listOf(
    "Shoot at box speed first — push/pull only when you have a reason.",
    "Expose for shadows with negative film; it handles highlights better than digital.",
    "Bracket in tricky light: one stop over, one under, one at metered.",
    "Keep unexposed film in a cool, dark place — fridge for long storage.",
    "Check your shutter speeds with a phone audio app before loading expensive film.",
    "Zone System: Zone V is 18% grey. Meter off your palm and open 1 stop.",
    "Stand development in Rodinal 1:100 for 60 min gives great pushed results.",
    "Rate Portra 400 at 200 in mixed light — it has headroom to spare.",
    "The best camera is the one loaded with film.",
    "Expired film: rate it at half box speed per decade past expiry.",
    "Always check the leader is advancing — watch the rewind knob for resistance.",
    "Morning and golden hour give directional light that flatters almost any subject.",
)

// Tab indices matching MainActivity Tab enum order:
// 0=DASH, 1=STASH, 2=ACTIVE, 3=DARK, 4=METER, 5=WEATHER, 6=STATS, 7=BACKUP
private const val TAB_STASH   = 1
private const val TAB_ACTIVE  = 2
private const val TAB_DARK    = 3
private const val TAB_METER   = 4
private const val TAB_WEATHER = 5
private const val TAB_STATS   = 6

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onNavigate: (tabIndex: Int, activeSubTab: Int) -> Unit
) {
    val rolls        by vm.rolls.collectAsState()
    val films        by vm.films.collectAsState()
    val cameras      by vm.cameras.collectAsState()
    val chemicals    by vm.chemicals.collectAsState()
    val weatherState by vm.weatherState.collectAsState()
    val stats        by vm.stats.collectAsState()

    val tip = remember { tips.random() }

    val shooting  = rolls.filter { !it.finished && !it.developed }
    val awaitDev  = rolls.filter { it.finished && !it.developed }
    val developed = rolls.filter { it.developed }

    val exhausted = chemicals.filter { chem ->
        val max = chem.maxRolls.toIntOrNull() ?: return@filter false
        vm.rolledCount(chem).toFloat() / max >= 0.8f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Good to see you.", color = AmberBright, fontSize = 24.sp)
            Text(
                "${rolls.size} roll${if (rolls.size != 1) "s" else ""} logged" +
                if (stats.totalShots > 0) " · ${stats.totalShots} shots" else "",
                color = TextTertiary, fontSize = 12.sp
            )
        }

        // ── Stat row — each taps into the right subtab ────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("In Camera",    shooting.size.toString(),  BlueInfo,
                    Modifier.weight(1f)) { onNavigate(TAB_ACTIVE, 0) }
                MiniStat("Awaiting Dev", awaitDev.size.toString(),
                    if (awaitDev.isNotEmpty()) OrangeWarn else TextTertiary,
                    Modifier.weight(1f)) { onNavigate(TAB_ACTIVE, 1) }
                MiniStat("Developed",    developed.size.toString(), GreenOk,
                    Modifier.weight(1f)) { onNavigate(TAB_ACTIVE, 2) }
            }
        }

        // ── Currently shooting ────────────────────────────────────────────────
        if (shooting.isNotEmpty()) {
            item { DashSectionHeader("Currently Shooting") }
            // Show up to 3, scrollable via LazyColumn parent
            items(shooting.take(3)) { roll ->
                val film  = films.find { it.id == roll.filmId }
                val cam   = cameras.find { it.id == roll.cameraId }
                val total = film?.shots ?: 36
                val pct   = (roll.shots.size.toFloat() / total).coerceIn(0f, 1f)
                DashRollRow(
                    filmName   = film?.name ?: "Unknown Film",
                    camName    = cam?.name ?: "",
                    shotCount  = roll.shots.size,
                    totalShots = total,
                    pct        = pct,
                    onClick    = { onNavigate(TAB_ACTIVE, 0) }
                )
            }
            if (shooting.size > 3) {
                item {
                    TextButton(onClick = { onNavigate(TAB_ACTIVE, 0) },
                        contentPadding = PaddingValues(0.dp)) {
                        Text("+${shooting.size - 3} more rolls →", color = Amber, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Awaiting dev alert ────────────────────────────────────────────────
        if (awaitDev.isNotEmpty()) {
            item {
                DashCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("🧪 ${awaitDev.size} roll${if (awaitDev.size != 1) "s" else ""} awaiting development",
                                color = OrangeWarn, fontSize = 13.sp)
                            Text("Tap to develop + access timers", color = TextTertiary, fontSize = 11.sp)
                        }
                        VaultButton("→", small = true, onClick = { onNavigate(TAB_ACTIVE, 1) })
                    }
                }
            }
        }

        // ── Chemistry alerts ──────────────────────────────────────────────────
        if (exhausted.isNotEmpty()) {
            item {
                DashCard {
                    Text("⚠ Chemistry Alert", color = Amber, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    exhausted.take(2).forEach { chem ->
                        val used = vm.rolledCount(chem)
                        val max  = chem.maxRolls.toIntOrNull() ?: 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(chem.name, color = TextPrimary, fontSize = 12.sp)
                            VaultTag(if (used >= max) "Exhausted" else "Near limit ($used/$max)",
                                textColor = if (used >= max) RedErr else OrangeWarn)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { onNavigate(TAB_DARK, 0) },
                        contentPadding = PaddingValues(0.dp)) {
                        Text("Go to Darkroom →", color = Amber, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Weather snapshot ──────────────────────────────────────────────────
        if (weatherState is WeatherState.Success) {
            val data = (weatherState as WeatherState.Success).data
            item {
                DashCard(onClick = { onNavigate(TAB_WEATHER, 0) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${"%.0f".format(data.main.temp)}°C · ${data.weather.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: ""}",
                                color = TextPrimary, fontSize = 14.sp)
                            Text(data.name, color = TextSecondary, fontSize = 11.sp)
                        }
                        Text("→", color = Amber, fontSize = 16.sp)
                    }
                }
            }
        }

        // ── Quick access — 3 per row, all 6 modules ───────────────────────────
        item {
            DashSectionHeader("Quick Access")
        }
        item {
            val items = listOf(
                Triple("📽 Stash",   Icons.Default.Inventory,   TAB_STASH),
                Triple("🎞 Rolls",   Icons.Default.CameraRoll,  TAB_ACTIVE),
                Triple("🧪 Darkroom",Icons.Default.Science,     TAB_DARK),
                Triple("☀ Meter",   Icons.Default.WbSunny,     TAB_METER),
                Triple("🌤 Weather", Icons.Default.Cloud,       TAB_WEATHER),
                Triple("📊 Stats",   Icons.Default.BarChart,    TAB_STATS),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, _, idx) ->
                            QuickNavBtn(label, Modifier.weight(1f)) { onNavigate(idx, 0) }
                        }
                    }
                }
            }
        }

        // ── Tip ───────────────────────────────────────────────────────────────
        item {
            DashCard {
                Text("💡 $tip", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun DashSectionHeader(title: String) {
    Text(title, color = Amber, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun DashCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun DashRollRow(
    filmName: String, camName: String,
    shotCount: Int, totalShots: Int, pct: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(filmName, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$shotCount/$totalShots", color = TextTertiary, fontSize = 11.sp)
        }
        if (camName.isNotBlank()) Text(camName, color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        VaultProgressBar(pct)
    }
}

@Composable
private fun MiniStat(
    label: String, value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 26.sp)
            Text(label, color = TextTertiary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun QuickNavBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}

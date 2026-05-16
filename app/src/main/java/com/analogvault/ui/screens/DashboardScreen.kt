package com.analogvault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onNavigate: (Int) -> Unit  // index into Tab enum order
) {
    val rolls       by vm.rolls.collectAsState()
    val films       by vm.films.collectAsState()
    val cameras     by vm.cameras.collectAsState()
    val chemicals   by vm.chemicals.collectAsState()
    val weatherState by vm.weatherState.collectAsState()
    val stats       by vm.stats.collectAsState()

    val tip = remember { tips.random() }

    val shooting   = rolls.filter { !it.finished && !it.developed }
    val finished   = rolls.filter { it.finished && !it.developed }
    val exhausted  = chemicals.filter { chem ->
        val max = chem.maxRolls.toIntOrNull() ?: return@filter false
        val used = vm.rolledCount(chem)
        used.toFloat() / max >= 0.8f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text("Good to see you.", color = AmberBright, fontSize = 24.sp)
        Text(
            buildString {
                append("${rolls.size} roll${if (rolls.size != 1) "s" else ""} logged")
                if (stats.totalShots > 0) append(" · ${stats.totalShots} shots")
            },
            color = TextTertiary, fontSize = 12.sp
        )

        // Stat row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("In Camera", shooting.size.toString(), BlueInfo, Modifier.weight(1f),
                onClick = { onNavigate(1) }) // ACTIVE
            MiniStat("Awaiting Dev", finished.size.toString(),
                if (finished.isNotEmpty()) OrangeWarn else TextTertiary,
                Modifier.weight(1f), onClick = { onNavigate(1) })
            MiniStat("Developed", stats.developed.toString(), GreenOk, Modifier.weight(1f))
        }

        // Currently shooting rolls
        if (shooting.isNotEmpty()) {
            DashSection("Currently Shooting") {
                shooting.take(3).forEach { roll ->
                    val film = films.find { it.id == roll.filmId }
                    val cam  = cameras.find { it.id == roll.cameraId }
                    val total = film?.shots ?: 36
                    val pct = (roll.shots.size.toFloat() / total).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(1) }
                            .padding(vertical = 6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(film?.name ?: "Unknown", color = TextPrimary, fontSize = 13.sp)
                            Text("${roll.shots.size}/$total", color = TextTertiary, fontSize = 11.sp)
                        }
                        Text(cam?.name ?: "", color = TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        VaultProgressBar(pct)
                    }
                    if (roll != shooting.take(3).last()) HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 2.dp))
                }
                if (shooting.size > 3) {
                    TextButton(onClick = { onNavigate(1) }) {
                        Text("+${shooting.size - 3} more rolls →", color = Amber, fontSize = 12.sp)
                    }
                }
            }
        }

        // Chemical alerts
        if (exhausted.isNotEmpty()) {
            DashSection("⚠ Chemistry Alert") {
                exhausted.take(3).forEach { chem ->
                    val used = vm.rolledCount(chem)
                    val max  = chem.maxRolls.toIntOrNull() ?: 0
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(chem.name, color = TextPrimary, fontSize = 13.sp)
                        val color = if (used >= max) RedErr else OrangeWarn
                        VaultTag(if (used >= max) "Exhausted" else "Near limit ($used/$max)", textColor = color)
                    }
                }
                TextButton(onClick = { onNavigate(2) }) { // DARK
                    Text("Go to Darkroom →", color = Amber, fontSize = 12.sp)
                }
            }
        }

        // Weather snapshot if loaded
        if (weatherState is WeatherState.Success) {
            val data = (weatherState as WeatherState.Success).data
            DashSection("Weather") {
                Row(Modifier.fillMaxWidth().clickable { onNavigate(4) }, // WEATHER
                    horizontalArrangement = Arrangement.SpaceBetween,
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

        // Quick nav
        DashSection("Quick Access") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickNavBtn("📽 Load Roll",  Modifier.weight(1f)) { onNavigate(1) }
                QuickNavBtn("🧪 Darkroom",  Modifier.weight(1f)) { onNavigate(2) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickNavBtn("☀ Meter",     Modifier.weight(1f)) { onNavigate(3) }
                QuickNavBtn("📊 Stats",    Modifier.weight(1f)) { onNavigate(5) }
            }
        }

        // Tip of the day
        DashSection("Tip") {
            Text("💡 $tip", color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ─── Small components ─────────────────────────────────────────────────────────

@Composable
private fun DashSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(title, color = Amber, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun MiniStat(
    label: String, value: String, color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null
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
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

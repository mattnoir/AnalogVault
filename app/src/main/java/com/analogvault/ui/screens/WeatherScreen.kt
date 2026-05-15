package com.analogvault.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.analogvault.data.network.WeatherResponse
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.WeatherState
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

@Composable
fun WeatherScreen(vm: MainViewModel) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val weatherState by vm.weatherState.collectAsState()
    val owmKey      by vm.owmKey.collectAsState()

    var editingKey by remember { mutableStateOf(false) }
    var keyInput   by remember { mutableStateOf(owmKey) }
    var gpsLoading by remember { mutableStateOf(false) }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            gpsLoading = true
            scope.launch {
                val loc = getWeatherLocation(context)
                gpsLoading = false
                if (loc != null) vm.fetchWeather(loc.first, loc.second)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        SectionTitle("Weather")

        // API Key section
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(Bg3)
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Text("OpenWeatherMap API Key", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                if (editingKey) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = keyInput, onValueChange = { keyInput = it },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber, unfocusedBorderColor = Border,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true,
                            placeholder = { Text("Paste API key…", color = TextTertiary, fontSize = 12.sp) }
                        )
                        VaultButton("Save", small = true, onClick = { vm.saveOwmKey(keyInput); editingKey = false })
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (owmKey.isBlank()) "No key set" else "●●●●●●●●${owmKey.takeLast(4)}",
                            color = if (owmKey.isBlank()) TextTertiary else GreenOk, fontSize = 13.sp
                        )
                        TextButton(onClick = { keyInput = owmKey; editingKey = true }) {
                            Text(if (owmKey.isBlank()) "Add Key" else "Edit", color = Amber, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Free key at openweathermap.org/api", color = TextTertiary, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Fetch button
        VaultButton(
            text = if (gpsLoading) "Getting location…" else "📍 Fetch Current Weather",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                locationPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        )

        Spacer(Modifier.height(16.dp))

        // State display
        when (val state = weatherState) {
            is WeatherState.Loading -> {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            }
            is WeatherState.Error -> {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)).background(RedErr.copy(alpha = 0.1f))
                        .border(1.dp, RedErr.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text("⚠ ${state.message}", color = RedErr, fontSize = 13.sp)
                }
            }
            is WeatherState.Success -> WeatherDisplay(state.data)
            else -> {
                EmptyState("Tap the button to fetch weather at your location")
            }
        }
    }
}

@Composable
fun WeatherDisplay(data: WeatherResponse) {
    val desc = data.weather.firstOrNull()
    Column {
        // Main card
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(Bg3)
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(
                            "${data.name}${if (data.sys?.country != null) ", ${data.sys.country}" else ""}",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        Text("${"%.0f".format(data.main.temp)}°C", color = Amber, fontSize = 48.sp)
                        Text("Feels like ${"%.0f".format(data.main.feels_like)}°C", color = TextSecondary, fontSize = 12.sp)
                    }
                    if (desc != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            AsyncImage(
                                model = "https://openweathermap.org/img/wn/${desc.icon}@2x.png",
                                contentDescription = null,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(desc.description.replaceFirstChar { it.uppercase() },
                                color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Detail grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherStatBox("Humidity", "${data.main.humidity}%", modifier = Modifier.weight(1f))
            WeatherStatBox("Pressure", "${data.main.pressure} hPa", modifier = Modifier.weight(1f))
            WeatherStatBox("Wind", "${"%.1f".format(data.wind.speed)} m/s", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherStatBox("Cloud cover", "${data.clouds.all}%", modifier = Modifier.weight(1f))
            if (data.visibility != null)
                WeatherStatBox("Visibility", "${"%.1f".format(data.visibility / 1000.0)} km", modifier = Modifier.weight(1f))
        }

        // Photography note
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(AmberDark.copy(alpha = 0.15f))
                .border(1.dp, AmberDark, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            val note = buildPhotographyNote(data)
            Text("📸 $note", color = Amber, fontSize = 12.sp)
        }
    }
}

@Composable
fun WeatherStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, color = TextPrimary, fontSize = 16.sp)
            Text(label, color = TextTertiary, fontSize = 9.sp)
        }
    }
}

private fun buildPhotographyNote(data: WeatherResponse): String {
    val clouds = data.clouds.all
    val desc   = data.weather.firstOrNull()?.main ?: ""
    return when {
        desc == "Rain" || desc == "Drizzle" -> "Diffused light — great for portraits. Protect your gear."
        desc == "Snow"                       -> "High reflectivity — expose for shadows, reduce 1–2 stops."
        clouds < 10                          -> "Harsh directional light. Use fill flash or shoot golden hour."
        clouds in 10..40                     -> "Partial cloud. Nice soft directional light."
        clouds in 40..80                     -> "Overcast approaching. Even diffuse light."
        else                                 -> "Full overcast. Flat but consistent exposure for street."
    }
}

private suspend fun getWeatherLocation(context: android.content.Context): Pair<Double, Double>? =
    suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(loc.latitude to loc.longitude)
                    else cont.resume(null)
                }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) { cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }

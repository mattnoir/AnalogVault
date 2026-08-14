package com.analogvault.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.analogvault.data.model.FilmStock
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
    val isMetric    by vm.isMetric.collectAsState()
    val placeName      by vm.placeName.collectAsState()
    val placeResults   by vm.placeResults.collectAsState()
    val placeSearching by vm.placeSearching.collectAsState()

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

        // ── Place ─────────────────────────────────────────────────────────────
        // Pinning a place is the normal case for planning: you check the light
        // for the coast on Thursday from your kitchen. A pinned place overrides
        // the device everywhere weather is used, including Home, so the two can
        // never disagree about which sky they are describing.
        PlacePicker(
            pinnedName = placeName,
            results = placeResults,
            searching = placeSearching,
            hasKey = owmKey.isNotBlank(),
            onSearch = { vm.searchPlaces(it) },
            onPick = { vm.pinPlace(it) },
            onClearResults = { vm.clearPlaceResults() },
            onUseDevice = {
                vm.unpinPlace()
                locationPermLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            },
            gpsLoading = gpsLoading,
        )

        Spacer(Modifier.height(16.dp))

        // State display
        when (val state = weatherState) {
            is WeatherState.Loading -> {
                // Film advancing rather than a spinner: same message, spoken in
                // the language the rest of the app uses.
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    com.analogvault.ui.film.FilmAdvance(label = "Reading the light")
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
            is WeatherState.Success -> {
                    val films by vm.films.collectAsState()
                    WeatherDisplay(state.data, films, isMetric)
                }
            else -> {
                EmptyState(
                    "No forecast yet.",
                    verb = "Fetch the light where you are",
                    onVerb = {
                        locationPermLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                )
            }
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

@Composable
fun WeatherDisplay(data: WeatherResponse, films: List<FilmStock>, isMetric: Boolean = true) {
    val desc = data.weather.firstOrNull()
    val light = remember(data) { analyzeLightConditions(data) }
    val recs  = remember(data, films) { recommendFilms(films, light) }
    // The API is queried with units=metric — convert here for imperial display
    val tempUnit = if (isMetric) "°C" else "°F"
    fun temp(c: Double) = if (isMetric) c else c * 9.0 / 5.0 + 32.0

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
                        Text("${"%.0f".format(temp(data.main.temp))}$tempUnit", color = Amber, fontSize = 48.sp)
                        Text("Feels like ${"%.0f".format(temp(data.main.feels_like))}$tempUnit", color = TextSecondary, fontSize = 12.sp)
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
                // Time-of-day indicator
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VaultTag(light.timeLabel, textColor = light.timeLabelColor)
                    VaultTag(light.lightQuality, textColor = light.lightQualityColor)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Detail grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherStatBox("Humidity", "${data.main.humidity}%", modifier = Modifier.weight(1f))
            WeatherStatBox("Pressure", "${data.main.pressure} hPa", modifier = Modifier.weight(1f))
            WeatherStatBox("Wind",
                if (isMetric) "${"%.1f".format(data.wind.speed)} m/s"
                else "${"%.1f".format(data.wind.speed * 2.237)} mph",
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherStatBox("Cloud cover", "${data.clouds.all}%", modifier = Modifier.weight(1f))
            if (data.visibility != null)
                WeatherStatBox("Visibility",
                    if (isMetric) "${"%.1f".format(data.visibility / 1000.0)} km"
                    else "${"%.1f".format(data.visibility / 1609.34)} mi",
                    modifier = Modifier.weight(1f))
        }

        // Photography conditions note
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(AmberDark.copy(alpha = 0.15f))
                .border(1.dp, AmberDark, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text("📸 ${light.shootingNote}", color = Amber, fontSize = 12.sp)
        }

        // Film recommendations from stash
        if (recs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Best from your stash", color = AmberBright, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            recs.forEachIndexed { i, rec ->
                VaultCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    when (i) { 0 -> "🥇"; 1 -> "🥈"; else -> "🥉" },
                                    fontSize = 14.sp
                                )
                                Text(rec.film.name, color = TextPrimary, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                VaultTag("ISO ${rec.film.iso}")
                                VaultTag(rec.film.type.split(" ").first())
                                if (rec.film.quantity > 0)
                                    VaultTag("×${rec.film.quantity}", textColor = GreenOk)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(rec.reason, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                if (i < recs.lastIndex) Spacer(Modifier.height(6.dp))
            }
        } else if (films.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Bg3)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text("Add film to your stash to get personalised recommendations here.",
                    color = TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

// ─── Place picker ─────────────────────────────────────────────────────────────

/**
 * Search a place by name and pin it, or go back to following the device.
 *
 * Uses OpenWeatherMap's geocoding endpoint, which is on the same free tier and
 * the same key as the forecast itself — no second subscription. It returns
 * several matches on purpose: place names are not unique, and choosing between
 * two Springfields is the user's call.
 */
@Composable
private fun PlacePicker(
    pinnedName: String,
    results: List<com.analogvault.data.network.GeoPlace>,
    searching: Boolean,
    hasKey: Boolean,
    gpsLoading: Boolean,
    onSearch: (String) -> Unit,
    onPick: (com.analogvault.data.network.GeoPlace) -> Unit,
    onClearResults: () -> Unit,
    onUseDevice: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text("LOCATION", color = TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (pinnedName.isBlank()) "Following this device"
            else "Pinned to $pinnedName",
            color = if (pinnedName.isBlank()) TextSecondary else AmberBright,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VaultTextField(
                query, { query = it }, "Town or city",
                modifier = Modifier.weight(1f),
                placeholder = "e.g. Bratislava",
            )
            VaultButton(
                text = if (searching) "…" else "Search",
                small = true,
                enabled = hasKey && query.isNotBlank(),
                onClick = { onSearch(query) },
            )
        }
        if (!hasKey) {
            Spacer(Modifier.height(6.dp))
            Text("Add an API key above to search places — it is the same free key.",
                color = TextTertiary, fontSize = 11.sp)
        }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            results.forEach { place ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg3)
                        .border(1.dp, Border, RoundedCornerShape(6.dp))
                        .clickable { query = ""; onPick(place) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(place.label, color = TextPrimary, fontSize = 13.sp,
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("%.2f, %.2f".format(place.lat, place.lon),
                        color = TextTertiary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            TextButton(onClick = { onClearResults() }) {
                Text("Clear results", color = TextTertiary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultButton(
            text = if (gpsLoading) "Getting location…" else "📍 Use where I am",
            modifier = Modifier.fillMaxWidth(),
            ghost = pinnedName.isBlank(),
            onClick = onUseDevice,
        )
    }
}

// ─── Light condition analysis ─────────────────────────────────────────────────

data class LightConditions(
    val timeLabel: String,
    val timeLabelColor: androidx.compose.ui.graphics.Color,
    val lightQuality: String,
    val lightQualityColor: androidx.compose.ui.graphics.Color,
    val shootingNote: String,
    // Numeric signals for film scoring
    val isGoldenHour: Boolean,
    val isBlueHour: Boolean,
    val isNight: Boolean,
    val isMidday: Boolean,
    val isOvercast: Boolean,
    val isRainy: Boolean,
    val isFoggy: Boolean,
    val evEstimate: Int       // rough EV at ISO 100
)

// Internal rather than private: Home's "light right now" card reuses the same
// EV estimate and light labels, and two implementations of "what is the light
// doing" would drift apart the first time either is tuned.
internal fun analyzeLightConditions(data: WeatherResponse): LightConditions {
    val sunrise = data.sys?.sunrise ?: 0L
    val sunset  = data.sys?.sunset  ?: 0L
    // Use the timestamp from the response (server-side local time already in dt + timezone offset)
    val nowEpoch = if (data.dt > 0) data.dt else System.currentTimeMillis() / 1000
    val localNow = nowEpoch + data.timezone   // seconds since epoch in local timezone

    // Derive local-time hour from epoch (mod day)
    val secondsInDay  = localNow % 86400
    val localHour     = (secondsInDay / 3600).toInt()  // 0–23

    val sunriseLocal  = if (sunrise > 0) ((sunrise + data.timezone) % 86400 / 3600).toInt() else 6
    val sunsetLocal   = if (sunset  > 0) ((sunset  + data.timezone) % 86400 / 3600).toInt() else 20

    // Relative position in the day
    val minBeforeRise = (sunriseLocal * 60) - (localHour * 60 + (secondsInDay % 3600 / 60).toInt())
    val minAfterRise  = -minBeforeRise
    val minBeforeSet  = (sunsetLocal * 60) - (localHour * 60 + (secondsInDay % 3600 / 60).toInt())
    val minAfterSet   = -minBeforeSet

    val isNight      = localHour < sunriseLocal - 1 || localHour > sunsetLocal + 1
    val isBlueHour   = (minAfterSet in 0..30) || (minBeforeRise in 0..30)
    val isGoldenHour = !isBlueHour && (
        (minAfterRise in 0..60) || (minBeforeSet in 0..60)
    )
    val isMidday     = !isGoldenHour && !isBlueHour && !isNight &&
                       localHour in (sunriseLocal + 2)..(sunsetLocal - 2)
    val isHighSun    = isMidday && localHour in 10..15

    val clouds  = data.clouds.all
    val wMain   = data.weather.firstOrNull()?.main ?: ""
    val wId     = data.weather.firstOrNull()?.id ?: 800
    val isRainy = wMain in listOf("Rain","Drizzle","Thunderstorm")
    val isSnowy = wMain == "Snow"
    val isFoggy = wId in 700..799
    val isOvercast = clouds > 75 || isRainy

    // Rough EV estimate at ISO 100 (Sunny 16 rule and adjustments)
    val baseEv = when {
        isNight                      -> 1
        isBlueHour                   -> 5
        isGoldenHour && isOvercast   -> 9
        isGoldenHour                 -> 11
        isHighSun && clouds < 20     -> 15
        isHighSun && clouds < 60     -> 13
        isMidday  && clouds < 20     -> 14
        isMidday  && clouds < 60     -> 12
        isMidday                     -> 10
        else                         -> 10
    }.let { ev ->
        when {
            isRainy -> ev - 2
            isFoggy -> ev - 3
            isSnowy -> ev + 1
            else    -> ev
        }
    }

    val (timeLabel, timeLabelColor) = when {
        isNight     -> "🌙 Night"       to TextTertiary
        isBlueHour  -> "🌆 Blue Hour"   to androidx.compose.ui.graphics.Color(0xFF7EB8D4)
        isGoldenHour-> "🌅 Golden Hour" to Amber
        isHighSun   -> "☀ Midday Sun"   to androidx.compose.ui.graphics.Color(0xFFFFE066)
        else        -> "🌤 Daytime"     to TextSecondary
    }

    val (lightQuality, lightQualityColor) = when {
        isNight      -> "Very low light"   to RedErr
        isFoggy      -> "Foggy/flat"       to TextTertiary
        isRainy      -> "Soft diffused"    to GreenOk
        isBlueHour   -> "Cool even light"  to androidx.compose.ui.graphics.Color(0xFF7EB8D4)
        isGoldenHour -> "Warm directional" to Amber
        isHighSun && clouds < 20 -> "Harsh contrast" to OrangeWarn
        isOvercast   -> "Flat overcast"    to TextSecondary
        clouds < 40  -> "Soft directional" to GreenOk
        else         -> "Mixed cloud"      to TextSecondary
    }

    val shootingNote = when {
        isNight      -> "Very dark — fast film (ISO 800+) or tripod essential. Long exposures for city lights."
        isFoggy      -> "Fog creates a moody low-contrast scene. Any ISO works; bracket your exposures."
        isRainy && isGoldenHour -> "Rare wet golden light — beautiful warm reflections. Protect your gear."
        isRainy      -> "Soft even light perfect for portraits and street. Protect your camera."
        isSnowy      -> "High reflectivity — meter for shadows and reduce 1–2 stops. Cold can slow your shutter."
        isBlueHour   -> "Cool blue twilight — 20–30 min window. Tripod recommended. Slow film renders beautifully."
        isGoldenHour -> "Warm low-angle light — the best hour to shoot. Long shadows, rich colour, low contrast."
        isHighSun && clouds < 20 -> "Harsh midday contrast. Seek shade or embrace the shadows. Fast film or ND filter for bright subjects."
        isHighSun    -> "Partial cloud softens midday harshness. Good light for most subjects."
        isOvercast   -> "Even overcast light — ideal for portraits and street. No harsh shadows, consistent exposure."
        else         -> "Gentle mixed light — forgiving for most film stocks."
    }

    return LightConditions(
        timeLabel, timeLabelColor, lightQuality, lightQualityColor, shootingNote,
        isGoldenHour, isBlueHour, isNight, isHighSun, isOvercast, isRainy, isFoggy, baseEv
    )
}

// ─── Film recommendation engine ───────────────────────────────────────────────

data class FilmRecommendation(val film: FilmStock, val score: Int, val reason: String)

// Internal rather than private: Home's weather card shows the same top pick, and
// two scoring implementations would disagree the first time either is tuned.
internal fun recommendFilms(films: List<FilmStock>, light: LightConditions): List<FilmRecommendation> {
    if (films.isEmpty()) return emptyList()

    // Only recommend stash films with quantity > 0
    val available = films.filter { it.quantity > 0 }
    if (available.isEmpty()) return emptyList()

    val recs = available.map { film ->
        var score = 50
        val reasons = mutableListOf<String>()

        val iso  = film.iso
        val isBW = film.type.contains("Black", ignoreCase = true) ||
                   film.type.contains("B&W",   ignoreCase = true)
        val isSlide  = film.type.contains("Slide", ignoreCase = true) ||
                       film.type.contains("E-6",   ignoreCase = true)
        val isColor  = film.type.contains("Color", ignoreCase = true) ||
                       film.type.contains("C-41",  ignoreCase = true)
        val isInstant = film.type.contains("Instant", ignoreCase = true)

        // ── ISO scoring based on estimated EV ────────────────────────────────
        val idealIso = when {
            light.isNight                           -> 1600
            light.isBlueHour                        -> 400
            light.isGoldenHour && light.isOvercast  -> 400
            light.isGoldenHour                      -> 200
            light.isMidday && !light.isOvercast     -> 100
            light.isOvercast                        -> 400
            else                                    -> 200
        }
        // Score decreases logarithmically with distance from ideal ISO
        val isoRatio = if (iso >= idealIso) iso.toFloat() / idealIso else idealIso.toFloat() / iso
        score += when {
            isoRatio < 1.5f  -> { reasons.add("ISO $iso is ideal for this light"); 20 }
            isoRatio < 2.5f  -> { reasons.add("ISO $iso works well here"); 10 }
            isoRatio < 4f    -> { reasons.add("ISO $iso is usable"); 0 }
            isoRatio < 8f    -> { reasons.add("ISO $iso is a stretch for this light"); -10 }
            else             -> { reasons.add("ISO $iso is quite mismatched"); -20 }
        }

        // ── Film type scoring ─────────────────────────────────────────────────
        when {
            light.isGoldenHour && isColor && !isSlide -> {
                score += 15; reasons.add("Color neg renders golden warmth beautifully")
            }
            light.isGoldenHour && isSlide -> {
                score += 10; reasons.add("Slide film captures saturated golden tones precisely")
            }
            light.isGoldenHour && isBW -> {
                score += 8; reasons.add("B&W accentuates golden-hour shadows and texture")
            }
            light.isOvercast && isBW -> {
                score += 12; reasons.add("Flat overcast light is ideal for B&W contrast control")
            }
            light.isRainy && isBW -> {
                score += 10; reasons.add("Rainy soft light suits B&W street and portraiture")
            }
            light.isRainy && isColor -> {
                score += 8; reasons.add("Color neg handles the muted palette of wet scenes well")
            }
            light.isFoggy && isBW -> {
                score += 15; reasons.add("Fog and B&W is a classic — ethereal, atmospheric")
            }
            light.isFoggy && isColor -> {
                score += 5; reasons.add("Color in fog can be interesting but low saturation")
            }
            light.isNight && isBW -> {
                score += 10; reasons.add("B&W handles pushed night shooting well")
            }
            light.isNight && isColor -> {
                score += 6; reasons.add("Color neg captures artificial light warmth at night")
            }
            light.isMidday && isSlide -> {
                score -= 8; reasons.add("Slide has narrow latitude — harsh midday is risky")
            }
            light.isMidday && !light.isOvercast && isColor -> {
                score += 5; reasons.add("Color neg latitude handles midday contrast")
            }
        }

        // Slide penalty under poor/hard-to-meter conditions
        if (isSlide && (light.isNight || light.isFoggy || light.isRainy)) {
            score -= 10; reasons.add("Slide's narrow latitude is challenging in variable light")
        }

        // Instant penalty at night
        if (isInstant && light.isNight) {
            score -= 15; reasons.add("Instant film struggles in very low light")
        }

        // Expiry bonus/penalty
        if (film.expiryDate.isNotBlank()) {
            val expYear = film.expiryDate.take(4).toIntOrNull() ?: 9999
            val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            when {
                expYear < thisYear - 5 -> { score -= 15; reasons.add("Heavily expired — expect grain and colour shift") }
                expYear < thisYear     -> { score -= 5;  reasons.add("Expired — may show character grain") }
                expYear == thisYear    -> { score += 2;  reasons.add("Fresh stock") }
            }
        }

        val reason = reasons.take(2).joinToString(". ").ifBlank { "Versatile all-rounder" }
        FilmRecommendation(film, score, reason)
    }

    return recs.sortedByDescending { it.score }.take(3)
}

// Internal so Home can fetch its own weather. Weather is no longer a
// destination in the navigation bar, so if this screen were the only thing
// that could ask for a location, Home's "light right now" card would sit empty
// for anyone who never went looking for the screen that feeds it.
internal suspend fun getWeatherLocation(context: android.content.Context): Pair<Double, Double>? =
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

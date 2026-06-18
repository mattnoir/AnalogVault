package com.analogvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.analogvault.data.model.*
import com.analogvault.data.network.WeatherApi
import com.analogvault.data.repo.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: VaultRepository,
    private val weatherApi: WeatherApi
) : ViewModel() {

    val films       = repo.films.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cameras     = repo.cameras.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lenses      = repo.lenses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accessories = repo.accessories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rolls       = repo.rolls.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chemicals   = repo.chemicals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val zoomLevels  = repo.zoomLevels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bulkRolls   = repo.bulkRolls.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Settings
    private val _owmKey = MutableStateFlow("")
    val owmKey: StateFlow<String> = _owmKey.asStateFlow()
    private val _currency = MutableStateFlow("€")
    val currency: StateFlow<String> = _currency.asStateFlow()
    private val _isMetric = MutableStateFlow(true)
    val isMetric: StateFlow<Boolean> = _isMetric.asStateFlow()
    private val _customIsos = MutableStateFlow<List<Int>>(emptyList())
    val customIsos: StateFlow<List<Int>> = _customIsos.asStateFlow()

    init {
        viewModelScope.launch {
            _owmKey.value   = repo.getSetting("owm_key") ?: ""
            _currency.value = repo.getSetting("currency") ?: "€"
            _isMetric.value = (repo.getSetting("is_metric") ?: "true") == "true"
            val raw = repo.getSetting("custom_isos") ?: ""
            _customIsos.value = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            // Seed default zoom levels if empty
            val zooms = repo.zoomLevels.first()
            if (zooms.isEmpty()) {
                listOf(
                    ZoomLevel(uid(), "0.6x", 13),
                    ZoomLevel(uid(), "1x", 23),
                    ZoomLevel(uid(), "3x", 70),
                    ZoomLevel(uid(), "10x", 230)
                ).forEach { repo.upsertZoomLevel(it) }
            }
        }
    }

    fun saveOwmKey(key: String) = viewModelScope.launch {
        _owmKey.value = key; repo.setSetting("owm_key", key)
    }
    fun saveCurrency(c: String) = viewModelScope.launch {
        _currency.value = c; repo.setSetting("currency", c)
    }
    fun saveMetric(m: Boolean) = viewModelScope.launch {
        _isMetric.value = m; repo.setSetting("is_metric", m.toString())
    }
    fun addCustomIso(iso: Int) = viewModelScope.launch {
        val updated = (_customIsos.value + iso).distinct().sorted()
        _customIsos.value = updated
        repo.setSetting("custom_isos", updated.joinToString(","))
    }
    fun removeCustomIso(iso: Int) = viewModelScope.launch {
        val updated = _customIsos.value.filter { it != iso }
        _customIsos.value = updated
        repo.setSetting("custom_isos", updated.joinToString(","))
    }

    // ─── Film Stock CRUD ─────────────────────────────────────────────────────
    fun upsertFilm(f: FilmStock) = viewModelScope.launch { repo.upsertFilm(f) }
    fun deleteFilm(f: FilmStock) = viewModelScope.launch { repo.deleteFilm(f) }

    // ─── Camera CRUD ─────────────────────────────────────────────────────────
    fun upsertCamera(c: Camera) = viewModelScope.launch { repo.upsertCamera(c) }
    fun deleteCamera(c: Camera) = viewModelScope.launch { repo.deleteCamera(c) }

    // ─── Lens CRUD ───────────────────────────────────────────────────────────
    fun upsertLens(l: Lens) = viewModelScope.launch { repo.upsertLens(l) }
    fun deleteLens(l: Lens) = viewModelScope.launch { repo.deleteLens(l) }

    // ─── Accessory CRUD ──────────────────────────────────────────────────────
    fun upsertAccessory(a: Accessory) = viewModelScope.launch { repo.upsertAccessory(a) }
    fun deleteAccessory(a: Accessory) = viewModelScope.launch { repo.deleteAccessory(a) }

    // ─── Roll CRUD ───────────────────────────────────────────────────────────
    fun upsertRoll(r: Roll) = viewModelScope.launch { repo.upsertRoll(r) }
    fun deleteRoll(id: String) = viewModelScope.launch { repo.deleteRollById(id) }

    /** Unload a roll loaded by mistake: return its film to the stash and delete the roll. */
    fun unloadRoll(roll: Roll) = viewModelScope.launch {
        films.value.find { it.id == roll.filmId }?.let { film ->
            repo.upsertFilm(film.copy(quantity = film.quantity + 1))
        }
        repo.deleteRollById(roll.id)
    }

    fun addShot(rollId: String, shot: Shot) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(shots = roll.shots + shot))
    }
    fun updateShot(rollId: String, shot: Shot) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(shots = roll.shots.map { if (it.id == shot.id) shot else it }))
    }
    fun deleteShot(rollId: String, shotId: String) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(shots = roll.shots.filter { it.id != shotId }))
    }
    fun markFinished(rollId: String, finished: Boolean) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(finished = finished))
    }
    fun markDeveloped(rollId: String, devLog: DevLog?, devCost: Double = 0.0, isSelfDev: Boolean = false) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(developed = devLog != null, devLog = devLog, devCost = devCost, isSelfDev = isSelfDev))
    }
    fun markScanned(rollId: String, scanLog: ScanLog?, scanCost: Double = 0.0) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(scanned = scanLog != null, scanLog = scanLog, scanCost = scanCost))
    }

    // ─── Chemical CRUD ───────────────────────────────────────────────────────
    fun upsertChemical(c: Chemical) = viewModelScope.launch { repo.upsertChemical(c) }
    fun deleteChemical(c: Chemical) = viewModelScope.launch { repo.deleteChemical(c) }
    fun setChemicalRolls(chemId: String, count: Int) = viewModelScope.launch {
        val chem = chemicals.value.find { it.id == chemId } ?: return@launch
        repo.upsertChemical(chem.copy(manualRolls = count))
    }

    /** Count developed rolls that used this chemical (match by developer name or just total developed) */
    fun rolledCount(chem: Chemical): Int {
        if (chem.manualRolls >= 0) return chem.manualRolls
        return rolls.value.count { r -> r.developed && (r.devLog?.developer?.contains(chem.name, ignoreCase = true) == true) }
    }

    // ─── Zoom levels ─────────────────────────────────────────────────────────
    fun upsertZoomLevel(z: ZoomLevel) = viewModelScope.launch { repo.upsertZoomLevel(z) }
    fun deleteZoomLevel(z: ZoomLevel) = viewModelScope.launch { repo.deleteZoomLevel(z) }

    // ─── Bulk Rolls ───────────────────────────────────────────────────────────
    fun upsertBulkRoll(b: BulkRoll) = viewModelScope.launch { repo.upsertBulkRoll(b) }
    fun deleteBulkRoll(b: BulkRoll) = viewModelScope.launch { repo.deleteBulkRoll(b) }

    fun cutFromBulk(bulk: BulkRoll, frames: Int, quantity: Int, expiryDate: String) =
        viewModelScope.launch { repo.cutFromBulk(bulk, frames, quantity, expiryDate) }

    // ─── Weather ─────────────────────────────────────────────────────────────
    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val weatherState: StateFlow<WeatherState> = _weatherState

    fun fetchWeather(lat: Double, lon: Double) = viewModelScope.launch {
        val key = _owmKey.value
        if (key.isBlank()) { _weatherState.value = WeatherState.Error("Add OWM API key in Settings"); return@launch }
        _weatherState.value = WeatherState.Loading
        try {
            val result = weatherApi.getCurrentWeather(lat, lon, key)
            _weatherState.value = WeatherState.Success(result)
        } catch (e: Exception) {
            _weatherState.value = WeatherState.Error(e.message ?: "Network error")
        }
    }

    // ─── Stats ───────────────────────────────────────────────────────────────
    val stats: StateFlow<Stats> = combine(rolls, films, cameras, bulkRolls) { r, f, c, b ->
        val totalShots = r.sumOf { it.shots.size }
        val byFilm = r.groupBy { roll -> f.find { it.id == roll.filmId }?.name ?: "Unknown" }
            .mapValues { it.value.size }.entries.sortedByDescending { it.value }.take(8)
        val byCam = r.groupBy { roll -> c.find { it.id == roll.cameraId }?.name ?: "Unknown" }
            .mapValues { it.value.size }.entries.sortedByDescending { it.value }.take(6)
        val byMonth = r.flatMap { it.shots }.filter { it.date.length >= 7 }
            .groupBy { it.date.substring(0, 7) }.mapValues { it.value.size }
            .entries.sortedBy { it.key }.takeLast(12)
        val byProc = r.filter { it.devLog != null }
            .groupBy { it.devLog!!.process.ifBlank { "Unknown" } }
            .mapValues { it.value.size }.entries.sortedByDescending { it.value }
        // Film cost = per-roll film cost of shot rolls (stash films + bulk-cut rolls carry an
        // amortised costPerRoll) PLUS the value of bulk film not yet cut into rolls. Counting
        // only the uncut remainder avoids double-counting frames already cut into stash/rolls.
        val filmRollCost = r.sumOf { roll -> f.find { it.id == roll.filmId }?.costPerRoll ?: 0.0 }
        val bulkRemaining = b.sumOf { bulk ->
            if (bulk.totalCost <= 0.0) 0.0
            else if (bulk.totalFrames > 0)
                bulk.totalCost * (bulk.totalFrames - bulk.usedFrames).coerceAtLeast(0) / bulk.totalFrames
            else bulk.totalCost
        }
        val rollCosts = r.map { roll ->
            val film = f.find { it.id == roll.filmId }
            RollCostSummary(
                rollId    = roll.id,
                filmName  = film?.name ?: "Unknown Film",
                shotCount = roll.shots.size,
                filmCost  = film?.costPerRoll ?: 0.0,
                devCost   = roll.devCost,
                scanCost  = roll.scanCost
            )
        }.filter { it.totalCost > 0.0 }
            .sortedByDescending { it.totalCost }
        Stats(
            totalRolls = r.size,
            developed = r.count { it.developed },
            shooting = r.count { !it.finished && !it.developed },
            finished = r.count { it.finished && !it.developed },
            totalShots = totalShots,
            avgShots = if (r.isNotEmpty()) totalShots.toDouble() / r.size else 0.0,
            byFilm = byFilm,
            byCam = byCam,
            byMonth = byMonth,
            byProc = byProc,
            totalFilmCost = filmRollCost + bulkRemaining,
            totalDevCost  = r.sumOf { it.devCost },
            totalScanCost = r.sumOf { it.scanCost },
            selfDevRolls  = r.count { it.isSelfDev && it.developed },
            labDevRolls   = r.count { !it.isSelfDev && it.developed },
            rollCosts     = rollCosts
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Stats())
}

fun uid() = UUID.randomUUID().toString()

data class RollCostSummary(
    val rollId: String,
    val filmName: String,
    val shotCount: Int,
    val filmCost: Double,
    val devCost: Double,
    val scanCost: Double
) {
    val totalCost get() = filmCost + devCost + scanCost
    val costPerShot get() = if (shotCount > 0 && totalCost > 0.0) totalCost / shotCount else 0.0
}

data class Stats(
    val totalRolls: Int = 0,
    val developed: Int = 0,
    val shooting: Int = 0,
    val finished: Int = 0,
    val totalShots: Int = 0,
    val avgShots: Double = 0.0,
    val byFilm: List<Map.Entry<String, Int>> = emptyList(),
    val byCam: List<Map.Entry<String, Int>> = emptyList(),
    val byMonth: List<Map.Entry<String, Int>> = emptyList(),
    val byProc: List<Map.Entry<String, Int>> = emptyList(),
    // Cost totals
    val totalFilmCost: Double = 0.0,
    val totalDevCost: Double = 0.0,
    val totalScanCost: Double = 0.0,
    val selfDevRolls: Int = 0,
    val labDevRolls: Int = 0,
    // Per-roll breakdown (only rolls that have any cost data)
    val rollCosts: List<RollCostSummary> = emptyList()
)

sealed class WeatherState {
    object Idle : WeatherState()
    object Loading : WeatherState()
    data class Success(val data: com.analogvault.data.network.WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

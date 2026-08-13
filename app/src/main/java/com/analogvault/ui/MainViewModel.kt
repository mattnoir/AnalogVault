package com.analogvault.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.analogvault.data.export.ExportResult
import com.analogvault.data.export.RollExporter
import com.analogvault.data.model.*
import com.analogvault.data.network.WeatherApi
import com.analogvault.data.repo.VaultRepository
import com.analogvault.ui.screens.DevTimer
import com.analogvault.ui.screens.MeterReading
import com.analogvault.ui.screens.formatLatLon
import com.analogvault.ui.screens.formatWeatherString
import com.analogvault.ui.screens.getCurrentLatLon
import com.analogvault.util.Constants
import com.analogvault.util.Exposure
import com.analogvault.util.legacyPhotoCacheDir
import com.analogvault.util.photoDir
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: VaultRepository,
    private val weatherApi: WeatherApi,
    private val rollExporter: RollExporter
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
    private val _highRefresh = MutableStateFlow(true)
    val highRefresh: StateFlow<Boolean> = _highRefresh.asStateFlow()

    // Meter settings — persisted so the per-device calibration (and last-used
    // ISO/shutter/metering) survive app restarts. Calibration is stored as a
    // count of third-stops (classic camera increments).
    private val _meterCalibThirds = MutableStateFlow(0)
    val meterCalibThirds: StateFlow<Int> = _meterCalibThirds.asStateFlow()
    private val _meterIso = MutableStateFlow(400)
    val meterIso: StateFlow<Int> = _meterIso.asStateFlow()
    private val _meterShutter = MutableStateFlow("1/125")
    val meterShutter: StateFlow<String> = _meterShutter.asStateFlow()
    private val _meterMetering = MutableStateFlow(Constants.METERING_TYPES[0])
    val meterMetering: StateFlow<String> = _meterMetering.asStateFlow()
    // The priority-mode selector and its fixed aperture are gone: the exposure
    // ladder shows every equivalent pair and lets you pick one, which is both
    // priorities at once. Their settings rows are left in the database rather
    // than deleted, so downgrading an install does not lose them.
    private val _recipFilm = MutableStateFlow("Other B&W (generic)")
    val recipFilm: StateFlow<String> = _recipFilm.asStateFlow()

    // ── Meter live state — hoisted here so readings, EV lock and zone marks
    //    survive tab navigation (previously reset on every visit) ────────────
    private val _meterReading = MutableStateFlow<MeterReading?>(null)
    val meterReading: StateFlow<MeterReading?> = _meterReading.asStateFlow()
    private val _meterEvLocked = MutableStateFlow(false)
    val meterEvLocked: StateFlow<Boolean> = _meterEvLocked.asStateFlow()
    private val _meterManualEv = MutableStateFlow(12.0)
    val meterManualEv: StateFlow<Double> = _meterManualEv.asStateFlow()
    private val _meterZoneEnabled = MutableStateFlow(false)
    val meterZoneEnabled: StateFlow<Boolean> = _meterZoneEnabled.asStateFlow()
    private val _meterZone = MutableStateFlow(5)
    val meterZone: StateFlow<Int> = _meterZone.asStateFlow()
    private val _meterShadowEv = MutableStateFlow<Double?>(null)
    val meterShadowEv: StateFlow<Double?> = _meterShadowEv.asStateFlow()
    private val _meterHighlightEv = MutableStateFlow<Double?>(null)
    val meterHighlightEv: StateFlow<Double?> = _meterHighlightEv.asStateFlow()
    /**
     * The roll the meter is metering for. It supplies the film speed, the gear
     * to clamp against and the frame the commit bar logs to.
     *
     * Deliberately not persisted: which roll is in your hands is a fact about
     * right now, and a stale value restored a week later would clamp the meter
     * against a camera sitting in a drawer. Null means "no roll" — the meter
     * still works, it just stops clamping and hands its reading to the shot
     * sheet instead of logging a frame.
     */
    private val _meterRollId = MutableStateFlow<String?>(null)
    val meterRollId: StateFlow<String?> = _meterRollId.asStateFlow()
    fun setMeterRoll(rollId: String?) { _meterRollId.value = rollId }

    /** Called from the Camera2 capture callback (already throttled there). */
    fun onMeterReading(iso: Int, shutterSec: Double, aperture: Double) {
        if (_meterEvLocked.value) return
        _meterReading.value = MeterReading(
            sceneEV = Exposure.evFromSensor(iso, shutterSec, aperture).coerceIn(-6.0, 24.0),
            sensorIso = iso, sensorShutterSec = shutterSec, sensorAperture = aperture
        )
    }
    fun clearMeterReading() { _meterReading.value = null }
    fun setMeterManualEv(ev: Double) { _meterManualEv.value = ev }
    fun setMeterLock(locked: Boolean, evAtLock: Double? = null) {
        _meterEvLocked.value = locked
        if (locked && evAtLock != null) _meterManualEv.value = evAtLock
    }
    fun setMeterZoneEnabled(on: Boolean) { _meterZoneEnabled.value = on }
    fun setMeterZone(zone: Int) { _meterZone.value = zone }
    fun markMeterShadow(ev: Double) { _meterShadowEv.value = ev }
    fun markMeterHighlight(ev: Double) { _meterHighlightEv.value = ev }
    fun clearMeterMarks() { _meterShadowEv.value = null; _meterHighlightEv.value = null }

    init {
        viewModelScope.launch(Dispatchers.IO) { migratePhotosFromCache() }
        viewModelScope.launch {
            _owmKey.value   = repo.getSetting("owm_key") ?: ""
            _currency.value = repo.getSetting("currency") ?: "€"
            _isMetric.value = (repo.getSetting("is_metric") ?: "true") == "true"
            _highRefresh.value = (repo.getSetting("high_refresh") ?: "true") == "true"
            _agitationCues.value = (repo.getSetting("agitation_cues") ?: "true") == "true"
            _remindersEnabled.value   = repo.getSetting("reminders_enabled") == "true"
            _remindExpiry.value       = (repo.getSetting("remind_expiry") ?: "true") == "true"
            _remindUndeveloped.value  = (repo.getSetting("remind_undeveloped") ?: "true") == "true"
            _remindChemicals.value    = (repo.getSetting("remind_chemicals") ?: "true") == "true"
            // Idempotent (KEEP policy) — re-ensures the periodic work exists
            if (_remindersEnabled.value) com.analogvault.work.Reminders.schedule(appContext)
            _meterCalibThirds.value = repo.getSetting("meter_calib_thirds")?.toIntOrNull() ?: 0
            _meterIso.value      = repo.getSetting("meter_iso")?.toIntOrNull() ?: 400
            _meterShutter.value  = repo.getSetting("meter_shutter") ?: "1/125"
            _meterMetering.value = Constants.normaliseMetering(repo.getSetting("meter_metering"))
            _recipFilm.value     = repo.getSetting("recip_film") ?: "Other B&W (generic)"
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
    fun saveHighRefresh(on: Boolean) = viewModelScope.launch {
        _highRefresh.value = on; repo.setSetting("high_refresh", on.toString())
    }
    fun saveMeterCalibThirds(thirds: Int) = viewModelScope.launch {
        _meterCalibThirds.value = thirds; repo.setSetting("meter_calib_thirds", thirds.toString())
    }
    fun saveMeterIso(iso: Int) = viewModelScope.launch {
        _meterIso.value = iso; repo.setSetting("meter_iso", iso.toString())
    }
    fun saveMeterShutter(s: String) = viewModelScope.launch {
        _meterShutter.value = s; repo.setSetting("meter_shutter", s)
    }
    fun saveMeterMetering(m: String) = viewModelScope.launch {
        _meterMetering.value = m; repo.setSetting("meter_metering", m)
    }
    fun saveRecipFilm(name: String) = viewModelScope.launch {
        _recipFilm.value = name; repo.setSetting("recip_film", name)
    }

    /**
     * One-time migration: builds ≤ 0.4.0 stored shot photos in cacheDir, which
     * the OS may purge under storage pressure (silent photo loss). Move them to
     * filesDir and rewrite the stored paths.
     */
    private suspend fun migratePhotosFromCache() {
        try {
            val legacy = legacyPhotoCacheDir(appContext)
            val files = legacy.listFiles() ?: return
            if (files.isEmpty()) { legacy.delete(); return }
            val dest = photoDir(appContext)
            val moved = mutableMapOf<String, String>() // old abs path → new abs path
            for (f in files) {
                val target = File(dest, f.name)
                val ok = f.renameTo(target) ||
                    runCatching { f.copyTo(target, overwrite = true); f.delete() }.isSuccess
                if (ok) moved[f.absolutePath] = target.absolutePath
            }
            if (moved.isNotEmpty()) {
                repo.rolls.first().forEach { roll ->
                    var changed = false
                    val newShots = roll.shots.map { s ->
                        moved[s.photoThumbPath]?.let { changed = true; s.copy(photoThumbPath = it) } ?: s
                    }
                    if (changed) repo.upsertRoll(roll.copy(shots = newShots))
                }
            }
            legacy.delete()
        } catch (_: Exception) {
            // never block startup over a housekeeping move; retried next launch
        }
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

    /**
     * One-tap frame log: adds a shot immediately with sensible defaults (last
     * shot's exposure, now, cached weather) so the frame counter keeps up with
     * shooting; GPS is filled in asynchronously afterwards if permission is
     * already granted (never prompts). Details can be edited later.
     *
     * The meter passes the exposure it just solved; everything else still comes
     * from the roll, so committing a reading and tapping a frame on Home write
     * the same shape of shot.
     */
    fun quickLogShot(
        rollId: String,
        shutter: String? = null,
        aperture: String? = null,
        iso: String? = null,
    ) = viewModelScope.launch {
        val roll = repo.rolls.first().find { it.id == rollId } ?: return@launch
        if (roll.finished || roll.developed) return@launch
        val last = roll.shots.lastOrNull()
        val film = films.value.find { it.id == roll.filmId }
        val shot = Shot(
            id       = uid(),
            shutter  = shutter ?: last?.shutter ?: "",
            aperture = aperture ?: last?.aperture ?: "",
            iso      = iso ?: last?.iso ?: roll.pushIso.ifBlank { film?.iso?.toString() ?: "" },
            lens     = last?.lens ?: lenses.value.find { it.id == roll.cameraLensId }?.name.orEmpty(),
            weather  = (weatherState.value as? WeatherState.Success)?.data
                ?.let { formatWeatherString(it, isMetric.value) } ?: "",
            date     = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date())
        )
        repo.upsertRoll(roll.copy(shots = roll.shots + shot))

        if (hasLocationPermission()) {
            getCurrentLatLon(appContext)?.let { (lat, lon) ->
                // Re-read: the roll may have changed while waiting for the fix
                val fresh = repo.rolls.first().find { it.id == rollId } ?: return@launch
                repo.upsertRoll(fresh.copy(shots = fresh.shots.map {
                    if (it.id == shot.id) it.copy(location = formatLatLon(lat, lon)) else it
                }))
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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

    // ─── Roll exports ────────────────────────────────────────────────────────

    fun exportRollCsv(uri: android.net.Uri, roll: Roll) = viewModelScope.launch {
        val film = films.value.find { it.id == roll.filmId }
        val cam  = cameras.value.find { it.id == roll.cameraId }
        toastExport(rollExporter.writeCsv(appContext, uri, roll, film, cam))
    }

    fun exportRollPdf(uri: android.net.Uri, roll: Roll) = viewModelScope.launch {
        val film = films.value.find { it.id == roll.filmId }
        val cam  = cameras.value.find { it.id == roll.cameraId }
        toastExport(rollExporter.writePdf(appContext, uri, roll, film, cam))
    }

    private fun toastExport(res: ExportResult) {
        val msg = when (res) { is ExportResult.Success -> res.message; is ExportResult.Error -> res.message }
        android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    // ─── Darkroom timer ──────────────────────────────────────────────────────
    // Hoisted here so a running development timer survives tab navigation, and
    // computed from the wall clock (elapsedRealtime end-time) so it can neither
    // drift nor stall while the device sleeps — delay()-tick loops do both.

    private val _timerState = MutableStateFlow<DarkroomTimerState?>(null)
    val timerState: StateFlow<DarkroomTimerState?> = _timerState.asStateFlow()
    private var timerJob: Job? = null
    private var timerEndElapsedMs = 0L
    private var lastAgitationCueSec = -1

    // Classic agitation rhythm: initial agitation, then ~10 s at each minute mark
    private val _agitationCues = MutableStateFlow(true)
    val agitationCues: StateFlow<Boolean> = _agitationCues.asStateFlow()
    fun saveAgitationCues(on: Boolean) = viewModelScope.launch {
        _agitationCues.value = on; repo.setSetting("agitation_cues", on.toString())
    }

    // ─── Reminders (daily WorkManager check) ─────────────────────────────────
    private val _remindersEnabled = MutableStateFlow(false)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()
    private val _remindExpiry = MutableStateFlow(true)
    val remindExpiry: StateFlow<Boolean> = _remindExpiry.asStateFlow()
    private val _remindUndeveloped = MutableStateFlow(true)
    val remindUndeveloped: StateFlow<Boolean> = _remindUndeveloped.asStateFlow()
    private val _remindChemicals = MutableStateFlow(true)
    val remindChemicals: StateFlow<Boolean> = _remindChemicals.asStateFlow()

    fun saveRemindersEnabled(on: Boolean) = viewModelScope.launch {
        _remindersEnabled.value = on
        repo.setSetting("reminders_enabled", on.toString())
        if (on) com.analogvault.work.Reminders.schedule(appContext)
        else com.analogvault.work.Reminders.cancel(appContext)
    }
    fun saveRemindExpiry(on: Boolean) = viewModelScope.launch {
        _remindExpiry.value = on; repo.setSetting("remind_expiry", on.toString())
    }
    fun saveRemindUndeveloped(on: Boolean) = viewModelScope.launch {
        _remindUndeveloped.value = on; repo.setSetting("remind_undeveloped", on.toString())
    }
    fun saveRemindChemicals(on: Boolean) = viewModelScope.launch {
        _remindChemicals.value = on; repo.setSetting("remind_chemicals", on.toString())
    }

    fun startTimer(timer: DevTimer) {
        timerJob?.cancel()
        lastAgitationCueSec = -1
        _timerState.value = DarkroomTimerState(
            timer = timer, currentStep = 0,
            secondsLeft = timer.steps.firstOrNull()?.durationSec ?: 0
        )
    }

    fun stopTimer() {
        timerJob?.cancel()
        _timerState.value = null
    }

    fun toggleTimerRunning() {
        val s = _timerState.value ?: return
        if (s.finished) return
        if (s.running) {
            timerJob?.cancel()
            _timerState.value = s.copy(running = false, secondsLeft = timerRemainingSeconds())
        } else {
            timerEndElapsedMs = SystemClock.elapsedRealtime() + s.secondsLeft * 1000L
            _timerState.value = s.copy(running = true)
            startTimerTick()
        }
    }

    fun resetTimerStep() {
        val s = _timerState.value ?: return
        timerJob?.cancel()
        lastAgitationCueSec = -1
        _timerState.value = s.copy(
            secondsLeft = s.timer.steps[s.currentStep].durationSec,
            running = false, finished = false
        )
    }

    fun skipTimerStep() {
        val s = _timerState.value ?: return
        if (s.currentStep >= s.timer.steps.lastIndex) return
        timerJob?.cancel()
        lastAgitationCueSec = -1
        val next = s.currentStep + 1
        _timerState.value = s.copy(
            currentStep = next, secondsLeft = s.timer.steps[next].durationSec, running = false
        )
    }

    private fun timerRemainingSeconds(): Int =
        (((timerEndElapsedMs - SystemClock.elapsedRealtime()) + 999) / 1000).toInt().coerceAtLeast(0)

    private fun startTimerTick() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val s = _timerState.value ?: return@launch
                if (!s.running) return@launch
                val left = timerRemainingSeconds()
                if (left <= 0) { onTimerStepFinished(); return@launch }
                // Agitation cue at each whole minute of elapsed step time
                // (skip when the step is about to finish — that gets its own alert)
                val elapsed = s.timer.steps[s.currentStep].durationSec - left
                if (_agitationCues.value && elapsed > 0 && elapsed % 60 == 0 &&
                    lastAgitationCueSec != elapsed && left > 5
                ) {
                    lastAgitationCueSec = elapsed
                    vibrateAgitation()
                }
                if (left != s.secondsLeft) _timerState.value = s.copy(secondsLeft = left)
                delay(200)
            }
        }
    }

    private fun onTimerStepFinished() {
        val s = _timerState.value ?: return
        vibrateStepDone()
        lastAgitationCueSec = -1
        if (s.currentStep < s.timer.steps.lastIndex) {
            val next = s.currentStep + 1
            // Auto-pause between steps so the user sees the transition
            _timerState.value = s.copy(
                currentStep = next, secondsLeft = s.timer.steps[next].durationSec, running = false
            )
        } else {
            _timerState.value = s.copy(secondsLeft = 0, running = false, finished = true)
        }
    }

    private fun vibrateStepDone() = vibratePattern(longArrayOf(0, 300, 150, 300))

    /** Shorter, distinct pulse for agitation cues. */
    private fun vibrateAgitation() = vibratePattern(longArrayOf(0, 120, 80, 120))

    private fun vibratePattern(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) { /* no vibrator — timer still advances */ }
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

data class DarkroomTimerState(
    val timer: DevTimer,
    val currentStep: Int = 0,
    val secondsLeft: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false
)

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

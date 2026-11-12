package com.analogvault.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.analogvault.data.camera.CameraOptics
import com.analogvault.data.camera.CameraRig
import com.analogvault.data.export.ExportResult
import com.analogvault.data.export.RollExporter
import com.analogvault.data.model.*
import com.analogvault.data.network.GeoPlace
import com.analogvault.data.network.WeatherApi
import com.analogvault.data.repo.VaultRepository
import com.analogvault.ui.screens.DEFAULT_HOME_ORDER
import com.analogvault.ui.screens.DevTimer
import com.analogvault.ui.screens.SavedAgitation
import com.analogvault.ui.screens.decodeAgitations
import com.analogvault.ui.screens.decodeTimers
import com.analogvault.ui.screens.encodeAgitations
import com.analogvault.ui.screens.encodeTimers
import com.analogvault.ui.screens.HomeSection
import com.analogvault.ui.screens.parseHomeHidden
import com.analogvault.ui.screens.parseHomeShown
import com.analogvault.ui.theme.Palette
import com.analogvault.ui.screens.parseHomeOrder
import com.analogvault.ui.screens.MeterReading
import com.analogvault.ui.screens.formatLatLon
import com.analogvault.ui.screens.formatWeatherString
import com.analogvault.ui.screens.getCurrentLatLon
import com.analogvault.util.AchCounters
import com.analogvault.util.AchProgress
import com.analogvault.util.AchRecord
import com.analogvault.util.AchSnapshot
import com.analogvault.util.Achievement
import com.analogvault.util.AchievementRecords
import com.analogvault.util.Achievements
import com.analogvault.util.Constants
import com.analogvault.util.EdgePrint
import com.analogvault.util.FrameNumberStyle
import com.analogvault.util.GrainStock
import com.analogvault.util.IrisStyle
import com.analogvault.util.Exposure
import com.analogvault.util.legacyPhotoCacheDir
import com.analogvault.util.photoDir
import com.analogvault.util.saveShotPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    // Frames a bulk loader fogs per roll while it is being loaded; pre-fills
    // the "Buffer frames" field when cutting rolls from a bulk canister.
    private val _bulkBufferFrames = MutableStateFlow(0)
    val bulkBufferFrames: StateFlow<Int> = _bulkBufferFrames.asStateFlow()

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
    // Which half of the exposure the meter holds still while the light moves.
    // Aperture priority holds the lens wide open by default and lets the
    // shutter follow; shutter priority holds the speed last picked off the
    // ladder; manual holds both and only reports how far off the light they
    // are. Stored under its own key: the 0.5.0 selector's `meter_mode` and
    // `meter_aperture` rows are still in older databases, and reading them back
    // would resurrect a choice made against a different screen.
    private val _meterPriority = MutableStateFlow(MeterPriority.APERTURE)
    val meterPriority: StateFlow<MeterPriority> = _meterPriority.asStateFlow()
    // Contrast of the meter's black-and-white preview, in tenths (13 = 1.3×).
    // A touch above flat by default: the point of the grey preview is to see
    // where the tones separate, and a straight desaturation looks muddier than
    // the print will.
    private val _meterMonoContrast = MutableStateFlow(Constants.MONO_CONTRAST_DEFAULT)
    val meterMonoContrast: StateFlow<Int> = _meterMonoContrast.asStateFlow()
    private val _recipFilm = MutableStateFlow("Other B&W (generic)")
    val recipFilm: StateFlow<String> = _recipFilm.asStateFlow()
    // The shape the meter's viewfinder crops the preview to: a METER_FRAMES
    // key, AUTO (the roll's camera) or FILL. Vertical stands the long side up.
    private val _meterFrame = MutableStateFlow(Constants.METER_FRAME_AUTO)
    val meterFrame: StateFlow<String> = _meterFrame.asStateFlow()
    private val _meterFrameVertical = MutableStateFlow(false)
    val meterFrameVertical: StateFlow<Boolean> = _meterFrameVertical.asStateFlow()
    // Thirds grid over the meter's preview. On unless switched off.
    // What the back cameras say about their lenses; null until read, or when
    // there is no back camera. Drives the meter's zoom and focal presets.
    private val _cameraRig = MutableStateFlow<CameraRig?>(null)
    val cameraRig: StateFlow<CameraRig?> = _cameraRig.asStateFlow()
    private val _cameraOptics = _cameraRig.map { it?.main }
    val cameraOptics: StateFlow<CameraOptics?> =
        _cameraOptics.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _cameraOpticsChecked = MutableStateFlow(false)
    /**
     * Some preset's focal length is the 23 mm guess rather than the phone's
     * lens data or a value typed in by hand.
     */
    val zoomFocalsUnverified: StateFlow<Boolean> =
        combine(zoomLevels, _cameraOptics, _cameraOpticsChecked) { levels, optics, checked ->
            checked && optics?.equivMm == null && levels.any { !it.manual }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _meterGrid = MutableStateFlow(true)
    val meterGrid: StateFlow<Boolean> = _meterGrid.asStateFlow()

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

    /**
     * Lens swapped onto a roll's camera for metering only, keyed by roll id.
     *
     * The meter clamps and opens wide against this lens, but the roll keeps the
     * lens it was loaded with and frames logged from the meter record that one.
     * Kept for the session so a swap survives a trip to another tab; not
     * persisted, for the same reason the roll choice is not.
     */
    private val _meterLensOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val meterLensOverrides: StateFlow<Map<String, String>> = _meterLensOverrides.asStateFlow()
    // The preview goes grey by itself on a black-and-white roll. Switching it
    // either way is remembered per roll (null key: no roll) until the app
    // closes, like a lens swap; a choice matching the film's default is dropped.
    private val _meterMonoOverrides = MutableStateFlow<Map<String?, Boolean>>(emptyMap())
    val meterMonoOverrides: StateFlow<Map<String?, Boolean>> = _meterMonoOverrides.asStateFlow()
    fun setMeterMono(rollId: String?, on: Boolean, filmDefault: Boolean) {
        _meterMonoOverrides.value =
            if (on == filmDefault) _meterMonoOverrides.value - rollId
            else _meterMonoOverrides.value + (rollId to on)
    }

    /** Null [lensId] goes back to the roll's own lens. */
    fun setMeterLens(rollId: String, lensId: String?) {
        _meterLensOverrides.value =
            if (lensId == null) _meterLensOverrides.value - rollId
            else _meterLensOverrides.value + (rollId to lensId)
    }

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
        // Rolls loaded before the frame count followed the camera still say 12.
        viewModelScope.launch { repo.refitRollFrames() }
        viewModelScope.launch {
            // The palette first, and nothing before it.
            //
            // These three decide what colour the app is, and the UI waits on
            // themeReady before it draws anything (see VaultActivity). Read
            // anywhere further down this block and the first frame is the
            // default scheme, with the real one arriving a beat later — which
            // is exactly what the amber theme looked like: a second of neon,
            // then a swap.
            _safelight.value = (repo.getSetting("safelight") ?: "false") == "true"
            // The badges are read here too, before the first frame: a palette
            // a secret unlocked has to be known as unlocked before it is worn,
            // or the app would open in Dye Layer and then change its coat.
            val records = AchievementRecords.decode(repo.getSetting(AchievementRecords.SETTING))
            applyLook(LookSettings(
                palette = repo.getSetting("palette"),
                legacyAmber = repo.getSetting("legacy_amber"),
                grain = repo.getSetting("grain"),
                iris = repo.getSetting("iris"),
                edgeText = repo.getSetting("edge_text"),
                edgeFrameStyle = repo.getSetting("edge_frame_style"),
            ), records)
            _saturation.value = repo.getSetting("saturation")?.toFloatOrNull() ?: 1f
            _themeReady.value = true

            _owmKey.value   = repo.getSetting("owm_key") ?: ""
            _currency.value = repo.getSetting("currency") ?: "€"
            _isMetric.value = (repo.getSetting("is_metric") ?: "true") == "true"
            _highRefresh.value = (repo.getSetting("high_refresh") ?: "true") == "true"
            _bulkBufferFrames.value = repo.getSetting("bulk_buffer_frames")?.toIntOrNull()
                ?.coerceAtLeast(0) ?: 0
            _agitationCues.value = (repo.getSetting("agitation_cues") ?: "true") == "true"
            _devTempC.value = repo.getSetting("dev_temp_c")?.toDoubleOrNull() ?: 20.0
            _homeOrder.value  = parseHomeOrder(repo.getSetting("home_order"))
            homeShown = parseHomeShown(repo.getSetting("home_shown"))
            _homeHidden.value = parseHomeHidden(repo.getSetting("home_hidden"), homeShown)
            _placeName.value = repo.getSetting("place_name").orEmpty()
            placeLat = repo.getSetting("place_lat")?.toDoubleOrNull()
            placeLon = repo.getSetting("place_lon")?.toDoubleOrNull()
            // Pinned places do not need the device, so the fetch can start here
            // rather than waiting for a screen to ask for a location permission.
            pinnedLatLon()?.let { (la, lo) -> fetchWeather(la, lo) }
            _achNotify.value          = (repo.getSetting("ach_notify") ?: "true") == "true"
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
            _meterPriority.value = MeterPriority.fromKey(repo.getSetting("meter_priority"))
            _meterMonoContrast.value = repo.getSetting("meter_mono_contrast")?.toIntOrNull()
                ?.coerceIn(Constants.MONO_CONTRAST_RANGE) ?: Constants.MONO_CONTRAST_DEFAULT
            _recipFilm.value     = repo.getSetting("recip_film") ?: "Other B&W (generic)"
            _meterFrame.value    = Constants.normaliseMeterFrame(repo.getSetting("meter_frame"))
            _meterFrameVertical.value = repo.getSetting("meter_frame_vertical") == "true"
            _meterGrid.value     = repo.getSetting("meter_grid") != "false"
            val raw = repo.getSetting("custom_isos") ?: ""
            _customIsos.value = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            val rig = withContext(Dispatchers.Default) { CameraRig.detect(appContext) }
            _cameraRig.value = rig
            _cameraOpticsChecked.value = true
            // Seed default zoom levels if empty — the ratios these cameras reach
            val zooms = repo.zoomLevels.first()
            if (zooms.isEmpty()) {
                defaultZoomRatios(rig).forEach { r ->
                    repo.upsertZoomLevel(zoomLevelFor(uid(), "", r, null, rig?.main))
                }
            }
            keepZoomLevelsInStep()
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
    fun saveMeterPriority(p: MeterPriority) = viewModelScope.launch {
        _meterPriority.value = p
        repo.setSetting("meter_priority", p.key)
    }
    fun saveMeterMonoContrast(tenths: Int) = viewModelScope.launch {
        _meterMonoContrast.value = tenths; repo.setSetting("meter_mono_contrast", tenths.toString())
    }
    fun saveRecipFilm(name: String) = viewModelScope.launch {
        _recipFilm.value = name; repo.setSetting("recip_film", name)
    }
    fun saveMeterFrame(frame: String) = viewModelScope.launch {
        _meterFrame.value = frame; repo.setSetting("meter_frame", frame)
    }
    fun saveMeterFrameVertical(on: Boolean) = viewModelScope.launch {
        _meterFrameVertical.value = on; repo.setSetting("meter_frame_vertical", on.toString())
    }
    fun saveMeterGrid(on: Boolean) = viewModelScope.launch {
        _meterGrid.value = on; repo.setSetting("meter_grid", on.toString())
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
    fun upsertCamera(c: Camera) = viewModelScope.launch {
        repo.upsertCamera(c)
        // A changed shooting format re-counts the 120 rolls already in this body.
        repo.refitRollFrames(c.id)
    }
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
        /** What the meter's viewfinder showed when the frame was logged. */
        photo: android.graphics.Bitmap? = null,
        /**
         * How the meter read the frame, for the achievements that ask about
         * it; null when the frame was not logged from the meter.
         */
        meterLog: MeterLog? = null,
        /** The meter's contrast when it previewed in black and white; null in colour. */
        monoContrast: Float? = null,
    ) = viewModelScope.launch {
        // Written before the roll is read, so the roll appended to below is
        // never one that went stale while the JPEG was being encoded.
        val photoFile = photo?.let { bmp ->
            withContext(Dispatchers.IO) {
                saveShotPhoto(appContext, bmp, "meter").also { bmp.recycle() }
            }
        }
        val roll = repo.rolls.first().find { it.id == rollId }
        if (roll == null || roll.finished || roll.developed) {
            photoFile?.delete()
            return@launch
        }
        val photoPath = photoFile?.absolutePath
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
                .format(java.util.Date()),
            photoThumbPath = photoPath.orEmpty(),
            monoContrast = monoContrast,
        )
        repo.upsertRoll(roll.copy(shots = roll.shots + shot))
        meterLog?.let { m ->
            bumpAchCounters(listOfNotNull(
                AchCounters.METER_LOGS,
                AchCounters.ZONE_LOGS.takeIf { m.zonePlaced },
                AchCounters.RECIPROCITY_LOGS.takeIf { m.longExposure },
            ))
        }

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
    // Each stage keeps the date it was first reached: editing a dev log later
    // is not developing the roll again. Undoing a stage clears its date.
    private fun today(): String = java.time.LocalDate.now().toString()

    fun markFinished(rollId: String, finished: Boolean) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(finished = finished,
            finishedAt = if (finished) roll.finishedAt ?: today() else null))
    }
    fun markDeveloped(rollId: String, devLog: DevLog?, devCost: Double = 0.0, isSelfDev: Boolean = false) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(developed = devLog != null, devLog = devLog, devCost = devCost, isSelfDev = isSelfDev,
            developedAt = if (devLog != null) roll.developedAt ?: today() else null))
    }
    fun markScanned(rollId: String, scanLog: ScanLog?, scanCost: Double = 0.0) = viewModelScope.launch {
        val roll = rolls.value.find { it.id == rollId } ?: return@launch
        repo.upsertRoll(roll.copy(scanned = scanLog != null, scanLog = scanLog, scanCost = scanCost,
            scannedAt = if (scanLog != null) roll.scannedAt ?: today() else null))
    }

    // ─── Chemical CRUD ───────────────────────────────────────────────────────
    fun upsertChemical(c: Chemical) = viewModelScope.launch { repo.upsertChemical(c) }
    fun deleteChemical(c: Chemical) = viewModelScope.launch { repo.deleteChemical(c) }
    fun setChemicalRolls(chemId: String, count: Int) = viewModelScope.launch {
        val chem = chemicals.value.find { it.id == chemId } ?: return@launch
        repo.upsertChemical(chem.copy(manualRolls = count))
    }

    /** Count developed rolls that used this chemical (match by developer name or just total developed) */
    fun rolledCount(chem: Chemical): Int = Achievements.chemicalRollCount(chem, rolls.value)

    // ─── Zoom levels ─────────────────────────────────────────────────────────
    fun upsertZoomLevel(z: ZoomLevel) = viewModelScope.launch { repo.upsertZoomLevel(z) }

    /**
     * Rows from before v12 get their ratio, and every preset not set by hand
     * gets its focal length from the phone's lens data — again whenever rows
     * arrive from a restore or the bound camera turns out to be another one.
     */
    private fun keepZoomLevelsInStep() = viewModelScope.launch {
        combine(repo.zoomLevels, _cameraOptics) { levels, optics -> levels to optics }
            .collect { (_, optics) ->
                // Read the table afresh: every write below re-emits, and working
                // from a stale emission can delete a row's last copy.
                val levels = repo.zoomLevels.first()
                val equiv = optics?.equivMm
                val fixed = levels.map { z ->
                    val up = z.upgradedFromLegacy()
                    z to if (!up.manual && equiv != null)
                        up.copy(mm = Math.round(up.ratio * equiv).toInt()) else up
                }
                // Seeds from every install a backup came from merge in by id,
                // so the same preset piles up. Keep one of each.
                val seen = HashSet<List<Any>>()
                fixed.forEach { (old, new) ->
                    when {
                        !seen.add(listOf(new.label, new.ratio, new.mm, new.manual)) ->
                            repo.deleteZoomLevel(old)
                        new != old -> repo.upsertZoomLevel(new)
                    }
                }
            }
    }

    /** Focal length a ratio gives on this phone; the 23 mm guess when it does not say. */
    fun autoMmFor(ratio: Double): Int =
        Math.round(ratio * (_cameraRig.value?.main?.equivMm ?: CameraOptics.FALLBACK_EQUIV_MM)).toInt()

    /**
     * Add ([existing] null) or edit a preset from the sheet's three fields.
     * A ratio alone takes its focal length from the phone. A focal length alone
     * becomes the ratio that frames like it. Both together pin the focal length
     * by hand — unless it is exactly what the phone's lens data gives anyway.
     * Returns false when neither field holds a number.
     */
    fun saveZoomLevel(existing: ZoomLevel?, label: String, ratioText: String, mmText: String): Boolean {
        val ratioIn = ZoomLevel.ratioFromLabel(ratioText.trim().let { if (it.endsWith("x", true) || it.endsWith("×")) it else "${it}x" })
        val mmIn = mmText.trim().toIntOrNull()?.takeIf { it > 0 }
        val optics = _cameraRig.value?.main
        val ratio = ratioIn ?: mmIn?.let { it / (optics?.equivMm ?: CameraOptics.FALLBACK_EQUIV_MM) }
            ?: return false
        // A preset asked for by focal length is named after it ("50mm"), not
        // after the odd ratio that frames like it.
        val name = label.ifBlank { if (ratioIn == null && mmIn != null) "${mmIn}mm" else "" }
        upsertZoomLevel(zoomLevelFor(existing?.id ?: uid(), name, ratio, mmIn.takeIf { ratioIn != null }, optics))
        return true
    }
    fun deleteZoomLevel(z: ZoomLevel) = viewModelScope.launch { repo.deleteZoomLevel(z) }

    // ─── Bulk Rolls ───────────────────────────────────────────────────────────
    fun upsertBulkRoll(b: BulkRoll) = viewModelScope.launch { repo.upsertBulkRoll(b) }
    fun deleteBulkRoll(b: BulkRoll) = viewModelScope.launch { repo.deleteBulkRoll(b) }

    fun cutFromBulk(bulk: BulkRoll, frames: Int, bufferFrames: Int, quantity: Int, expiryDate: String) =
        viewModelScope.launch { repo.cutFromBulk(bulk, frames, bufferFrames, quantity, expiryDate) }

    fun saveBulkBufferFrames(frames: Int) = viewModelScope.launch {
        val v = frames.coerceAtLeast(0)
        _bulkBufferFrames.value = v; repo.setSetting("bulk_buffer_frames", v.toString())
    }

    // ─── Home layout ─────────────────────────────────────────────────────────
    //
    // Which rows Home is built from, in which order. Persisted as two CSVs of
    // enum names rather than indices: indices would silently reshuffle
    // everyone's layout the day a section is inserted into the middle of the
    // enum, and names simply keep pointing at the same row.

    private val _homeOrder = MutableStateFlow(DEFAULT_HOME_ORDER)
    val homeOrder: StateFlow<List<HomeSection>> = _homeOrder.asStateFlow()
    private val _homeHidden = MutableStateFlow(parseHomeHidden(null))
    val homeHidden: StateFlow<Set<HomeSection>> = _homeHidden.asStateFlow()

    fun moveHomeSection(section: HomeSection, delta: Int) = viewModelScope.launch {
        val current = _homeOrder.value.toMutableList()
        val from = current.indexOf(section)
        val to = from + delta
        if (from < 0 || to !in current.indices) return@launch
        current.removeAt(from)
        current.add(to, section)
        _homeOrder.value = current
        repo.setSetting("home_order", current.joinToString(",") { it.name })
    }

    /** Opt-in sections (see HomeSection.optIn) the user has turned on. */
    private var homeShown: Set<HomeSection> = emptySet()

    fun setHomeSectionVisible(section: HomeSection, visible: Boolean) = viewModelScope.launch {
        if (section.optIn) {
            // Stored as shown, not hidden: an opt-in section starts hidden for
            // everyone, and the hidden list cannot tell "never chose" apart
            // from "chose to show it".
            homeShown = if (visible) homeShown + section else homeShown - section
            repo.setSetting("home_shown", homeShown.joinToString(",") { it.name })
            _homeHidden.value = if (visible) _homeHidden.value - section else _homeHidden.value + section
            return@launch
        }
        val next = if (visible) _homeHidden.value - section else _homeHidden.value + section
        _homeHidden.value = next
        repo.setSetting("home_hidden", next.filterNot { it.optIn }.joinToString(",") { it.name })
    }

    fun resetHomeLayout() = viewModelScope.launch {
        homeShown = emptySet()
        _homeOrder.value = DEFAULT_HOME_ORDER
        _homeHidden.value = parseHomeHidden(null)
        repo.setSetting("home_order", "")
        repo.setSetting("home_hidden", "")
        repo.setSetting("home_shown", "")
    }

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

    // ─── Chosen place ────────────────────────────────────────────────────────
    //
    // A pinned place overrides the device's location everywhere weather is used.
    // Planning a shoot somewhere you are not is the normal case for this app —
    // you check the light for the coast on Thursday from your kitchen — and a
    // forecast that silently snaps back to where the phone is would be useless
    // for it. Persisted as three settings rather than a table: it is one value,
    // and a table for one row is a migration nobody needed.

    private val _placeName = MutableStateFlow("")
    /** Blank when following the device's location. */
    val placeName: StateFlow<String> = _placeName.asStateFlow()
    private var placeLat: Double? = null
    private var placeLon: Double? = null

    private val _placeResults = MutableStateFlow<List<GeoPlace>>(emptyList())
    val placeResults: StateFlow<List<GeoPlace>> = _placeResults.asStateFlow()
    private val _placeSearching = MutableStateFlow(false)
    val placeSearching: StateFlow<Boolean> = _placeSearching.asStateFlow()

    /** Coordinates to use, or null to fall back to the device. */
    fun pinnedLatLon(): Pair<Double, Double>? {
        val la = placeLat; val lo = placeLon
        return if (la != null && lo != null) la to lo else null
    }

    fun searchPlaces(query: String) = viewModelScope.launch {
        val key = _owmKey.value
        if (key.isBlank() || query.isBlank()) { _placeResults.value = emptyList(); return@launch }
        _placeSearching.value = true
        try {
            _placeResults.value = weatherApi.searchPlaces(query.trim(), key)
        } catch (_: Exception) {
            _placeResults.value = emptyList()
        } finally {
            _placeSearching.value = false
        }
    }
    fun clearPlaceResults() { _placeResults.value = emptyList() }

    fun pinPlace(place: GeoPlace) = viewModelScope.launch {
        placeLat = place.lat; placeLon = place.lon
        _placeName.value = place.label
        repo.setSetting("place_name", place.label)
        repo.setSetting("place_lat", place.lat.toString())
        repo.setSetting("place_lon", place.lon.toString())
        _placeResults.value = emptyList()
        fetchWeather(place.lat, place.lon)
    }

    /** Back to wherever the phone is. */
    fun unpinPlace() = viewModelScope.launch {
        placeLat = null; placeLon = null
        _placeName.value = ""
        repo.setSetting("place_name", "")
        repo.setSetting("place_lat", "")
        repo.setSetting("place_lon", "")
        _weatherState.value = WeatherState.Idle
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

    // Haptic pulse at the start of each agitation window. The rhythm itself is a
    // property of the step (see DevTime.Agitation), not a fixed minute mark —
    // stand development agitates once in an hour and a rotary processor never
    // stops, and a pulse every sixty seconds is wrong for both.
    private val _agitationCues = MutableStateFlow(true)
    val agitationCues: StateFlow<Boolean> = _agitationCues.asStateFlow()
    fun saveAgitationCues(on: Boolean) = viewModelScope.launch {
        _agitationCues.value = on; repo.setSetting("agitation_cues", on.toString())
    }

    /**
     * Working temperature of the chemistry, in °C.
     *
     * Persisted because it is a property of the room and the tap, not of one
     * session: whatever the darkroom ran at last night is the best guess for
     * tonight, and being asked for it every time is how it ends up ignored.
     */
    /**
     * Safelight mode: the whole app swaps to the red scheme.
     *
     * Persisted, because it is turned on when you walk into a dark room and off
     * when you leave, and losing it on a process death mid-development would
     * blind you at the worst moment.
     */
    private val _safelight = MutableStateFlow(false)
    val safelight: StateFlow<Boolean> = _safelight.asStateFlow()
    fun setSafelight(on: Boolean) = viewModelScope.launch {
        _safelight.value = on; repo.setSetting("safelight", on.toString())
    }
    fun toggleSafelight() = setSafelight(!_safelight.value)

    /**
     * False until the palette settings have been read.
     *
     * The window background is already black, so holding the first frame until
     * this flips costs a few milliseconds of the black the app starts on
     * anyway — and it is the difference between opening in your theme and
     * opening in someone else's.
     */
    private val _themeReady = MutableStateFlow(false)
    val themeReady: StateFlow<Boolean> = _themeReady.asStateFlow()

    /**
     * The palette the app wears (see ui/theme/Palette.kt). Ignored while
     * safelight is on, which is its own scheme. Replaces the old Legacy amber
     * switch: `legacy_amber` is still read until a palette is first picked.
     */
    private val _palette = MutableStateFlow(Palette.DYE_LAYER)
    val palette: StateFlow<Palette> = _palette.asStateFlow()
    // The look setters only write the setting. The observer below turns the
    // stored value into the state the UI reads, so there is one source of it.
    // Setting the state here as well raced that observer: picking Expired and
    // then Amber, the database's answer to the first write could land after
    // the second tap and put Expired back, and the two flicked back and forth
    // for a moment before settling.
    fun setPalette(p: Palette) = viewModelScope.launch {
        // The picker only lists unlocked palettes; this is the backstop.
        if (!p.unlocked(achievementRecords.value)) return@launch
        repo.setSetting("palette", p.key)
    }

    // ─── Look: the cosmetic rewards ───────────────────────────────────────────
    private val _grain = MutableStateFlow(GrainStock.DEFAULT)
    val grain: StateFlow<GrainStock> = _grain.asStateFlow()
    fun setGrain(g: GrainStock) = viewModelScope.launch {
        if (!g.unlocked(achievementRecords.value)) return@launch
        repo.setSetting("grain", g.key)
    }

    private val _iris = MutableStateFlow(IrisStyle.DEFAULT)
    val iris: StateFlow<IrisStyle> = _iris.asStateFlow()
    fun setIris(i: IrisStyle) = viewModelScope.launch {
        if (!i.unlocked(achievementRecords.value)) return@launch
        repo.setSetting("iris", i.key)
    }

    private val _edgePrint = MutableStateFlow(EdgePrint())
    val edgePrint: StateFlow<EdgePrint> = _edgePrint.asStateFlow()
    fun setEdgeText(text: String) = viewModelScope.launch {
        if (!EdgePrint.UNLOCK.isMet(achievementRecords.value)) return@launch
        repo.setSetting("edge_text", EdgePrint.clean(text).trim())
    }
    fun setEdgeFrameStyle(style: FrameNumberStyle) = viewModelScope.launch {
        if (!EdgePrint.UNLOCK.isMet(achievementRecords.value)) return@launch
        repo.setSetting("edge_frame_style", style.key)
    }

    /** The stored look, before it is checked against what has been earned. */
    private data class LookSettings(
        val palette: String?, val legacyAmber: String?, val grain: String?,
        val iris: String?, val edgeText: String?, val edgeFrameStyle: String?,
    )

    /** Each choice, or its default where the badge behind it is not (or no longer) earned. */
    private fun applyLook(s: LookSettings, records: Map<Achievement, AchRecord>) {
        _palette.value = Palette.resolve(s.palette, s.legacyAmber, records)
        _grain.value = GrainStock.resolve(s.grain, records)
        _iris.value = IrisStyle.resolve(s.iris, records)
        _edgePrint.value = EdgePrint.resolve(s.edgeText, s.edgeFrameStyle, records)
    }

    init {
        // Kept live after the first read. It is how a pick in Settings reaches
        // the screen (the setters above only write), and a backup restore
        // writes these settings and the badges under the running app, so the
        // look follows that too without a restart. The first value repeats
        // what the theme block read, unless a pick has landed since; either
        // way it is the stored look, so it is applied like the rest.
        viewModelScope.launch {
            val keys = listOf("palette", "legacy_amber", "grain", "iris", "edge_text", "edge_frame_style")
            combine(keys.map { repo.observeSetting(it) }) { it.toList() }
                .combine(repo.observeSetting(AchievementRecords.SETTING)) { v, rec ->
                    LookSettings(v[0], v[1], v[2], v[3], v[4], v[5]) to rec
                }
                // Any write to the settings table wakes all seven queries.
                .distinctUntilChanged()
                .collect { (s, rec) -> applyLook(s, AchievementRecords.decode(rec)) }
        }
    }

    /**
     * Saturation of the cyan/magenta/yellow/mask/violet accent colors, 0f
     * (grayscale) to 1f (full dye-layer saturation, the default). Mutes the
     * high-contrast accents for eye comfort; has no effect while safelight is
     * active (see FilmTheme.kt).
     */
    private val _saturation = MutableStateFlow(1f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()
    fun setSaturation(value: Float) = viewModelScope.launch {
        _saturation.value = value; repo.setSetting("saturation", value.toString())
    }

    private val _devTempC = MutableStateFlow(20.0)
    val devTempC: StateFlow<Double> = _devTempC.asStateFlow()
    fun saveDevTempC(t: Double) = viewModelScope.launch {
        _devTempC.value = t; repo.setSetting("dev_temp_c", t.toString())
    }

    // ─── Achievement notifications ───────────────────────────────────────────
    /** Notify badges earned in the background. On by default, under its own key. */
    private val _achNotify = MutableStateFlow(true)
    val achNotify: StateFlow<Boolean> = _achNotify.asStateFlow()
    fun saveAchNotify(on: Boolean) = viewModelScope.launch {
        _achNotify.value = on; repo.setSetting("ach_notify", on.toString())
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

    /**
     * Timers the user built and kept, stored as written — at 20 °C for B&W —
     * and scaled to the working temperature only when started, like the presets.
     *
     * Observed rather than read once, so a backup restore shows up without a
     * restart.
     */
    val savedTimers: StateFlow<List<DevTimer>> = repo.observeSetting(Constants.SAVED_TIMERS_SETTING)
        .map { decodeTimers(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // The list is one setting, so every change is a read-modify-write. Read
    // back from the database rather than the StateFlow, which lags a write by
    // one emission, and hold the lock across both halves: two quick edits would
    // otherwise start from the same list and the second would drop the first.
    private val savedTimersLock = Mutex()
    private fun editSavedTimers(change: (List<DevTimer>) -> List<DevTimer>) = viewModelScope.launch {
        savedTimersLock.withLock {
            val current = decodeTimers(repo.getSetting(Constants.SAVED_TIMERS_SETTING))
            repo.setSetting(Constants.SAVED_TIMERS_SETTING, encodeTimers(change(current)))
        }
    }

    /** Adds [timer], or replaces the saved timer with the same id in place. */
    fun saveCustomTimer(timer: DevTimer) = editSavedTimers { current ->
        if (current.any { it.id == timer.id }) current.map { if (it.id == timer.id) timer else it }
        else current + timer
    }

    fun deleteCustomTimer(id: String) = editSavedTimers { current -> current.filterNot { it.id == id } }

    /**
     * Named agitation rhythms, offered on every step of the custom timer sheet.
     * A step copies the rhythm rather than pointing at it, so forgetting a
     * pattern never changes a timer that already uses it.
     */
    val savedAgitations: StateFlow<List<SavedAgitation>> =
        repo.observeSetting(Constants.SAVED_AGITATIONS_SETTING)
            .map { decodeAgitations(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Same read-modify-write under a lock as the saved timers, for the same reason.
    private val savedAgitationsLock = Mutex()
    private fun editSavedAgitations(change: (List<SavedAgitation>) -> List<SavedAgitation>) =
        viewModelScope.launch {
            savedAgitationsLock.withLock {
                val current = decodeAgitations(repo.getSetting(Constants.SAVED_AGITATIONS_SETTING))
                repo.setSetting(Constants.SAVED_AGITATIONS_SETTING, encodeAgitations(change(current)))
            }
        }

    fun saveAgitationPattern(pattern: SavedAgitation) = editSavedAgitations { current ->
        if (current.any { it.id == pattern.id }) current.map { if (it.id == pattern.id) pattern else it }
        else current + pattern
    }

    fun deleteAgitationPattern(id: String) = editSavedAgitations { current -> current.filterNot { it.id == id } }

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
            currentStep = next, secondsLeft = s.timer.steps[next].durationSec, running = false,
            skippedStep = true,
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
                // Pulse on the leading edge of an agitation window, not through
                // it: buzzing once a second for ten seconds is an alarm, and the
                // cue only has to say "now".
                val step = s.timer.steps[s.currentStep]
                val elapsed = step.durationSec - left
                val startsNow = step.agitation.isAgitating(elapsed) &&
                    !step.agitation.isAgitating(elapsed - 1)
                if (_agitationCues.value && startsNow && lastAgitationCueSec != elapsed && left > 3) {
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
            // A run counts for the achievements only when every step ran to
            // its end: skipping to the last step and letting it finish is not
            // developing a roll.
            if (!s.skippedStep) bumpAchCounters(listOfNotNull(
                AchCounters.TIMER_RUNS,
                AchCounters.SAFELIGHT_TIMER_RUNS.takeIf { _safelight.value },
            ))
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
        val allShots = r.flatMap { it.shots }
        val byAperture = com.analogvault.util.Habits.apertureHistogram(allShots.map { it.aperture })
        val byShutter  = com.analogvault.util.Habits.shutterHistogram(allShots.map { it.shutter })
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
            byAperture = byAperture,
            byShutter = byShutter,
            totalFilmCost = filmRollCost + bulkRemaining,
            filmCostOnRolls = filmRollCost,
            uncutBulkValue = bulkRemaining,
            totalDevCost  = r.sumOf { it.devCost },
            totalScanCost = r.sumOf { it.scanCost },
            selfDevRolls  = r.count { it.isSelfDev && it.developed },
            labDevRolls   = r.count { !it.isSelfDev && it.developed },
            rollCosts     = rollCosts
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    // ─── Achievements ────────────────────────────────────────────────────────
    /**
     * Every badge's progress, or null until the vault has been read.
     *
     * Built from the repository's own flows rather than the stateIn copies
     * above: those start as empty lists, and an evaluation of an empty vault
     * on launch would read as "nothing earned yet" — then everything the vault
     * already held would arrive a moment later as a burst of new unlocks.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val achievementProgress: StateFlow<List<AchProgress>?> =
        combine(repo.rolls, repo.films, repo.cameras, repo.lenses, repo.chemicals) { r, f, c, l, ch ->
            AchSnapshot(rolls = r, films = f, cameras = c, lenses = l, chemicals = ch)
        }
            .combine(repo.bulkRolls) { snap, b -> snap.copy(bulkRolls = b) }
            .combine(repo.observeSetting(AchCounters.SETTING)) { snap, k ->
                snap.copy(counters = AchCounters.decode(k))
            }
            // Logging a frame writes the roll once; a restore writes hundreds
            // of rows. Wait for the writes to settle before asking again.
            .debounce(300)
            .map { Achievements.evaluate(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The stored unlocks: when each badge was earned, and by what. */
    val achievementRecords: StateFlow<Map<Achievement, AchRecord>> =
        repo.observeSetting(AchievementRecords.SETTING)
            .map { AchievementRecords.decode(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _unlockNotices = MutableStateFlow<List<UnlockNotice>>(emptyList())
    /** The banner to show now; the rest wait their turn. */
    val unlockNotice: StateFlow<UnlockNotice?> = _unlockNotices.map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun dismissUnlockNotice() { _unlockNotices.value = _unlockNotices.value.drop(1) }

    private val _openBadges = MutableStateFlow(false)
    /** Set by a tapped achievement notification; VaultApp opens Stats → Badges and clears it. */
    val openBadges: StateFlow<Boolean> = _openBadges.asStateFlow()
    fun requestOpenBadges() { _openBadges.value = true }
    fun consumeOpenBadges() { _openBadges.value = false }

    /** Counts the things the vault's data cannot show for itself. */
    private fun bumpAchCounters(names: List<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            repo.updateSetting(AchCounters.SETTING) { AchCounters.increment(it, names) }
        }
    }

    init {
        viewModelScope.launch {
            achievementProgress.filterNotNull().collect { progress ->
                var earned: List<Achievement> = emptyList()
                var firstRun = false
                repo.updateSetting(AchievementRecords.SETTING) { raw ->
                    // An empty record set is still written on the first run,
                    // so later runs can tell the vault has been looked at.
                    val outcome = AchievementRecords.reconcile(
                        stored = AchievementRecords.decode(raw),
                        progress = progress,
                        today = java.time.LocalDate.now(),
                    )
                    earned = outcome.newlyEarned
                    firstRun = raw == null
                    AchievementRecords.encode(outcome.records)
                }
                announceUnlocks(earned, firstRun)
            }
        }
    }

    /**
     * What to say about newly earned badges, if anything.
     *
     * The first run with achievements earns the vault's whole history at
     * once; that is one quiet "you had already earned N", not N banners. On
     * screen a badge gets a banner, and off screen (a timer finishing in a
     * pocket) a notification if they are turned on.
     */
    private fun announceUnlocks(earned: List<Achievement>, firstRun: Boolean) {
        if (earned.isEmpty()) return
        val notice = if (firstRun) UnlockNotice(emptyList(), backfilled = earned.size) else UnlockNotice(earned)
        if (!firstRun && !com.analogvault.work.AppVisibility.foreground) {
            if (_achNotify.value) com.analogvault.work.AchievementNotifier.post(appContext, earned)
            return
        }
        _unlockNotices.value = _unlockNotices.value + notice
    }

}

fun uid() = UUID.randomUUID().toString()

/**
 * What the meter holds still while the light moves. [key] is the `meter_priority`
 * setting; anything unrecognised reads as aperture priority, the default.
 */
enum class MeterPriority(val key: String) {
    APERTURE("aperture"), SHUTTER("shutter"), MANUAL("manual");

    companion object {
        fun fromKey(key: String?): MeterPriority = entries.find { it.key == key } ?: APERTURE
    }
}

data class DarkroomTimerState(
    val timer: DevTimer,
    val currentStep: Int = 0,
    val secondsLeft: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    /** True once any step was skipped; such a run does not count as run to the end. */
    val skippedStep: Boolean = false
)

/**
 * One banner's worth of news: badges earned together, or the count the first
 * run found already earned.
 */
data class UnlockNotice(
    val earned: List<Achievement>,
    val backfilled: Int = 0,
)

/** What the meter knew about a frame it logged. */
data class MeterLog(
    /** A zone was placed: zone placement on, with spot metering. */
    val zonePlaced: Boolean = false,
    /** One second or longer, where the meter shows its reciprocity correction. */
    val longExposure: Boolean = false,
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
    /** Whole-stop histograms of the shot log, in scale order. See util/Habits. */
    val byAperture: List<Pair<String, Int>> = emptyList(),
    val byShutter: List<Pair<String, Int>> = emptyList(),
    // Cost totals
    val totalFilmCost: Double = 0.0,
    /**
     * Film cost of the rolls actually loaded, without the value of bulk stock
     * still on the spool. [totalFilmCost] answers "what is my film worth"; this
     * answers "what did these frames cost", and only the second one divides
     * sensibly by a frame count.
     */
    val filmCostOnRolls: Double = 0.0,
    val uncutBulkValue: Double = 0.0,
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

/**
 * Ratios to start a fresh install with: the widest the cameras reach, each
 * telephoto's own focal length, then common steps in between.
 */
internal fun defaultZoomRatios(rig: CameraRig?): List<Double> {
    if (rig == null) return listOf(1.0, 2.0, 3.0)
    fun tenth(r: Double) = Math.round(r * 10) / 10.0
    val wide = rig.minZoom.takeIf { it < 0.95 }?.let(::tenth)
    val teles = rig.lenses.map { tenth(rig.baseRatio(it)) }.filter { it > 1.0 }
    // A round step right next to a real lens ("3x" beside a 2.9x tele) is noise.
    val steps = listOf(1.0, 2.0, 3.0, 5.0, 10.0).filter { s -> teles.none { kotlin.math.abs(it - s) < 0.3 } }
    return (listOfNotNull(wide) + teles + steps)
        .filter { rig.inRange(it) }
        .distinct().sorted()
        .ifEmpty { listOf(1.0) }
}

/** A preset for [ratio]; [manualMm] pins its focal length unless the phone already gives that. */
internal fun zoomLevelFor(id: String, label: String, ratio: Double, manualMm: Int?, optics: CameraOptics?): ZoomLevel {
    val r = ratio.coerceIn(0.1, 100.0)
    val equiv = optics?.equivMm
    val autoMm = Math.round(r * (equiv ?: CameraOptics.FALLBACK_EQUIV_MM)).toInt()
    val manual = manualMm != null && (equiv == null || manualMm != autoMm)
    return ZoomLevel(
        id = id,
        label = label.trim().ifBlank { ZoomLevel.labelFor(r) },
        mm = if (manual) manualMm!! else autoMm,
        ratio = r,
        manual = manual,
    )
}

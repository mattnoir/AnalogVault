package com.analogvault.data.repo

import com.analogvault.data.db.*
import com.analogvault.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val filmDao: FilmDao,
    private val cameraDao: CameraDao,
    private val lensDao: LensDao,
    private val accessoryDao: AccessoryDao,
    private val rollDao: RollDao,
    private val chemicalDao: ChemicalDao,
    private val zoomLevelDao: ZoomLevelDao,
    private val settingDao: SettingDao,
    private val bulkRollDao: BulkRollDao
) {
    // Films
    val films: Flow<List<FilmStock>> = filmDao.getAll()
    suspend fun upsertFilm(f: FilmStock) = filmDao.upsert(f)
    suspend fun deleteFilm(f: FilmStock) = filmDao.delete(f)

    // Cameras
    val cameras: Flow<List<Camera>> = cameraDao.getAll()
    suspend fun upsertCamera(c: Camera) = cameraDao.upsert(c)
    suspend fun deleteCamera(c: Camera) = cameraDao.delete(c)

    // Lenses
    val lenses: Flow<List<Lens>> = lensDao.getAll()
    suspend fun upsertLens(l: Lens) = lensDao.upsert(l)
    suspend fun deleteLens(l: Lens) = lensDao.delete(l)

    // Accessories
    val accessories: Flow<List<Accessory>> = accessoryDao.getAll()
    suspend fun upsertAccessory(a: Accessory) = accessoryDao.upsert(a)
    suspend fun deleteAccessory(a: Accessory) = accessoryDao.delete(a)

    // Rolls
    val rolls: Flow<List<Roll>> = rollDao.getAll()
    suspend fun upsertRoll(r: Roll) = rollDao.upsert(r)
    suspend fun deleteRollById(id: String) = rollDao.deleteById(id)

    /**
     * Re-count the unfinished 120/220 rolls against their camera's frame size
     * (see [framesFor]), optionally for one camera only.
     *
     * Rolls loaded before the load sheet asked the camera all say 12, and a
     * body whose shooting format is set after loading would otherwise keep the
     * old count. A roll that already has more frames logged than the new count
     * is left alone — shrinking it would hide shots the user took.
     */
    suspend fun refitRollFrames(cameraId: String? = null) {
        val cams = cameras.first().associateBy { it.id }
        val stocks = films.first().associateBy { it.id }
        rolls.first()
            .filter { !it.finished && (cameraId == null || it.cameraId == cameraId) }
            .forEach { r ->
                val cam = cams[r.cameraId] ?: return@forEach
                val film = stocks[r.filmId] ?: return@forEach
                if (film.filmFormat != "120" && film.filmFormat != "220") return@forEach
                val frames = cam.framesFor(film)
                if (frames != r.totalShots && r.shots.size <= frames) {
                    rollDao.upsert(r.copy(totalShots = frames))
                }
            }
    }

    // Chemicals
    val chemicals: Flow<List<Chemical>> = chemicalDao.getAll()
    suspend fun upsertChemical(c: Chemical) = chemicalDao.upsert(c)
    suspend fun deleteChemical(c: Chemical) = chemicalDao.delete(c)

    // Zoom levels
    val zoomLevels: Flow<List<ZoomLevel>> = zoomLevelDao.getAll()
    suspend fun upsertZoomLevel(z: ZoomLevel) = zoomLevelDao.upsert(z)
    suspend fun deleteZoomLevel(z: ZoomLevel) = zoomLevelDao.delete(z)

    // Settings
    suspend fun getSetting(key: String): String? = settingDao.get(key)?.value
    suspend fun setSetting(key: String, value: String) = settingDao.upsert(Setting(key, value))
    /** A setting as a live value, for the ones a backup restore can change underneath the UI. */
    fun observeSetting(key: String): Flow<String?> = settingDao.observe(key)

    // Bulk rolls
    val bulkRolls: Flow<List<BulkRoll>> = bulkRollDao.getAll()
    suspend fun upsertBulkRoll(b: BulkRoll) = bulkRollDao.upsert(b)
    suspend fun deleteBulkRoll(b: BulkRoll) = bulkRollDao.delete(b)

    /**
     * Cut rolls from a bulk canister and add them to the film stash.
     * Creates [quantity] individual FilmStock entries (quantity=1 each) and
     * deducts frames×quantity from the canister. No Roll or camera involvement —
     * that happens later when the user loads one of the new stash entries.
     */
    suspend fun cutFromBulk(
        bulk: BulkRoll,
        frames: Int,
        quantity: Int,
        expiryDate: String
    ) {
        // Amortise the canister price over the rolls it can produce so each cut roll
        // carries its share of the cost (otherwise per-roll cost shows €0 for bulk film).
        val perRollCost =
            if (bulk.totalCost > 0.0 && bulk.totalFrames > 0 && frames > 0)
                bulk.totalCost * frames / bulk.totalFrames
            else 0.0
        repeat(quantity) {
            val stock = FilmStock(
                id          = java.util.UUID.randomUUID().toString(),
                name        = bulk.name,
                brand       = bulk.brand,
                type        = bulk.type,
                iso         = bulk.iso,
                shots       = frames,
                filmFormat  = "135 (35mm)",
                frameCount  = frames,
                expiryDate  = expiryDate,
                storage     = "Bulk",
                quantity    = 1,
                notes       = "Cut from bulk — ${bulk.name}",
                costPerRoll = perRollCost
            )
            filmDao.upsert(stock)
        }
        bulkRollDao.upsert(bulk.copy(usedFrames = bulk.usedFrames + (frames * quantity)))
    }
}

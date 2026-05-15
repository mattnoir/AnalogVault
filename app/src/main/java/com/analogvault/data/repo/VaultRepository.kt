package com.analogvault.data.repo

import com.analogvault.data.db.*
import com.analogvault.data.model.*
import kotlinx.coroutines.flow.Flow
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
    private val settingDao: SettingDao
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
    suspend fun deleteRoll(r: Roll) = rollDao.delete(r)
    suspend fun deleteRollById(id: String) = rollDao.deleteById(id)

    // Chemicals
    val chemicals: Flow<List<Chemical>> = chemicalDao.getAll()
    suspend fun upsertChemical(c: Chemical) = chemicalDao.upsert(c)
    suspend fun deleteChemical(c: Chemical) = chemicalDao.delete(c)

    // Zoom levels
    val zoomLevels: Flow<List<ZoomLevel>> = zoomLevelDao.getAll()
    suspend fun upsertZoomLevel(z: ZoomLevel) = zoomLevelDao.upsert(z)
    suspend fun deleteZoomLevel(z: ZoomLevel) = zoomLevelDao.delete(z)
    suspend fun replaceZoomLevels(levels: List<ZoomLevel>) {
        zoomLevelDao.deleteAll()
        levels.forEach { zoomLevelDao.upsert(it) }
    }

    // Settings
    suspend fun getSetting(key: String): String? = settingDao.get(key)?.value
    suspend fun setSetting(key: String, value: String) = settingDao.upsert(Setting(key, value))
}

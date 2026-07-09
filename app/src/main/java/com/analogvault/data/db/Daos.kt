package com.analogvault.data.db

import androidx.room.*
import com.analogvault.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmDao {
    @Query("SELECT * FROM films ORDER BY name ASC")
    fun getAll(): Flow<List<FilmStock>>
    @Upsert suspend fun upsert(film: FilmStock)
    @Delete suspend fun delete(film: FilmStock)
}

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY name ASC")
    fun getAll(): Flow<List<Camera>>
    @Upsert suspend fun upsert(cam: Camera)
    @Delete suspend fun delete(cam: Camera)
}

@Dao
interface LensDao {
    @Query("SELECT * FROM lenses ORDER BY name ASC")
    fun getAll(): Flow<List<Lens>>
    @Upsert suspend fun upsert(lens: Lens)
    @Delete suspend fun delete(lens: Lens)
}

@Dao
interface AccessoryDao {
    @Query("SELECT * FROM accessories ORDER BY name ASC")
    fun getAll(): Flow<List<Accessory>>
    @Upsert suspend fun upsert(acc: Accessory)
    @Delete suspend fun delete(acc: Accessory)
}

@Dao
interface RollDao {
    @Query("SELECT * FROM rolls ORDER BY startDate DESC")
    fun getAll(): Flow<List<Roll>>
    @Upsert suspend fun upsert(roll: Roll)
    @Delete suspend fun delete(roll: Roll)
    @Query("DELETE FROM rolls WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChemicalDao {
    @Query("SELECT * FROM chemicals ORDER BY name ASC")
    fun getAll(): Flow<List<Chemical>>
    @Upsert suspend fun upsert(chem: Chemical)
    @Delete suspend fun delete(chem: Chemical)
}

@Dao
interface BulkRollDao {
    @Query("SELECT * FROM bulk_rolls ORDER BY name ASC")
    fun getAll(): Flow<List<BulkRoll>>
    @Upsert suspend fun upsert(b: BulkRoll)
    @Delete suspend fun delete(b: BulkRoll)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAll(): Flow<List<DevRecipe>>
    @Upsert suspend fun upsert(r: DevRecipe)
    @Delete suspend fun delete(r: DevRecipe)
}

@Dao
interface ZoomLevelDao {
    @Query("SELECT * FROM zoom_levels ORDER BY mm ASC")
    fun getAll(): Flow<List<ZoomLevel>>
    @Upsert suspend fun upsert(z: ZoomLevel)
    @Delete suspend fun delete(z: ZoomLevel)
    @Query("DELETE FROM zoom_levels")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): Setting?
    @Upsert suspend fun upsert(setting: Setting)
}

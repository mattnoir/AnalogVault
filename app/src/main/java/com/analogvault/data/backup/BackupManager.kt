package com.analogvault.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.analogvault.data.model.*
import com.analogvault.data.repo.VaultRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VaultBackup(
    val version: Int = 2,
    val exportedAt: String = "",
    val films: List<FilmStock> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val lenses: List<Lens> = emptyList(),
    val accessories: List<Accessory> = emptyList(),
    val rolls: List<Roll> = emptyList(),
    val chemicals: List<Chemical> = emptyList(),
    val zoomLevels: List<ZoomLevel> = emptyList(),
    val owmKey: String = "",
    /** base64-encoded photos keyed by original file path */
    val photos: Map<String, String> = emptyMap()
)

sealed class BackupResult {
    data class Success(val message: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

@Singleton
class BackupManager @Inject constructor(
    private val repo: VaultRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun export(context: Context, uri: Uri, includePhotos: Boolean = true): BackupResult {
        return try {
            val rolls = repo.rolls.first()

            // Collect all photo paths referenced by shots
            val photos = mutableMapOf<String, String>()
            if (includePhotos) {
                val allPaths = rolls.flatMap { roll ->
                    roll.shots.mapNotNull { shot ->
                        shot.photoThumbPath.takeIf { it.isNotBlank() }
                    }
                }.distinct()

                var loaded = 0
                var failed = 0
                for (path in allPaths) {
                    try {
                        val file = File(path)
                        if (file.exists() && file.length() < 5 * 1024 * 1024) { // skip >5MB
                            val bytes = file.readBytes()
                            photos[path] = Base64.encodeToString(bytes, Base64.DEFAULT)
                            loaded++
                        }
                    } catch (e: Exception) { failed++ }
                }
            }

            val backup = VaultBackup(
                version     = 2,
                exportedAt  = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date()),
                films       = repo.films.first(),
                cameras     = repo.cameras.first(),
                lenses      = repo.lenses.first(),
                accessories = repo.accessories.first(),
                rolls       = rolls,
                chemicals   = repo.chemicals.first(),
                zoomLevels  = repo.zoomLevels.first(),
                owmKey      = repo.getSetting("owm_key") ?: "",
                photos      = photos
            )

            val json = gson.toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return BackupResult.Error("Could not open output stream")

            val photoNote = if (includePhotos && photos.isNotEmpty()) ", ${photos.size} photos" else ""
            BackupResult.Success(
                "Exported: ${backup.rolls.size} rolls, ${backup.films.size} films, " +
                "${backup.cameras.size} cameras$photoNote"
            )
        } catch (e: Exception) {
            BackupResult.Error("Export failed: ${e.message}")
        }
    }

    suspend fun import(context: Context, uri: Uri): BackupResult {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return BackupResult.Error("Could not read file")

            val backup = gson.fromJson(json, VaultBackup::class.java)
                ?: return BackupResult.Error("Invalid backup file")

            if (backup.version !in 1..2) {
                return BackupResult.Error("Unsupported backup version ${backup.version}")
            }

            // Restore photos to cache dir, update paths in shots
            var photosRestored = 0
            val pathMap = mutableMapOf<String, String>() // old path -> new path
            if (backup.photos.isNotEmpty()) {
                val dir = File(context.cacheDir, "camera_photos").also { it.mkdirs() }
                for ((oldPath, b64) in backup.photos) {
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val fileName = File(oldPath).name
                        val newFile = File(dir, fileName)
                        newFile.writeBytes(bytes)
                        pathMap[oldPath] = newFile.absolutePath
                        photosRestored++
                    } catch (e: Exception) { /* skip corrupt photo */ }
                }
            }

            // Remap photo paths in rolls if they moved
            val remappedRolls = if (pathMap.isNotEmpty()) {
                backup.rolls.map { roll ->
                    roll.copy(shots = roll.shots.map { shot ->
                        val newPath = pathMap[shot.photoThumbPath]
                        if (newPath != null) shot.copy(photoThumbPath = newPath) else shot
                    })
                }
            } else backup.rolls

            backup.films.forEach       { repo.upsertFilm(it) }
            backup.cameras.forEach     { repo.upsertCamera(it) }
            backup.lenses.forEach      { repo.upsertLens(it) }
            backup.accessories.forEach { repo.upsertAccessory(it) }
            remappedRolls.forEach      { repo.upsertRoll(it) }
            backup.chemicals.forEach   { repo.upsertChemical(it) }
            backup.zoomLevels.forEach  { repo.upsertZoomLevel(it) }
            if (backup.owmKey.isNotBlank()) repo.setSetting("owm_key", backup.owmKey)

            val photoNote = if (photosRestored > 0) ", $photosRestored photos" else ""
            BackupResult.Success(
                "Imported: ${backup.rolls.size} rolls, ${backup.films.size} films, " +
                "${backup.cameras.size} cameras$photoNote"
            )
        } catch (e: Exception) {
            BackupResult.Error("Import failed: ${e.message}")
        }
    }
}

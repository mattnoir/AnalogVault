package com.analogvault.data.backup

import android.content.Context
import android.net.Uri
import com.analogvault.data.model.*
import com.analogvault.data.repo.VaultRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class VaultBackup(
    val version: Int = 1,
    val exportedAt: String = "",
    val films: List<FilmStock> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val lenses: List<Lens> = emptyList(),
    val accessories: List<Accessory> = emptyList(),
    val rolls: List<Roll> = emptyList(),
    val chemicals: List<Chemical> = emptyList(),
    val zoomLevels: List<ZoomLevel> = emptyList(),
    val owmKey: String = ""
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

    // ─── Export ───────────────────────────────────────────────────────────────

    suspend fun export(context: Context, uri: Uri): BackupResult {
        return try {
            val backup = VaultBackup(
                version    = 1,
                exportedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date()),
                films       = repo.films.first(),
                cameras     = repo.cameras.first(),
                lenses      = repo.lenses.first(),
                accessories = repo.accessories.first(),
                rolls       = repo.rolls.first(),
                chemicals   = repo.chemicals.first(),
                zoomLevels  = repo.zoomLevels.first(),
                owmKey      = repo.getSetting("owm_key") ?: ""
            )
            val json = gson.toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return BackupResult.Error("Could not open output stream")

            val counts = "${backup.rolls.size} rolls, ${backup.films.size} films, " +
                    "${backup.cameras.size} cameras, ${backup.chemicals.size} chemicals"
            BackupResult.Success("Exported: $counts")
        } catch (e: Exception) {
            BackupResult.Error("Export failed: ${e.message}")
        }
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    suspend fun import(context: Context, uri: Uri): BackupResult {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return BackupResult.Error("Could not read file")

            val backup = gson.fromJson(json, VaultBackup::class.java)
                ?: return BackupResult.Error("Invalid backup file")

            if (backup.version != 1) {
                return BackupResult.Error("Unsupported backup version ${backup.version}")
            }

            // Upsert everything — existing records with same ID get overwritten,
            // new ones get inserted. Nothing is deleted so partial imports are safe.
            backup.films.forEach       { repo.upsertFilm(it) }
            backup.cameras.forEach     { repo.upsertCamera(it) }
            backup.lenses.forEach      { repo.upsertLens(it) }
            backup.accessories.forEach { repo.upsertAccessory(it) }
            backup.rolls.forEach       { repo.upsertRoll(it) }
            backup.chemicals.forEach   { repo.upsertChemical(it) }
            backup.zoomLevels.forEach  { repo.upsertZoomLevel(it) }
            if (backup.owmKey.isNotBlank()) repo.setSetting("owm_key", backup.owmKey)

            val counts = "${backup.rolls.size} rolls, ${backup.films.size} films, " +
                    "${backup.cameras.size} cameras, ${backup.chemicals.size} chemicals"
            BackupResult.Success("Imported: $counts")
        } catch (e: Exception) {
            BackupResult.Error("Import failed: ${e.message}")
        }
    }
}

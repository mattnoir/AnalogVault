package com.analogvault.data.backup

import android.content.Context
import android.net.Uri
import com.analogvault.data.model.*
import com.analogvault.data.repo.VaultRepository
import com.analogvault.util.photoDir
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// ── Backup envelope ───────────────────────────────────────────────────────────

data class VaultBackup(
    val version: Int = 3,
    val exportedAt: String = "",
    val films: List<FilmStock> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val lenses: List<Lens> = emptyList(),
    val accessories: List<Accessory> = emptyList(),
    val rolls: List<Roll> = emptyList(),
    val bulkRolls: List<BulkRoll> = emptyList(),
    val chemicals: List<Chemical> = emptyList(),
    val zoomLevels: List<ZoomLevel> = emptyList(),
    val owmKey: String = ""
    // photos are stored as separate files inside the ZIP — not embedded here
)

sealed class BackupResult {
    data class Success(val message: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

/**
 * Gson instantiates via Unsafe and never runs the constructor, so fields missing
 * from the JSON end up null even though the Kotlin types say non-null — touching
 * them mid-restore then crashes after some data has already been written.
 * The elvis operators look useless to the compiler but are load-bearing at runtime.
 */
@Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
private fun VaultBackup.sanitized(): VaultBackup = VaultBackup(
    version     = version,
    exportedAt  = exportedAt ?: "",
    // records without a primary key are corrupt — drop them rather than crash Room
    films       = (films ?: emptyList()).filter { it.id != null },
    cameras     = (cameras ?: emptyList()).filter { it.id != null },
    lenses      = (lenses ?: emptyList()).filter { it.id != null },
    accessories = (accessories ?: emptyList()).filter { it.id != null },
    rolls       = (rolls ?: emptyList()).filter { it.id != null }.map { roll ->
        roll.copy(
            shots = (roll.shots ?: emptyList()).map { s ->
                Shot(
                    id = s.id ?: "", shutter = s.shutter ?: "", aperture = s.aperture ?: "",
                    iso = s.iso ?: "", lens = s.lens ?: "", location = s.location ?: "",
                    notes = s.notes ?: "", weather = s.weather ?: "", date = s.date ?: "",
                    photoThumbPath = s.photoThumbPath ?: ""
                )
            },
            devLog = roll.devLog?.let { d ->
                DevLog(process = d.process ?: "", developer = d.developer ?: "",
                    dilution = d.dilution ?: "", temp = d.temp ?: "",
                    devTime = d.devTime ?: "", notes = d.notes ?: "")
            },
            scanLog = roll.scanLog?.let { s ->
                ScanLog(method = s.method ?: "", dpi = s.dpi ?: "",
                    software = s.software ?: "", notes = s.notes ?: "")
            }
        )
    },
    bulkRolls   = (bulkRolls ?: emptyList()).filter { it.id != null },
    chemicals   = (chemicals ?: emptyList()).filter { it.id != null },
    zoomLevels  = (zoomLevels ?: emptyList()).filter { it.id != null },
    owmKey      = owmKey ?: ""
)

// ── Key remapping for obfuscated v2 backups (R8 renamed VaultBackup fields) ──

private val OBFUSCATED_KEY_MAP = mapOf(
    "a" to "version",
    "b" to "exportedAt",
    "c" to "films",
    "d" to "cameras",
    "e" to "lenses",
    "f" to "accessories",
    "g" to "rolls",
    "h" to "chemicals",
    "i" to "zoomLevels",
    "j" to "owmKey",
    "k" to "photos"   // v2 legacy — base64 map; handled separately on import
)

/** If the root JSON object looks obfuscated (single-letter keys), remap it.
 *  Uses a lightweight prefix scan — reads only the first key to decide,
 *  never builds a DOM of the full document. */
private fun isObfuscated(json: String): Boolean {
    // Find the first string key after the opening brace
    val start = json.indexOf('"')
    if (start < 0) return false
    val end = json.indexOf('"', start + 1)
    if (end < 0) return false
    val firstKey = json.substring(start + 1, end)
    return firstKey.length == 1 && firstKey[0] in 'a'..'k'
}

// ── Manager ───────────────────────────────────────────────────────────────────

@Singleton
class BackupManager @Inject constructor(
    private val repo: VaultRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ── Export ──────────────────────────────────────────────────────────────

    // File I/O runs on Dispatchers.IO — these are called from viewModelScope (Main),
    // and a photo-heavy backup would otherwise freeze the UI / ANR.
    suspend fun export(context: Context, uri: Uri, includePhotos: Boolean = true): BackupResult = withContext(Dispatchers.IO) {
        try {
            val rolls = repo.rolls.first()

            val backup = VaultBackup(
                version     = 3,
                exportedAt  = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date()),
                films       = repo.films.first(),
                cameras     = repo.cameras.first(),
                lenses      = repo.lenses.first(),
                accessories = repo.accessories.first(),
                rolls       = rolls,
                bulkRolls   = repo.bulkRolls.first(),
                chemicals   = repo.chemicals.first(),
                zoomLevels  = repo.zoomLevels.first(),
                owmKey      = repo.getSetting("owm_key") ?: ""
            )

            // Collect photo paths referenced by shots
            val photoPaths = if (includePhotos) {
                rolls.flatMap { roll ->
                    roll.shots.mapNotNull { shot ->
                        shot.photoThumbPath.takeIf { it.isNotBlank() }
                    }
                }.distinct()
            } else emptyList()

            context.contentResolver.openOutputStream(uri)?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zos ->

                    // 1. backup.json (no embedded photos)
                    zos.putNextEntry(ZipEntry("backup.json"))
                    zos.write(gson.toJson(backup).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. One file per photo, under photos/
                    var photoCount = 0
                    for (path in photoPaths) {
                        try {
                            val file = File(path)
                            if (!file.exists()) continue
                            zos.putNextEntry(ZipEntry("photos/${file.name}"))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            photoCount++
                        } catch (_: Exception) {}
                    }

                    val photoNote = if (includePhotos && photoCount > 0) ", $photoCount photos" else ""
                    return@withContext BackupResult.Success(
                        "Exported: ${backup.rolls.size} rolls, ${backup.bulkRolls.size} bulk rolls, " +
                        "${backup.films.size} films, ${backup.cameras.size} cameras$photoNote"
                    )
                }
            } ?: return@withContext BackupResult.Error("Could not open output stream")

        } catch (e: Exception) {
            BackupResult.Error("Export failed: ${e.message}")
        }
    }

    // ── Import ──────────────────────────────────────────────────────────────

    suspend fun import(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupResult.Error("Could not read file")

            inputStream.buffered().use { buffered ->
                // Peek at the first two bytes to detect ZIP vs JSON without loading the whole file
                buffered.mark(4)
                val magic = ByteArray(2)
                val read = buffered.read(magic)
                buffered.reset()
                val isZip = read == 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()

                if (isZip) importZip(context, buffered)
                else importLegacyJson(context, buffered.reader(Charsets.UTF_8).readText())
            }
        } catch (e: Exception) {
            BackupResult.Error("Import failed: ${e.message}")
        }
    }

    // ── ZIP import (v3+) ────────────────────────────────────────────────────
    // Streams entries straight off the (content-resolver) input stream — the
    // archive is never held in memory, so photo-heavy backups can't OOM.

    private suspend fun importZip(context: Context, input: InputStream): BackupResult {
        var jsonText: String? = null
        val photoDir = photoDir(context)
        val restoredPaths = mutableMapOf<String, String>() // filename → absolute path
        var photosRestored = 0

        val zis = ZipInputStream(input) // not .use{} — the caller owns and closes the stream
        var entry = zis.nextEntry
        while (entry != null) {
            when {
                entry.name == "backup.json" -> {
                    jsonText = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry.name.startsWith("photos/") && !entry.isDirectory -> {
                    // File(...).name strips any path segments — zip-slip safe
                    val name = File(entry.name).name
                    val dest = File(photoDir, name)
                    dest.outputStream().use { zis.copyTo(it) }
                    restoredPaths[name] = dest.absolutePath
                    photosRestored++
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        val json = jsonText ?: return BackupResult.Error("backup.json not found in archive")
        // Gson can leave non-null Kotlin fields null on malformed/foreign JSON — sanitize
        val backup = gson.fromJson(json, VaultBackup::class.java)?.sanitized()
            ?: return BackupResult.Error("Invalid backup.json")

        val remappedRolls = backup.rolls.map { roll ->
            roll.copy(shots = roll.shots.map { shot ->
                val name = File(shot.photoThumbPath).name
                val newPath = restoredPaths[name]
                if (newPath != null) shot.copy(photoThumbPath = newPath) else shot
            })
        }

        restoreData(backup, remappedRolls)

        val photoNote = if (photosRestored > 0) ", $photosRestored photos" else ""
        return BackupResult.Success(
            "Imported: ${backup.rolls.size} rolls, ${backup.bulkRolls.size} bulk rolls, ${backup.films.size} films, " +
            "${backup.cameras.size} cameras$photoNote"
        )
    }

    // ── Legacy JSON import (v1 / v2, including obfuscated builds) ──────────
    //
    // We MUST NOT call JsonParser.parseString() on the raw string — for a backup
    // that embeds photos as base64, that's 50MB+ loaded into a DOM tree all at once,
    // which OOMs on devices with a 192MB heap limit.
    //
    // Instead we stream through the JSON once with JsonReader:
    //   • non-photo top-level values → accumulated into a plain JsonObject for Gson
    //   • the "photos"/"k" value     → each base64 string decoded and streamed to
    //                                   disk one photo at a time (max ~6MB at a time)

    private suspend fun importLegacyJson(context: Context, raw: String): BackupResult {
        val obfuscated = isObfuscated(raw)
        val photoDir = photoDir(context)
        val pathMap = mutableMapOf<String, String>()  // oldPath → newAbsPath
        var photosRestored = 0

        // Reconstruct a clean JsonObject of all non-photo fields
        val envelope = JsonObject()

        JsonReader(raw.reader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val rawKey = reader.nextName()
                val realKey = if (obfuscated) OBFUSCATED_KEY_MAP[rawKey] ?: rawKey else rawKey

                when (realKey) {
                    "photos" -> {
                        // Stream each photo entry: decode base64 → file without holding
                        // the full string in memory as a parsed JsonElement
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val oldPath = reader.nextName()
                                val b64 = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); null }
                                if (b64 != null) {
                                    try {
                                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                        val dest = File(photoDir, File(oldPath).name)
                                        dest.writeBytes(bytes)
                                        pathMap[oldPath] = dest.absolutePath
                                        photosRestored++
                                    } catch (_: Exception) {}
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> {
                        // Parse this field as a JsonElement and add under its real key
                        val elem = gson.fromJson<com.google.gson.JsonElement>(reader, com.google.gson.JsonElement::class.java)
                        envelope.add(realKey, elem)
                    }
                }
            }
            reader.endObject()
        }

        val backup = try {
            gson.fromJson(envelope, VaultBackup::class.java)?.sanitized()
        } catch (e: Exception) {
            return BackupResult.Error("Invalid backup file: ${e.message}")
        } ?: return BackupResult.Error("Invalid backup file")

        // version 0 = field absent (very old exports where Gson left the default) — treat as v1
        if (backup.version !in 0..3) {
            return BackupResult.Error("Unsupported backup version ${backup.version}")
        }

        val remappedRolls = if (pathMap.isNotEmpty()) {
            backup.rolls.map { roll ->
                roll.copy(shots = roll.shots.map { shot ->
                    pathMap[shot.photoThumbPath]?.let { shot.copy(photoThumbPath = it) } ?: shot
                })
            }
        } else backup.rolls

        restoreData(backup, remappedRolls)

        val photoNote = if (photosRestored > 0) ", $photosRestored photos" else ""
        return BackupResult.Success(
            "Imported: ${backup.rolls.size} rolls, ${backup.bulkRolls.size} bulk rolls, ${backup.films.size} films, " +
            "${backup.cameras.size} cameras$photoNote"
        )
    }

    // ── Common DB restore ───────────────────────────────────────────────────

    private suspend fun restoreData(backup: VaultBackup, rolls: List<Roll>) {
        backup.films.forEach       { repo.upsertFilm(it) }
        backup.cameras.forEach     { repo.upsertCamera(it) }
        backup.lenses.forEach      { repo.upsertLens(it) }
        backup.accessories.forEach { repo.upsertAccessory(it) }
        rolls.forEach              { repo.upsertRoll(it) }
        backup.bulkRolls.forEach   { repo.upsertBulkRoll(it) }
        backup.chemicals.forEach   { repo.upsertChemical(it) }
        backup.zoomLevels.forEach  { repo.upsertZoomLevel(it) }
        if (backup.owmKey.isNotBlank()) repo.setSetting("owm_key", backup.owmKey)
    }
}

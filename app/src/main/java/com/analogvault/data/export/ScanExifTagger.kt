package com.analogvault.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.analogvault.data.model.Camera
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Shot
import com.analogvault.ui.components.parseLatLon
import com.analogvault.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Display name for a SAF uri — used to sort scans into frame order. */
fun scanDisplayName(context: Context, uri: Uri): String =
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment ?: ""
    } catch (_: Exception) { uri.lastPathSegment ?: "" }

/** "yyyy-MM-dd HH:mm" / "yyyy-MM-dd" (shot.date) → EXIF "yyyy:MM:dd HH:mm:ss", or null. */
fun exifDateTime(shotDate: String): String? {
    val d = shotDate.trim()
    val datePart = d.take(10)
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(datePart)) return null
    val timePart = d.drop(11).take(5)
        .let { if (Regex("""\d{2}:\d{2}""").matches(it)) "$it:00" else "12:00:00" }
    return "${datePart.replace('-', ':')} $timePart"
}

/**
 * Writes the shot log's metadata (exposure, camera/lens, date, GPS, film) as
 * EXIF directly into the user's scanned files, so scans carry the same data a
 * digital camera would have embedded. JPEG only — ExifInterface can only write
 * JPEG reliably; other formats are counted as failures.
 */
@Singleton
class ScanExifTagger @Inject constructor() {

    suspend fun tag(
        context: Context,
        pairs: List<Pair<Uri, Shot>>,
        film: FilmStock?,
        camera: Camera?
    ): ExportResult = withContext(Dispatchers.IO) {
        var ok = 0
        var failed = 0
        pairs.forEach { (uri, shot) ->
            try {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    camera?.let {
                        if (it.brand.isNotBlank()) exif.setAttribute(ExifInterface.TAG_MAKE, it.brand)
                        if (it.name.isNotBlank())  exif.setAttribute(ExifInterface.TAG_MODEL, it.name)
                    }
                    if (shot.lens.isNotBlank())
                        exif.setAttribute(ExifInterface.TAG_LENS_MODEL, shot.lens)
                    shot.aperture.toDoubleOrNull()?.let {
                        exif.setAttribute(ExifInterface.TAG_F_NUMBER, it.toString())
                    }
                    if (shot.shutter.isNotBlank())
                        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,
                            Constants.evalShutter(shot.shutter).toString())
                    shot.iso.trim().toIntOrNull()?.let {
                        exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString())
                    }
                    exifDateTime(shot.date)?.let {
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it)
                        exif.setAttribute(ExifInterface.TAG_DATETIME, it)
                    }
                    parseLatLon(shot.location)?.let { (lat, lon) -> exif.setLatLong(lat, lon) }
                    val desc = listOfNotNull(
                        film?.name?.ifBlank { null },
                        shot.notes.ifBlank { null }
                    ).joinToString(" · ")
                    if (desc.isNotBlank()) exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Analog Vault")
                    exif.saveAttributes()
                    ok++
                } ?: failed++
            } catch (_: Exception) {
                failed++
            }
        }
        if (ok == 0)
            ExportResult.Error("No files tagged${if (failed > 0) " — $failed failed (JPEG only)" else ""}")
        else
            ExportResult.Success("EXIF written to $ok scan${if (ok != 1) "s" else ""}" +
                if (failed > 0) " ($failed failed)" else "")
    }
}

package com.analogvault.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.analogvault.data.model.Camera
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Roll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportResult {
    data class Success(val message: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/** RFC-4180 CSV escaping. */
fun csvEscape(v: String): String =
    if (v.any { it == '"' || it == ',' || it == '\n' || it == '\r' })
        "\"${v.replace("\"", "\"\"")}\"" else v

fun csvRow(vararg cells: String): String = cells.joinToString(",") { csvEscape(it) }

/**
 * Per-roll shot-log exports: CSV for spreadsheets and a printable PDF
 * "contact sheet" to archive alongside the physical negatives.
 * All I/O on Dispatchers.IO (called from viewModelScope/Main).
 */
@Singleton
class RollExporter @Inject constructor() {

    // ── CSV ──────────────────────────────────────────────────────────────────

    suspend fun writeCsv(
        context: Context, uri: Uri,
        roll: Roll, film: FilmStock?, camera: Camera?
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: return@withContext ExportResult.Error("Could not open output file")
            out.bufferedWriter().use { w ->
                w.appendLine(csvRow("Film", "Camera", "Loaded", "EI", "Frames", "Developed", "Scanned"))
                w.appendLine(csvRow(
                    film?.name ?: "Unknown", camera?.name ?: "Unknown", roll.startDate,
                    roll.pushIso.ifBlank { film?.iso?.toString() ?: "" },
                    roll.shots.size.toString(),
                    roll.devLog?.let { "${it.process} ${it.developer} ${it.dilution} ${it.temp}C ${it.devTime}min".trim() } ?: "",
                    roll.scanLog?.let { "${it.method} ${it.dpi}".trim() } ?: ""
                ))
                w.appendLine()
                w.appendLine(SHOT_HEADER)
                roll.shots.forEachIndexed { i, s ->
                    w.appendLine(csvRow(
                        (i + 1).toString(), s.date, s.shutter, s.aperture, s.iso,
                        s.lens, s.location, s.weather, s.notes
                    ))
                }
            }
            ExportResult.Success("Exported ${roll.shots.size} shots to CSV")
        } catch (e: Exception) {
            ExportResult.Error("CSV export failed: ${e.message}")
        }
    }

    /** All rolls in one flat CSV (roll/film/camera columns per row). */
    suspend fun writeAllCsv(
        context: Context, uri: Uri,
        rolls: List<Roll>, films: List<FilmStock>, cameras: List<Camera>
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: return@withContext ExportResult.Error("Could not open output file")
            var count = 0
            out.bufferedWriter().use { w ->
                w.appendLine(csvRow("Roll film", "Camera", "Loaded") + "," + SHOT_HEADER)
                rolls.forEach { roll ->
                    val filmName = films.find { it.id == roll.filmId }?.name ?: "Unknown"
                    val camName  = cameras.find { it.id == roll.cameraId }?.name ?: "Unknown"
                    roll.shots.forEachIndexed { i, s ->
                        w.appendLine(csvRow(
                            filmName, camName, roll.startDate,
                            (i + 1).toString(), s.date, s.shutter, s.aperture, s.iso,
                            s.lens, s.location, s.weather, s.notes
                        ))
                        count++
                    }
                }
            }
            ExportResult.Success("Exported $count shots from ${rolls.size} rolls to CSV")
        } catch (e: Exception) {
            ExportResult.Error("CSV export failed: ${e.message}")
        }
    }

    // ── PDF contact sheet ────────────────────────────────────────────────────

    suspend fun writePdf(
        context: Context, uri: Uri,
        roll: Roll, film: FilmStock?, camera: Camera?
    ): ExportResult = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            val pageW = 595; val pageH = 842   // A4 in PostScript points
            val margin = 36f
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f; color = 0xFF000000.toInt(); isFakeBoldText = true
            }
            val body  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF222222.toInt() }
            val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF777777.toInt() }
            val frame = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 0.7f; color = 0xFF999999.toInt()
            }

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas

            // Header (first page only)
            var y = margin + 14f
            canvas.drawText(film?.name ?: "Roll", margin, y, title); y += 16f
            buildList {
                add("Camera: ${camera?.name ?: "—"}   Loaded: ${roll.startDate}" +
                    if (roll.pushIso.isNotBlank()) "   EI ${roll.pushIso}" else "")
                roll.devLog?.let {
                    add("Dev: ${it.process} · ${it.developer} ${it.dilution} · ${it.temp}°C · ${it.devTime} min".trim())
                }
                roll.scanLog?.let {
                    add("Scan: ${it.method}${if (it.dpi.isNotBlank()) " · ${it.dpi} dpi" else ""}")
                }
                add("${roll.shots.size} frames")
            }.forEach { canvas.drawText(it, margin, y, body); y += 12f }
            y += 8f

            // Frame grid: 3 columns of thumbnail + metadata lines
            val cols = 3
            val gap = 10f
            val cellW = (pageW - margin * 2 - gap * (cols - 1)) / cols
            val thumbH = cellW * 0.66f
            val lineH = 10f
            val cellH = thumbH + lineH * 4 + 12f
            var col = 0

            roll.shots.forEachIndexed { i, shot ->
                if (col == 0 && y + cellH > pageH - margin) {
                    canvas.drawText("page $pageNum", pageW - margin - 40f, pageH - margin + 20f, faint)
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    y = margin
                }
                val x = margin + col * (cellW + gap)
                val rect = RectF(x, y, x + cellW, y + thumbH)
                decodeThumb(shot.photoThumbPath, cellW.toInt() * 2)?.let { bmp ->
                    canvas.drawBitmap(bmp, null, rect, null)
                    bmp.recycle()
                }
                canvas.drawRect(rect, frame)

                var ty = y + thumbH + lineH
                val exposure = listOfNotNull(
                    shot.shutter.ifBlank { null },
                    shot.aperture.ifBlank { null }?.let { "f/$it" },
                    shot.iso.ifBlank { null }?.let { "ISO $it" }
                ).joinToString(" · ")
                canvas.drawText("#${i + 1}${if (exposure.isNotBlank()) "  $exposure" else ""}", x, ty, body); ty += lineH
                canvas.drawText(shot.lens.take(34), x, ty, faint); ty += lineH
                canvas.drawText(shot.date, x, ty, faint); ty += lineH
                canvas.drawText(shot.notes.replace('\n', ' ').take(38), x, ty, faint)

                col++
                if (col == cols) { col = 0; y += cellH }
            }
            canvas.drawText("page $pageNum", pageW - margin - 40f, pageH - margin + 20f, faint)
            doc.finishPage(page)

            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                ?: return@withContext ExportResult.Error("Could not open output file")
            ExportResult.Success("Contact sheet PDF: ${roll.shots.size} frames, $pageNum page${if (pageNum != 1) "s" else ""}")
        } catch (e: Exception) {
            ExportResult.Error("PDF export failed: ${e.message}")
        } finally {
            doc.close()
        }
    }

    private fun decodeThumb(path: String, targetW: Int): Bitmap? {
        if (path.isBlank()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }
    }

    private companion object {
        val SHOT_HEADER = csvRow(
            "Frame", "Date", "Shutter", "Aperture", "ISO", "Lens", "Location", "Weather", "Notes"
        )
    }
}

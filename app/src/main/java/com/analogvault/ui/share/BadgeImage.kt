package com.analogvault.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.analogvault.R
import com.analogvault.ui.theme.DyeLayerColors
import com.analogvault.ui.theme.FilmColors
import com.analogvault.util.AchCategory
import com.analogvault.util.BadgeCardText
import java.io.File
import kotlin.random.Random

/**
 * A badge as an image to share: one frame of a contact sheet, in the palette
 * the app is wearing.
 *
 * Drawn with android.graphics rather than captured from the screen, so it is
 * the same picture whatever is open, at a size that posts well (4:5). Under
 * Safelight it is drawn in Dye Layer instead: a solid red picture is what the
 * darkroom needs, not what anyone else wants to look at.
 */
object BadgeImage {
    private const val W = 1080
    private const val H = 1350
    private const val AUTHORITY_SUFFIX = ".files"

    fun render(context: Context, text: BadgeCardText, category: AchCategory, palette: FilmColors): Bitmap {
        val c = if (palette.safelight) DyeLayerColors else palette
        val accent = when (category) {
            AchCategory.MILESTONE -> c.cyan
            AchCategory.CRAFT -> c.yellow
            AchCategory.SECRET -> c.magenta
        }.toArgb()
        val display = font(context, R.font.big_shoulders_display_black)
        val body = font(context, R.font.space_grotesk_regular)
        val mono = font(context, R.font.space_mono_regular)

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(c.void.toArgb())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // The strip: film base with a row of sprocket holes along each edge.
        val strip = RectF(60f, 150f, W - 60f, 1200f)
        fill.color = c.film.toArgb()
        canvas.drawRect(strip, fill)
        fill.color = c.void.toArgb()
        var x = strip.left + 28f
        while (x + 44f < strip.right - 20f) {
            canvas.drawRoundRect(RectF(x, strip.top + 22f, x + 44f, strip.top + 52f), 6f, 6f, fill)
            canvas.drawRoundRect(RectF(x, strip.bottom - 52f, x + 44f, strip.bottom - 22f), 6f, 6f, fill)
            x += 78f
        }

        // The frame itself.
        val frame = RectF(110f, 270f, W - 110f, 1080f)
        fill.color = c.filmRaised.toArgb()
        canvas.drawRect(frame, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = accent
        }
        canvas.drawRect(frame, stroke)

        // The edge print above the frame: its number, as a contact sheet has.
        val rebate = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono; textSize = 30f; letterSpacing = 0.18f; color = accent
        }
        canvas.drawText(text.frameMark, frame.left, frame.top - 26f, rebate)

        val inner = (frame.width() - 120f).toInt()
        val left = frame.left + 60f
        var y = frame.top + 70f

        val eyebrow = TextPaint(rebate).apply { textSize = 30f; color = accent }
        canvas.drawText(text.eyebrow, left, y, eyebrow)
        y += 40f

        // Titles run from "BULK CUTTER" to "SAME SPOT, DIFFERENT YEAR": shrink
        // until it fits in three lines rather than spilling into the text.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; color = c.halide.toArgb() }
        var size = 132f
        var title: StaticLayout
        do {
            titlePaint.textSize = size
            title = layout(text.title, titlePaint, inner, maxLines = 4, spacing = 0.92f)
            size -= 8f
        } while (title.lineCount > 3 && size > 60f)
        y = drawLayout(canvas, title, left, y)
        y += 30f

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = body; textSize = 42f; color = c.halide.toArgb()
        }
        drawLayout(canvas, layout(text.description, bodyPaint, inner, maxLines = 4, spacing = 1.15f), left, y)

        // When and on what, pinned to the foot of the frame.
        val detailPaint = TextPaint(rebate).apply { textSize = 28f; letterSpacing = 0.12f }
        var dy = frame.bottom - 60f - (text.details.size - 1) * 46f
        text.details.forEachIndexed { i, line ->
            detailPaint.color = if (i == 0) accent else c.dim.toArgb()
            drawFitted(canvas, line, detailPaint, left, dy, inner.toFloat())
            dy += 46f
        }

        // The rebate under the strip.
        val foot = TextPaint(rebate).apply { textSize = 28f; color = c.dim.toArgb() }
        drawFitted(canvas, text.rebate, foot, strip.left, strip.bottom + 70f, strip.width())

        grain(canvas)
        return bmp
    }

    /**
     * Writes [bitmap] to the app's cache and opens the share sheet for it,
     * where saving to the gallery sits beside every app it can go to.
     */
    fun share(context: Context, bitmap: Bitmap, fileName: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // One at a time: the last shared image is the only one worth keeping.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share badge").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * One line of text, shrunk until it fits [width] rather than cut short:
     * an edge print or a date losing its last characters reads as a mistake.
     * Below 18 px it gives up and ellipsizes.
     */
    private fun drawFitted(canvas: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float) {
        val p = TextPaint(paint)
        while (p.measureText(text) > width && p.textSize > 18f) {
            p.textSize -= 1f
            p.letterSpacing = (p.letterSpacing - 0.005f).coerceAtLeast(0.04f)
        }
        canvas.drawText(TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString(), x, y, p)
    }

    private fun font(context: Context, id: Int): Typeface =
        ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    /** Draws [layout] with its top at [y]; returns the y under it. */
    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float): Float {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    /** The app's grain, printed into the image so it reads as a frame of film, not a screenshot. */
    private fun grain(canvas: Canvas) {
        val paint = Paint()
        val rnd = Random(7)
        repeat(W * H / 40) {
            val v = rnd.nextInt(256)
            paint.color = android.graphics.Color.argb(14, v, v, v)
            canvas.drawPoint(rnd.nextInt(W).toFloat(), rnd.nextInt(H).toFloat(), paint)
        }
    }
}

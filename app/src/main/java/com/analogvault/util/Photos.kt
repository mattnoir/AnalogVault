package com.analogvault.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * App-internal directory for shot photos. Lives in filesDir — NOT cacheDir —
 * because the OS is free to purge cache at any time, which silently deleted
 * users' shot photos in builds ≤ 0.4.0.
 */
fun photoDir(context: Context): File = File(context.filesDir, "camera_photos").also { it.mkdirs() }

/** Legacy location used by builds ≤ 0.4.0; migrated to [photoDir] on startup. */
fun legacyPhotoCacheDir(context: Context): File = File(context.cacheDir, "camera_photos")

private const val MAX_THUMB_DIM = 1280
private const val THUMB_JPEG_QUALITY = 85

/**
 * Downscale [file] in place to at most [MAX_THUMB_DIM] px on the long edge,
 * applying EXIF rotation. Shot thumbnails don't need sensor resolution; this
 * keeps storage and backup ZIPs small. No-op on failure or already-small images.
 */
fun downscalePhotoInPlace(file: File) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return

        val rotation = when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (maxOf(w, h) <= MAX_THUMB_DIM && rotation == 0f) return

        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= MAX_THUMB_DIM) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return

        val scale = (MAX_THUMB_DIM.toFloat() / maxOf(bmp.width, bmp.height)).coerceAtMost(1f)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, it) }
        if (out !== bmp) bmp.recycle()
        out.recycle()
    } catch (_: Exception) {
        // keep the original file untouched on any failure
    }
}

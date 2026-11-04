package com.analogvault.data.camera

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size
import android.util.SizeF
import kotlin.math.hypot

/**
 * What the phone's back camera says about its own glass, turned into the one
 * number the meter's focal presets need: the 35 mm-equivalent focal length of
 * the camera at a zoom ratio of 1.0×.
 *
 * Every preset is a zoom ratio, and a ratio is only a focal length once it is
 * multiplied by that number. It used to be a hard-coded 23 mm, which is right
 * for some phones and several millimetres off for others.
 *
 * The characteristics that go into it:
 *  - `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` — the real focal length of the lens, mm.
 *  - `SENSOR_INFO_PHYSICAL_SIZE` — the size of the whole pixel array, mm.
 *  - `SENSOR_INFO_PIXEL_ARRAY_SIZE` and `SENSOR_INFO_ACTIVE_ARRAY_SIZE` — the
 *    image at 1.0× covers only the active array, so the sensor is cropped to it
 *    before the crop factor is taken.
 *  - `LENS_INTRINSIC_CALIBRATION` with `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE`
 *    — a focal length in pixels; the fallback when the sensor size is missing.
 *  - `CONTROL_ZOOM_RATIO_RANGE` (API 30+) or `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`
 *    — which ratios the camera can actually reach.
 *
 * The crop factor is the usual diagonal one (43.27 mm over the sensor's
 * diagonal), the same convention phone makers use for their "23 mm" or
 * "26 mm" figures.
 */
data class CameraOptics(
    /** Camera2 id these numbers were read from. */
    val cameraId: String,
    /** 35 mm-equivalent focal length at 1.0×, or null when the phone does not say. */
    val equivMm: Double?,
    /** Real focal length of the lens, mm. */
    val focalMm: Double?,
    /** Width × height of the image area at 1.0×, mm. */
    val sensorMm: Pair<Double, Double>?,
    /** 43.27 mm over the sensor diagonal. */
    val cropFactor: Double?,
    /** How [equivMm] was found: "sensor", "calibration", or null. */
    val source: String?,
    val minZoom: Float,
    val maxZoom: Float,
) {
    companion object {
        /** The diagonal of a 36 × 24 mm frame. */
        const val FULL_FRAME_DIAGONAL_MM = 43.2666

        /**
         * What every preset assumed before the phone was asked. Still used to
         * place presets when the phone has no lens data, with a warning.
         */
        const val FALLBACK_EQUIV_MM = 23.0

        /** Outside this, a computed equivalent is a broken HAL, not a lens. */
        private val PLAUSIBLE_EQUIV_MM = 8.0..300.0

        /** Every back-facing camera an app may open, in the manager's order. */
        fun detectAll(context: Context): List<CameraOptics> = try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            mgr.cameraIdList.mapNotNull { id ->
                try {
                    val c = mgr.getCameraCharacteristics(id)
                    val back = c.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
                    val usable = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                    ) != false
                    if (back && usable) from(id, c) else null
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }

        fun from(id: String, c: CameraCharacteristics): CameraOptics {
            val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
            val maxDigital = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            return compute(
                cameraId        = id,
                focalLengths    = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS),
                physicalSize    = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
                pixelArray      = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE),
                activeArray     = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                preCorrection   = c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE),
                intrinsic       = c.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION),
                minZoom         = zoomRange?.lower ?: 1f,
                maxZoom         = zoomRange?.upper ?: maxDigital ?: 1f,
            )
        }

        fun compute(
            cameraId: String,
            focalLengths: FloatArray?,
            physicalSize: SizeF?,
            pixelArray: Size?,
            activeArray: Rect?,
            preCorrection: Rect?,
            intrinsic: FloatArray?,
            minZoom: Float,
            maxZoom: Float,
        ): CameraOptics {
            // A logical multi-camera can list several; the first is the one the
            // default request uses, which is what 1.0× shows.
            val focal = focalLengths?.firstOrNull { it > 0f && it.isFinite() }?.toDouble()

            val sensor = physicalSize
                ?.takeIf { it.width > 0f && it.height > 0f }
                ?.let { phys ->
                    val w = phys.width.toDouble()
                    val h = phys.height.toDouble()
                    // The physical size covers the whole pixel array; 1.0× is the
                    // active array inside it.
                    if (pixelArray != null && activeArray != null &&
                        pixelArray.width > 0 && pixelArray.height > 0 &&
                        activeArray.width() > 0 && activeArray.height() > 0
                    ) {
                        w * activeArray.width() / pixelArray.width to
                            h * activeArray.height() / pixelArray.height
                    } else w to h
                }

            val crop = sensor?.let { (w, h) -> FULL_FRAME_DIAGONAL_MM / hypot(w, h) }
            val fromSensor = if (focal != null && crop != null) focal * crop else null

            // Intrinsic calibration gives the focal length in pixels of the
            // pre-correction array, so no sensor size is needed at all.
            val fromCalibration = intrinsic
                ?.takeIf { it.size >= 2 && it[0] > 0f && it[1] > 0f }
                ?.let { k ->
                    val arr = preCorrection ?: activeArray
                    arr?.takeIf { it.width() > 0 && it.height() > 0 }?.let { r ->
                        val fPx = (k[0] + k[1]) / 2.0
                        fPx * FULL_FRAME_DIAGONAL_MM / hypot(r.width().toDouble(), r.height().toDouble())
                    }
                }

            val (equiv, source) = when {
                fromSensor != null && fromSensor in PLAUSIBLE_EQUIV_MM -> fromSensor to "sensor"
                fromCalibration != null && fromCalibration in PLAUSIBLE_EQUIV_MM -> fromCalibration to "calibration"
                else -> null to null
            }

            val lo = minZoom.takeIf { it > 0f && it.isFinite() } ?: 1f
            val hi = maxZoom.takeIf { it >= lo && it.isFinite() } ?: lo
            return CameraOptics(
                cameraId   = cameraId,
                equivMm    = equiv,
                focalMm    = focal,
                sensorMm   = sensor,
                cropFactor = crop,
                source     = source,
                minZoom    = lo,
                maxZoom    = hi,
            )
        }
    }
}

/**
 * The phone's back cameras as one zoom range, the way the stock camera app
 * presents them: 1.0x is the main camera (the one CameraX binds by default),
 * an ultrawide covers what is below 1.0x and each telephoto takes over from the
 * main camera's digital zoom once the zoom reaches its own focal length.
 *
 * Many phones (Samsung among them) do not hand third-party apps a logical
 * camera that switches lenses by itself — the ultrawide and the teles are
 * separate camera ids, each with its own zoom range. Zooms are therefore
 * "total" ratios relative to the main camera at 1.0x, and [pick] says which
 * camera to bind and what ratio to set on it.
 */
data class CameraRig(
    val main: CameraOptics,
    /** Cameras the zoom switches between, main included, widest first. */
    val lenses: List<CameraOptics>,
) {
    /** A lens's 1.0x expressed as a total ratio; the main camera is 1.0. */
    fun baseRatio(lens: CameraOptics): Double {
        val m = main.equivMm
        val e = lens.equivMm
        return if (lens.cameraId == main.cameraId || m == null || e == null) 1.0 else e / m
    }

    val minZoom: Double get() = lenses.minOf { baseRatio(it) * it.minZoom }
    val maxZoom: Double get() = lenses.maxOf { baseRatio(it) * it.maxZoom }

    fun inRange(total: Double) = total >= minZoom - 0.01 && total <= maxZoom + 0.01

    /**
     * The camera to bind for [total] and the ratio to set on it: the longest
     * lens whose widest view still fits — real glass beats digital zoom — or
     * the widest lens when nothing does.
     */
    fun pick(total: Double): Pair<CameraOptics, Float> {
        val lens = lenses.filter { baseRatio(it) * it.minZoom <= total + 0.005 }
            .maxByOrNull { baseRatio(it) }
            ?: lenses.minBy { baseRatio(it) * it.minZoom }
        val local = (total / baseRatio(lens)).toFloat().coerceIn(lens.minZoom, lens.maxZoom)
        return lens to local
    }

    /** "ULTRAWIDE", "MAIN", "TELE" — what the stock camera app would call it. */
    fun kind(lens: CameraOptics): String = when {
        lens.cameraId == main.cameraId -> "MAIN"
        baseRatio(lens) < 1.0 -> "ULTRAWIDE"
        else -> "TELE"
    }

    companion object {
        /**
         * Below this a camera counts as an ultrawide, above [TELE_FROM] as a
         * telephoto. Anything in between (a macro or depth camera with a
         * main-like field of view) is left out, so zooming to 1.1x never lands
         * on a 2 MP macro sensor.
         */
        private const val WIDE_BELOW = 0.85
        private const val TELE_FROM = 1.5

        fun detect(context: Context): CameraRig? = of(CameraOptics.detectAll(context))

        fun of(all: List<CameraOptics>): CameraRig? {
            val main = all.firstOrNull() ?: return null
            val m = main.equivMm
            // Without the main camera's equivalent there is no way to line the
            // others up against it: the main camera alone, as before.
            if (m == null) return CameraRig(main, listOf(main))
            val others = all.drop(1)
                .filter { it.equivMm != null }
                .filter { val r = it.equivMm!! / m; r < WIDE_BELOW || r >= TELE_FROM }
                // Some phones list the same lens twice (a logical camera and
                // its physical one); keep the first of each focal length.
                .distinctBy { Math.round(it.equivMm!!) }
                // A lens the main camera's own range already reaches adds nothing.
                .filter { val r = it.equivMm!! / m; r < main.minZoom - 0.05 || r > 1.0 }
            return CameraRig(main, (others + main).sortedBy { it.equivMm })
        }
    }
}

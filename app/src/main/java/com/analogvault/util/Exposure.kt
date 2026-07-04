package com.analogvault.util

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow

/**
 * Pure exposure/optics math shared by the light meter, DOF calculator and
 * reciprocity helper. No Android dependencies — unit-testable on the JVM.
 */
object Exposure {

    /** Scene EV (at ISO 100 reference) from the sensor's exposure parameters. */
    fun evFromSensor(iso: Int, shutterSec: Double, aperture: Double): Double =
        log2((aperture * aperture) / shutterSec) - log2(iso / 100.0)

    /** Shutter time in seconds for a fixed aperture at the given scene EV and film ISO. */
    fun solveShutterSec(iso: Int, aperture: Double, ev: Double): Double =
        (aperture * aperture) / (2.0.pow(ev) * (iso / 100.0))

    /** Nearest standard shutter-speed string (log-space nearest; "B" excluded). */
    fun nearestStandardShutter(sec: Double): String =
        Constants.SHUTTER_SPEEDS.filter { it != "B" }
            .minByOrNull { abs(ln(Constants.evalShutter(it)) - ln(sec.coerceAtLeast(1e-6))) }
            ?: "1/125"

    /**
     * Zone System placement. Zone V is middle gray; placing the metered area on
     * Zone III means "render it 2 stops darker than the meter suggests", i.e.
     * +2 EV (higher EV = less exposure). Zone VII → −2 EV.
     */
    fun zoneOffsetEv(zone: Int): Int = 5 - zone

    /**
     * Schwarzschild reciprocity-failure correction: t' = t^p for t > 1 s
     * (film sensitivity drops during long exposures). Identity at or below 1 s.
     */
    fun reciprocityCorrect(meteredSec: Double, factor: Double): Double =
        if (meteredSec <= 1.0) meteredSec else meteredSec.pow(factor)

    // ── Depth of field ───────────────────────────────────────────────────────

    /** Hyperfocal distance in mm. */
    fun hyperfocalMm(focalMm: Double, aperture: Double, cocMm: Double): Double =
        focalMm * focalMm / (aperture * cocMm) + focalMm

    /**
     * Near/far focus limits in metres for a subject at [subjectM] metres.
     * far == null means infinity (subject at or beyond hyperfocal distance).
     */
    fun dofNearFar(
        focalMm: Double, aperture: Double, subjectM: Double, cocMm: Double
    ): Pair<Double, Double?> {
        val h = hyperfocalMm(focalMm, aperture, cocMm)
        val s = subjectM * 1000.0
        val near = h * s / (h + (s - focalMm))
        val far = if (s >= h) null else h * s / (h - (s - focalMm))
        return near / 1000.0 to far?.let { it / 1000.0 }
    }

    /** Conventional circle-of-confusion (mm) per shooting format. */
    fun cocForFormat(format: String): Double = when {
        format.startsWith("6x4.5") -> 0.045
        format.startsWith("6x6")   -> 0.053
        format.startsWith("6x7")   -> 0.053
        format.startsWith("6x9")   -> 0.059
        format.startsWith("6x17")  -> 0.090
        format.startsWith("4x5")   -> 0.100
        format.startsWith("110")   -> 0.015
        else                       -> 0.030   // 135 / 35mm
    }

    /** Formats supported by the DOF calculator (order = UI order). */
    val DOF_FORMATS = listOf("35mm", "6x4.5", "6x6", "6x7", "6x9", "6x17", "4x5", "110")
}

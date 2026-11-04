package com.analogvault.util

import kotlin.math.abs
import kotlin.math.log2

/**
 * The f-numbers a lens's aperture ring actually clicks to.
 *
 * A lens is a fixed set of stops: an f/1.8 lens with whole-stop clicks offers
 * f/1.8, 2, 2.8, 4, 5.6, 8, 11, 16 and nothing in between, whatever the light
 * does. The meter used to snap every answer to the third-stop scale, so the
 * same ladder read f/5.6–f/8–f/11 in one room and f/5–f/7.1–f/10 in the next —
 * numbers no ring on a full-stop lens is engraved with. Snapping to the lens's
 * own scale keeps the offered stops fixed; what moves with the light is how far
 * each one is off the reading.
 *
 * No Android dependencies — unit-testable on the JVM.
 */
object ApertureScale {

    /** How far apart the ring's clicks are. Stored on gear by [key]. */
    enum class Step(val key: String, val label: String) {
        FULL("full", "Full stops"),
        HALF("half", "Half stops"),
        THIRD("third", "Third stops");

        companion object {
            /** Unknown or blank keys read as [DEFAULT], the way old rows were never told otherwise. */
            fun of(key: String?): Step = entries.firstOrNull { it.key == key } ?: DEFAULT
            fun ofLabel(label: String): Step = entries.firstOrNull { it.label == label } ?: DEFAULT
        }
    }

    /**
     * Whole stops: the clicks on most manual lenses, and what the meter falls
     * back to when it has no lens to ask. A third-stop default would bring back
     * the drifting f/5–f/7.1 ladder on every lens nobody had edited.
     */
    val DEFAULT = Step.FULL

    private val FULL_STOPS = listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0)
    private val HALF_STOPS = listOf(
        1.0, 1.2, 1.4, 1.7, 2.0, 2.4, 2.8, 3.3, 4.0, 4.8, 5.6, 6.7,
        8.0, 9.5, 11.0, 13.0, 16.0, 19.0, 22.0, 27.0, 32.0,
    )

    /** The engraved values for [step], f/0.95 to f/32. */
    fun standard(step: Step): List<Double> = when (step) {
        Step.FULL  -> FULL_STOPS
        Step.HALF  -> HALF_STOPS
        Step.THIRD -> Constants.APERTURES
    }

    /** Lowest and highest f-number a custom stop may take: anything outside is a typo. */
    private const val CUSTOM_MIN = 0.5
    private const val CUSTOM_MAX = 128.0

    /**
     * Custom stops from free text: "4.5, 6.3", "f/3.5 f/4.5", "4,5; 6,3" — a
     * comma between digits with no space is a decimal comma, everything else
     * separates. Unparseable and out-of-range pieces are dropped, and the result
     * is sorted and de-duplicated.
     */
    fun parseCustom(text: String): List<Double> =
        text.replace(Regex("(?<=\\d),(?=\\d)"), ".")
            .split(Regex("[^0-9.]+"))
            .mapNotNull { it.trim('.').toDoubleOrNull() }
            .filter { it in CUSTOM_MIN..CUSTOM_MAX }
            .distinct()
            .sorted()

    /** Stored strings ("4.5") to f-numbers, ignoring anything unreadable. */
    fun parseStored(values: List<String>?): List<Double> =
        values.orEmpty().mapNotNull { it.toDecimalOrNull() }.filter { it in CUSTOM_MIN..CUSTOM_MAX }

    /** Bare number as the gear fields store it ("8", "4.5"). */
    fun store(a: Double): String =
        if (a == a.toLong().toDouble()) a.toLong().toString() else a.toString()

    /**
     * Every stop the ring can be set to: the standard clicks for [step] between
     * [widest] and [narrowest], both ends themselves (an f/1.7 or f/3.5 wide
     * open sits off every standard scale), and the [custom] stops inside the
     * range. Null ends are open — the scale runs to f/0.95 or f/32.
     *
     * Custom stops outside a known range are left off: a ring cannot click past
     * its own end, and showing them would put a hazard stripe on a stop the user
     * said the lens has.
     */
    fun onLens(step: Step, widest: Double?, narrowest: Double?, custom: List<Double>): List<Double> {
        val lo = widest ?: Double.MIN_VALUE
        val hi = narrowest ?: Double.MAX_VALUE
        val inRange = { a: Double -> a >= lo * IN_RANGE_SLACK_LO && a <= hi * IN_RANGE_SLACK_HI }
        // Ends and custom stops first, so they win a tie with a standard click a
        // hair away (an f/1.8 wide open over the third-stop scale's own 1.8).
        val preferred = listOfNotNull(widest, narrowest) + custom.filter(inRange)
        val standard = standard(step).filter { it > lo * IN_RANGE_SLACK_HI && it < hi * IN_RANGE_SLACK_LO }
        return dedupe(preferred + standard)
    }

    /**
     * The scale the meter snaps a computed aperture to: the lens's own stops
     * inside its range, and the standard clicks for [step] outside it.
     *
     * The outside part is what lets the meter say "this rung needs f/1.4 and
     * your lens opens to f/2" — snapping only to the lens's stops would pull
     * that answer back to f/2 and hide that the frame is out of reach.
     */
    fun extended(step: Step, widest: Double?, narrowest: Double?, custom: List<Double>): List<Double> {
        val lens = onLens(step, widest, narrowest, custom)
        val outside = standard(step).filter {
            (widest != null && it < widest * IN_RANGE_SLACK_LO) ||
                (narrowest != null && it > narrowest * IN_RANGE_SLACK_HI)
        }
        return dedupe(lens + outside)
    }

    /** The stop on [scale] nearest [exact], in stops rather than in f-number. */
    fun snap(scale: List<Double>, exact: Double): Double =
        scale.minByOrNull { abs(log2(it) - log2(exact.coerceAtLeast(0.1))) } ?: exact

    /** Sorted, with values under a tenth of a stop apart merged into the first one seen. */
    private fun dedupe(values: List<Double>): List<Double> {
        val kept = mutableListOf<Double>()
        for (v in values) {
            if (kept.none { abs(log2(v / it)) < SAME_STOP }) kept += v
        }
        return kept.sorted()
    }

    /**
     * Under a tenth of a stop apart is the same click: f/1.7 and f/1.8 are not,
     * f/4.5 and f/4.6 are. In log2(N), where one stop is a half.
     */
    private const val SAME_STOP = 0.05

    // A hundredth of the f-number either way, as in GearClamp.apertureReason, so
    // a typed "22" and the scale's 22.0 meet.
    private const val IN_RANGE_SLACK_LO = 0.99
    private const val IN_RANGE_SLACK_HI = 1.01
}

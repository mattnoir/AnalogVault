package com.analogvault.util

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Development time as a function of temperature.
 *
 * Black-and-white and colour are treated differently on purpose, because they
 * are different kinds of process and pretending otherwise ruins film:
 *
 *  - **B&W** developers have a published time/temperature curve. Working a few
 *    degrees off 20 °C is normal practice — a tap-water bath in July is 24 °C —
 *    and the curve tells you what to do about it. [bwFactor] implements it.
 *
 *  - **C-41 and E-6** are fixed-temperature processes. Their times are specified
 *    at one temperature by the kit, with a tolerance measured in tenths of a
 *    degree, and the relationship at other temperatures is not a curve anyone
 *    should extrapolate — it is a table the manufacturer publishes for the
 *    handful of temperatures they have tested. This file therefore refuses to
 *    rescale them and reports the deviation instead. See [ProcessSpec].
 *
 * No Android dependencies — unit-testable on the JVM.
 */
object DevTime {

    /** The temperature B&W times are published at. */
    const val BW_REFERENCE_C = 20.0

    /** Sensible ends for a temperature control. Below 14 °C most developers stall. */
    val TEMP_RANGE_C = 14.0..42.0

    /**
     * Multiplier on the 20 °C time for a B&W developer at [tempC].
     *
     * This is Ilford's published time/temperature relationship, which runs at
     * about 8 % per degree — 21 °C is 0.92×, 24 °C is 0.72×, 16 °C is 1.42× —
     * i.e. a factor of 0.92 per degree above the reference. It halves the time
     * at roughly +8.3 °C, which is the familiar "a degree costs you 8 %".
     *
     * It is a fit to one family of curves, not a law. Deep into the cold it
     * understates how much developers slow down, which is why the UI stops
     * offering it below [TEMP_RANGE_C].
     */
    fun bwFactor(tempC: Double): Double = 0.92.pow(tempC - BW_REFERENCE_C)

    /** [baseSec] measured at 20 °C, corrected for [tempC]. */
    fun bwAdjustedSec(baseSec: Int, tempC: Double): Int =
        (baseSec * bwFactor(tempC)).roundToInt().coerceAtLeast(1)

    /**
     * What a process expects of its temperature.
     *
     * @param fixedC the one temperature the process runs at, or null for B&W,
     *   which has a curve instead.
     * @param toleranceC how far off [fixedC] the process still works. C-41's
     *   developer is specified at ±0.15 °C; the bleach and wash steps are far
     *   more forgiving, but the developer is the one that sets the standard.
     */
    data class ProcessSpec(
        val label: String,
        val fixedC: Double?,
        val toleranceC: Double = 0.0,
    ) {
        val isFixedTemperature: Boolean get() = fixedC != null
    }

    val BW  = ProcessSpec("B&W", fixedC = null)
    val C41 = ProcessSpec("C-41", fixedC = 38.0, toleranceC = 0.3)
    val E6  = ProcessSpec("E-6", fixedC = 38.0, toleranceC = 0.3)

    /** Match a preset or chemical name onto the process whose rules apply. */
    fun specFor(name: String): ProcessSpec {
        val n = name.lowercase()
        return when {
            "c-41" in n || "c41" in n || "colour negative" in n || "color negative" in n -> C41
            "e-6" in n || "e6" in n || "slide" in n -> E6
            else -> BW
        }
    }

    /**
     * How to describe a temperature to the user, given what the process wants.
     *
     * Returns null when there is nothing to say — a B&W developer at any
     * workable temperature, or a colour process sitting inside its tolerance.
     */
    fun warning(spec: ProcessSpec, tempC: Double): String? {
        val fixed = spec.fixedC ?: return null
        val off = tempC - fixed
        if (kotlin.math.abs(off) <= spec.toleranceC) return null
        val direction = if (off > 0) "above" else "below"
        return "${spec.label} runs at ${"%.1f".format(fixed)} °C ±${"%.1f".format(spec.toleranceC)}. " +
            "You are ${"%.1f".format(kotlin.math.abs(off))} °C $direction that — follow your kit's own " +
            "chart for this temperature rather than scaling the time, because colour times are " +
            "tabulated, not curved."
    }

    // ── Agitation ────────────────────────────────────────────────────────────

    /**
     * When to agitate during a step.
     *
     * Agitation is a rhythm, not a reminder: the first burst drives out air
     * bells and wets the emulsion evenly, and each later burst refreshes the
     * developer at the film surface. Modelling it as "initial then every N"
     * covers every scheme worth naming, including the ones that agitate not at
     * all.
     *
     * Later bursts fall on the step clock — at every multiple of [everySec]
     * after the initial agitation has ended — because that is how data sheets
     * write it: "10 s at the start of each minute" means at 1:00, 2:00, 3:00,
     * not a minute after the initial burst stopped.
     *
     * @param initialSec how long to agitate at the start of the step.
     * @param everySec gap between later bursts; 0 means no further agitation.
     * @param burstSec how long each later burst lasts.
     */
    data class Agitation(
        val initialSec: Int = 30,
        val everySec: Int = 60,
        val burstSec: Int = 10,
    ) {
        companion object {
            /** Standard B&W inversions: 30 s initial, 10 s each minute after. */
            val STANDARD = Agitation(30, 60, 10)
            /** Stand and semi-stand development: agitate once, then leave it. */
            val STAND = Agitation(60, 0, 0)
            /** Rotary and colour kits: never stop. */
            val CONTINUOUS = Agitation(Int.MAX_VALUE, 0, 0)
            /** Washes, stabiliser, wetting agent — nothing to time. */
            val NONE = Agitation(0, 0, 0)
        }

        val isContinuous: Boolean get() = initialSec == Int.MAX_VALUE

        private val hasBursts: Boolean get() = !isContinuous && everySec > 0 && burstSec > 0

        /**
         * Why this rhythm cannot be timed, or null when it can. A burst as long
         * as its interval never stops, which is continuous agitation written the
         * long way round, and a burst with no interval has nowhere to go.
         */
        val problem: String? get() = when {
            isContinuous -> null
            initialSec < 0 || everySec < 0 || burstSec < 0 -> "Times cannot be negative"
            burstSec > 0 && everySec == 0 -> "Set how often the later bursts come"
            everySec > 0 && burstSec == 0 -> "Set how long each later burst lasts"
            everySec > 0 && burstSec >= everySec -> "Each burst must be shorter than the interval"
            else -> null
        }

        /** Start of the first later burst: the first multiple of [everySec] after the initial ends. */
        private fun firstBurstStart(): Int = (initialSec / everySec + 1) * everySec

        /** True while [elapsedSec] falls inside an agitation window. */
        fun isAgitating(elapsedSec: Int): Boolean = when {
            isContinuous -> true
            elapsedSec < initialSec -> true
            !hasBursts || elapsedSec < firstBurstStart() -> false
            else -> elapsedSec % everySec < burstSec
        }

        /**
         * Every window inside a step of [durationSec], as start/end second pairs,
         * for drawing. Continuous agitation returns the whole step as one window.
         */
        fun windows(durationSec: Int): List<IntRange> {
            if (durationSec <= 0) return emptyList()
            if (isContinuous) return listOf(0 until durationSec)
            val out = mutableListOf<IntRange>()
            if (initialSec > 0) out += 0 until minOf(initialSec, durationSec)
            if (hasBursts) {
                var start = firstBurstStart()
                // Guard the loop on the step length rather than the count: a long
                // stand step with a short interval would otherwise build a list
                // nobody can see and nothing can draw.
                while (start < durationSec && out.size < 400) {
                    out += start until minOf(start + burstSec, durationSec)
                    start += everySec
                }
            }
            return out
        }

        /** Seconds until the next burst begins, or null when there is not one. */
        fun secondsToNext(elapsedSec: Int, durationSec: Int): Int? {
            if (!hasBursts) return null
            if (isAgitating(elapsedSec)) return 0
            val next = maxOf(firstBurstStart(), (elapsedSec / everySec + 1) * everySec)
            return if (next < durationSec) next - elapsedSec else null
        }

        /** The rhythm in words, e.g. "30 s, then 2 s every 15 s". */
        fun describe(): String = when {
            isContinuous -> "Continuously"
            initialSec == 0 && !hasBursts -> "No agitation"
            !hasBursts -> "${initialSec} s at the start, then leave it"
            initialSec == 0 -> "${burstSec} s every ${everySec} s"
            else -> "${initialSec} s, then ${burstSec} s every ${everySec} s"
        }
    }
}

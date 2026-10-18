package com.analogvault.util

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.ln

/**
 * What the shot log says about how someone actually shoots.
 *
 * Both histograms bucket to whole stops. Third-stop precision is right for
 * recording a frame and wrong for describing a habit: thirty buckets across a
 * phone's width says nothing, and "I live at f/8" is the finding.
 *
 * No Android dependencies — unit-testable on the JVM.
 */
object Habits {

    /** The stops on a classic aperture ring, which is what the ring can be set to. */
    val WHOLE_STOP_APERTURES = listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0)

    /**
     * Counts per bucket, in scale order, trimmed to the range actually used but
     * keeping the empty buckets inside it.
     *
     * The interior gaps are the point: a photographer with counts at f/2 and
     * f/11 and nothing between has a different habit from one spread evenly,
     * and closing the gap up would draw them identically.
     */
    private fun <T> histogram(
        values: List<T>,
        scale: List<T>,
        label: (T) -> String,
    ): List<Pair<String, Int>> {
        if (values.isEmpty()) return emptyList()
        val counts = scale.associateWith { 0 }.toMutableMap()
        values.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val used = scale.withIndex().filter { (_, v) -> (counts[v] ?: 0) > 0 }
        if (used.isEmpty()) return emptyList()
        return scale.subList(used.first().index, used.last().index + 1)
            .map { label(it) to (counts[it] ?: 0) }
    }

    /** Aperture strings as stored on shots ("8", "5.6", "f/2" tolerated). */
    fun apertureHistogram(raw: List<String>): List<Pair<String, Int>> {
        val snapped = raw.mapNotNull { s ->
            s.removePrefix("f/").removePrefix("F/").toDecimalOrNull()
                ?.takeIf { it > 0.0 }
                // Nearest in log space: apertures are geometric, so f/1.8 sits
                // closer to f/2 than a linear comparison against f/1.4 admits.
                ?.let { v -> WHOLE_STOP_APERTURES.minByOrNull { abs(log2(it) - log2(v)) } }
        }
        return histogram(snapped, WHOLE_STOP_APERTURES) { formatStop(it) }
    }

    /** Shutter strings as stored on shots ("1/125", "2s", "B"). */
    fun shutterHistogram(raw: List<String>): List<Pair<String, Int>> {
        val scale = Constants.WHOLE_STOP_SHUTTERS
        val snapped = raw.mapNotNull { s ->
            // Bulb is a decision, not a speed, and has no place on a scale of
            // them — it would land on whatever evalShutter happens to return.
            if (s.isBlank() || s == "B") return@mapNotNull null
            val sec = Constants.evalShutter(s).takeIf { it > 0.0 } ?: return@mapNotNull null
            scale.minByOrNull { abs(ln(Constants.evalShutter(it)) - ln(sec)) }
        }
        return histogram(snapped, scale) { it }
    }

    /** "8" for whole stops, "1.4" otherwise — the number as a ring engraves it. */
    private fun formatStop(a: Double): String =
        if (a == a.toLong().toDouble()) a.toLong().toString() else a.toString()
}

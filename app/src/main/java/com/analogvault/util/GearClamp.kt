package com.analogvault.util

import com.analogvault.data.model.Camera
import com.analogvault.data.model.Lens
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Bounds the meter's suggestions by the gear actually in the user's hands.
 *
 * The app already knows the body and the lens on the loaded roll; a meter that
 * answers "1/2000 at f/1.4" for a Zenit E is answering a question nobody asked.
 * Every limit here is nullable and null means unclamped, so a vault with no gear
 * detail filled in behaves exactly as it did before this existed.
 *
 * No Android dependencies — unit-testable on the JVM.
 */
object GearClamp {

    /**
     * Aperture is stated the way a lens barrel states it: [widestAperture] is the
     * *smallest* f-number. Shutter is stated in seconds, so [fastestShutterSec]
     * is the smallest number.
     */
    data class Limits(
        val fastestShutterSec: Double? = null,
        val slowestShutterSec: Double? = null,
        val hasBulb: Boolean = false,
        val widestAperture: Double? = null,
        val narrowestAperture: Double? = null,
    ) {
        /** Nothing to clamp against — every rung is achievable. */
        val unclamped: Boolean
            get() = fastestShutterSec == null && slowestShutterSec == null &&
                widestAperture == null && narrowestAperture == null
    }

    /** Why a rung can't be set. Null on the achievable ones. */
    enum class Reason { SHUTTER_TOO_FAST, SHUTTER_TOO_SLOW, LENS_TOO_SLOW, LENS_WONT_STOP_DOWN }

    /** One step of the exposure ladder: a shutter speed and the aperture it needs. */
    data class Rung(
        val shutter: String,
        val shutterSec: Double,
        /** Unrounded solution — the honest number behind the snapped one. */
        val apertureExact: Double,
        /** Snapped to a stop the ring can actually be set to. */
        val aperture: Double,
        val reason: Reason?,
    ) {
        val achievable: Boolean get() = reason == null
    }

    /**
     * How the gear falls short and what to do about it.
     *
     * @param stopsOff signed, in stops: negative means the scene is darker than
     *   the gear can reach (underexposure), positive means brighter.
     */
    data class Advice(
        val headline: String,
        val detail: String,
        val stopsOff: Double,
    )

    // ── Building the limits ──────────────────────────────────────────────────

    fun limitsOf(camera: Camera?, lens: Lens?): Limits = Limits(
        fastestShutterSec = camera?.fastestShutter?.takeIf { it.isNotBlank() }
            ?.let { Constants.evalShutter(it) },
        slowestShutterSec = camera?.slowestShutter?.takeIf { it.isNotBlank() }
            ?.let { Constants.evalShutter(it) },
        hasBulb = camera?.hasBulb ?: false,
        widestAperture = lens?.maxAperture?.toDecimalOrNull(),
        narrowestAperture = lens?.minAperture?.toDecimalOrNull(),
    )

    // ── The ladder ───────────────────────────────────────────────────────────

    /**
     * Every standard shutter speed with the aperture it needs at [ev] (scene EV
     * at the ISO 100 reference) for film speed [iso], marked with whether the
     * gear can be set that way.
     *
     * "B" is excluded: bulb is not a rung you pick off a ladder, it is what you
     * fall back to when the ladder runs out, and it appears in the advice instead.
     */
    fun ladder(iso: Int, ev: Double, limits: Limits): List<Rung> =
        Constants.SHUTTER_SPEEDS.filter { it != "B" }.map { s ->
            val sec = Constants.evalShutter(s)
            // Not Constants.calcAperture: that floors the result at f/1.0, which
            // is right for a readout nobody can act on but wrong here — a rung
            // needing f/0.6 would print f/1.0 and look merely unavailable rather
            // than physically impossible, and every dark-scene rung would read
            // as the same f/1.0.
            val exact = apertureFor(iso, sec, ev)
            val snapped = Constants.nearestStandardAperture(exact)
            Rung(
                shutter = s,
                shutterSec = sec,
                apertureExact = exact,
                aperture = snapped,
                // Shutter first: it is the limit the user can feel (the dial has
                // a hard stop), and naming it beats naming the aperture that only
                // went out of range as a consequence of the speed.
                reason = when {
                    limits.fastestShutterSec != null && sec < limits.fastestShutterSec * 0.99 ->
                        Reason.SHUTTER_TOO_FAST
                    limits.slowestShutterSec != null && sec > limits.slowestShutterSec * 1.01 ->
                        Reason.SHUTTER_TOO_SLOW
                    limits.widestAperture != null && snapped < limits.widestAperture * 0.99 ->
                        Reason.LENS_TOO_SLOW
                    limits.narrowestAperture != null && snapped > limits.narrowestAperture * 1.01 ->
                        Reason.LENS_WONT_STOP_DOWN
                    else -> null
                },
            )
        }

    // ── The way out ──────────────────────────────────────────────────────────

    /**
     * Null when the gear can expose this scene correctly. Otherwise the shortfall
     * and the two real options: accept the error, or change the film's speed.
     *
     * The exposure equation is EV = log2(N²/t) at the film's speed, so the gear's
     * reach is a closed interval and the scene either sits inside it or doesn't.
     */
    fun advise(iso: Int, ev: Double, limits: Limits, camera: Camera?, lens: Lens?): Advice? {
        if (limits.unclamped) return null
        // EV at the film's speed rather than the ISO 100 reference — the gear
        // does not know what the reference is, it only knows N and t.
        val evFilm = ev + log2(iso / 100.0)

        val widest = limits.widestAperture
        val narrowest = limits.narrowestAperture
        val slowest = limits.slowestShutterSec
        val fastest = limits.fastestShutterSec

        // Darkest the gear can handle: widest opening for the longest time.
        val minEv = if (widest != null && slowest != null) log2(widest * widest / slowest) else null
        // Brightest: smallest opening for the shortest time.
        val maxEv = if (narrowest != null && fastest != null) log2(narrowest * narrowest / fastest) else null

        val bodyName = camera?.name?.takeIf { it.isNotBlank() } ?: "this body"
        val lensName = lens?.name?.takeIf { it.isNotBlank() } ?: "this lens"

        if (minEv != null && evFilm < minEv - 0.05) {
            val short = minEv - evFilm
            val wideStr = formatAperture(widest!!)
            val slowStr = camera?.slowestShutter.orEmpty().ifBlank { formatSeconds(slowest!!) }
            val escape = if (limits.hasBulb) {
                val needed = widest * widest / 2.0.pow(evFilm)
                "Hold it on B for about ${formatSeconds(needed)} at $wideStr, or push this roll " +
                    "${pushStops(short)}."
            } else {
                "Brace for $slowStr at $wideStr and accept ${"%.1f".format(short)} EV under, " +
                    "or push this roll ${pushStops(short)}."
            }
            return Advice(
                headline = "Too dark for your gear",
                detail = "$lensName opens to $wideStr and $bodyName stops at $slowStr. $escape",
                stopsOff = -short,
            )
        }

        if (maxEv != null && evFilm > maxEv + 0.05) {
            val over = evFilm - maxEv
            val narrowStr = formatAperture(narrowest!!)
            val fastStr = camera?.fastestShutter.orEmpty().ifBlank { formatSeconds(fastest!!) }
            // ND filters are sold by the stop, so round up: a 1.4-stop shortfall
            // needs the 2-stop filter, and the 1-stop one still blows the frame.
            val nd = kotlin.math.ceil(over).toInt().coerceAtLeast(1)
            return Advice(
                headline = "Too bright for your gear",
                detail = "$bodyName tops out at $fastStr and $lensName closes to $narrowStr. " +
                    "That is ${"%.1f".format(over)} EV more light than they can hold — " +
                    "a $nd-stop ND filter or slower film is the only way to hold the highlights.",
                stopsOff = over,
            )
        }
        return null
    }

    /**
     * How far to push, in whole stops.
     *
     * Film is pushed in whole stops — there is no half-stop development time on
     * any datasheet — and the push has to *cover* the shortfall, so this rounds
     * up and never goes below one. Rounding to the nearest half instead produced
     * "or push this roll not at all" whenever the gap was under a quarter stop,
     * which is not an instruction anyone can act on.
     *
     * The 0.05 slack keeps a shortfall that is a hair over a whole stop, as
     * floating-point EV arithmetic tends to be, from asking for the next one up.
     */
    private fun pushStops(shortfall: Double): String =
        when (val n = kotlin.math.ceil(shortfall - 0.05).toLong().coerceAtLeast(1L)) {
            1L -> "one stop"
            2L -> "two stops"
            3L -> "three stops"
            else -> "$n stops"
        }

    /** "f/8", "f/1.4" — matches the aperture scale and stays '.'-decimal. */
    fun formatAperture(a: Double): String {
        val whole = a == a.toLong().toDouble()
        return when {
            a < 2.0 && whole -> "f/${"%.1f".format(java.util.Locale.US, a)}"
            whole            -> "f/${a.toLong()}"
            else             -> "f/$a"
        }
    }

    /** "1/125" below a second, "2.5s"/"45s" above. */
    fun formatSeconds(sec: Double): String = when {
        sec < 1.0  -> "1/${Math.round(1.0 / sec)}"
        sec < 10.0 -> "${(Math.round(sec * 10) / 10.0).toString().trimEnd('0').trimEnd('.')}s"
        else       -> "${Math.round(sec)}s"
    }

    /** Shutter seconds for an aperture at a given EV — the ladder read backwards. */
    fun shutterSecFor(iso: Int, aperture: Double, ev: Double): Double =
        (aperture * aperture) / (2.0.pow(ev) * (iso / 100.0))

    /** Exact aperture for a shutter time, for callers holding seconds not strings. */
    fun apertureFor(iso: Int, shutterSec: Double, ev: Double): Double =
        sqrt(shutterSec * 2.0.pow(ev) * (iso / 100.0))
}

package com.analogvault.util

/**
 * Where the sun is, in minutes past local midnight.
 *
 * Everything here is derived from the sunrise/sunset the weather API already
 * returns, so there is no almanac maths and nothing to get wrong about the
 * observer's latitude. All values are local to the *weather station's* timezone
 * rather than the phone's, which is the correct frame: the light being described
 * is the light where the forecast is for.
 *
 * @param nowMinutes minutes past local midnight, 0..1439
 * @param dayFraction 0f at sunrise, 1f at sunset. Negative before sunrise and
 *   greater than 1 after sunset, so callers can tell night from a clamped arc.
 */
data class SunClock(
    val nowMinutes: Int,
    val sunriseMinutes: Int,
    val sunsetMinutes: Int,
    val dayFraction: Float,
    /** Minutes until golden hour begins. Null when it is golden hour or past it. */
    val minutesToGolden: Int?,
    /** Minutes remaining in the current golden hour, or null when not in one. */
    val goldenMinutesLeft: Int?,
    val blueStartMinutes: Int,
    val blueEndMinutes: Int,
    val isDaylight: Boolean,
) {
    val sunriseLabel: String get() = clockLabel(sunriseMinutes)
    val sunsetLabel: String get() = clockLabel(sunsetMinutes)
    val blueWindowLabel: String get() = "${clockLabel(blueStartMinutes)}–${clockLabel(blueEndMinutes)}"

    companion object {
        /** The last hour before sunset is "golden"; a real one varies with latitude. */
        const val GOLDEN_WINDOW_MIN = 60
        /** Blue hour runs from sunset to half an hour after it. */
        const val BLUE_WINDOW_MIN = 30

        fun clockLabel(minutes: Int): String {
            val m = ((minutes % 1440) + 1440) % 1440
            return "%02d:%02d".format(m / 60, m % 60)
        }

        /**
         * @param sunriseEpoch,sunsetEpoch UTC seconds, as OpenWeatherMap reports them
         * @param timezoneOffsetSeconds the station's offset from UTC
         * @param nowEpoch UTC seconds; pass the response's own timestamp so a
         *   cached forecast describes the light it was actually fetched for
         */
        fun from(
            sunriseEpoch: Long,
            sunsetEpoch: Long,
            timezoneOffsetSeconds: Int,
            nowEpoch: Long,
        ): SunClock? {
            if (sunriseEpoch <= 0L || sunsetEpoch <= 0L) return null

            fun localMinutes(epoch: Long): Int =
                (((epoch + timezoneOffsetSeconds) % 86_400L) / 60L).toInt()

            val sunrise = localMinutes(sunriseEpoch)
            val sunset = localMinutes(sunsetEpoch)
            val now = localMinutes(nowEpoch)
            if (sunset <= sunrise) return null // polar day/night; the arc is meaningless

            val goldenStart = sunset - GOLDEN_WINDOW_MIN

            return SunClock(
                nowMinutes = now,
                sunriseMinutes = sunrise,
                sunsetMinutes = sunset,
                dayFraction = (now - sunrise).toFloat() / (sunset - sunrise).toFloat(),
                minutesToGolden = (goldenStart - now).takeIf { it > 0 },
                goldenMinutesLeft = (sunset - now).takeIf { now in goldenStart until sunset },
                blueStartMinutes = sunset,
                blueEndMinutes = sunset + BLUE_WINDOW_MIN,
                isDaylight = now in sunrise..sunset,
            )
        }
    }
}

/** "47 min" / "2h 05m" — countdowns the user reads at a glance. */
fun formatCountdown(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "%dh %02dm".format(minutes / 60, minutes % 60)

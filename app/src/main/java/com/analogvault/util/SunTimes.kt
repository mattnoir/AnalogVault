package com.analogvault.util

import java.time.Instant
import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sunrise and sunset for any place and day, worked out rather than fetched.
 *
 * [SunClock] reads the times OpenWeatherMap sends with today's forecast, which
 * is right for "how long until golden hour" and useless for a frame logged two
 * summers ago in another country. This is the sunrise equation as NOAA states
 * it, good to a minute or two away from the poles — far finer than "was this
 * frame shot in golden hour" needs.
 *
 * No Android dependencies — unit-testable on the JVM.
 */
object SunTimes {

    /** The sun's centre 0.833° below the horizon: refraction plus its own radius. */
    private const val HORIZON_DEG = -0.833
    private const val OBLIQUITY_DEG = 23.4397

    /**
     * Sunrise and sunset on [date] at [lat], [lon] (degrees, east positive), or
     * null when the sun never rises or never sets that day.
     */
    fun riseAndSet(date: LocalDate, lat: Double, lon: Double): Pair<Instant, Instant>? {
        // Days since the J2000 epoch, at the day's solar noon on the meridian.
        val n = date.toEpochDay() - 10_957.0 + 0.0008
        val meanSolar = n - lon / 360.0
        val m = norm(357.5291 + 0.98560028 * meanSolar)
        val mRad = Math.toRadians(m)
        val centre = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val eclipticLon = Math.toRadians(norm(m + centre + 180.0 + 102.9372))
        val transit = 2_451_545.0 + meanSolar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * eclipticLon)

        val declination = asin(sin(eclipticLon) * sin(Math.toRadians(OBLIQUITY_DEG)))
        val latRad = Math.toRadians(lat)
        val cosHour = (sin(Math.toRadians(HORIZON_DEG)) - sin(latRad) * sin(declination)) /
            (cos(latRad) * cos(declination))
        if (cosHour !in -1.0..1.0) return null
        val hourFraction = Math.toDegrees(acos(cosHour)) / 360.0

        return julianToInstant(transit - hourFraction) to julianToInstant(transit + hourFraction)
    }

    private fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    private fun julianToInstant(jd: Double): Instant =
        Instant.ofEpochMilli(((jd - 2_440_587.5) * 86_400_000.0).toLong())
}

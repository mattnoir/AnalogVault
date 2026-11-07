package com.analogvault.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

class SunTimesTest {

    @Test
    fun `london midsummer matches the almanac`() {
        // Greenwich, 21 June 2026: sunrise 03:43 UTC, sunset 20:21 UTC.
        val (rise, set) = SunTimes.riseAndSet(LocalDate.of(2026, 6, 21), 51.4779, 0.0)!!
        assertNearUtc(3 * 60 + 43, rise)
        assertNearUtc(20 * 60 + 21, set)
    }

    @Test
    fun `sydney midwinter matches the almanac`() {
        // Sydney, 21 June 2026: sunrise 07:00 AEST (21:00 UTC), sunset 16:54 AEST (06:54 UTC).
        val (rise, set) = SunTimes.riseAndSet(LocalDate.of(2026, 6, 21), -33.8688, 151.2093)!!
        assertNearUtc(21 * 60, rise)
        assertNearUtc(6 * 60 + 54, set)
    }

    @Test
    fun `polar day and polar night have no sunrise`() {
        assertNull(SunTimes.riseAndSet(LocalDate.of(2026, 6, 21), 78.22, 15.65))   // Longyearbyen, summer
        assertNull(SunTimes.riseAndSet(LocalDate.of(2026, 12, 21), 78.22, 15.65))  // and winter
        assertNotNull(SunTimes.riseAndSet(LocalDate.of(2026, 3, 21), 78.22, 15.65))
    }

    /** Time of day in UTC within [tolerance] minutes, wrapping round midnight. */
    private fun assertNearUtc(expectedMinutes: Int, actual: Instant, tolerance: Int = 5) {
        val t = actual.atZone(ZoneOffset.UTC)
        val got = t.hour * 60 + t.minute
        val diff = Math.floorMod(got - expectedMinutes + 720, 1440) - 720
        assertTrue("expected ~$expectedMinutes min UTC, got $got", abs(diff) <= tolerance)
    }
}

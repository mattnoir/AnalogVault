package com.analogvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeShareTest {

    @Test fun `a plain badge says what, when and where`() {
        val t = BadgeShare.text(
            Achievement.PUSHER, AchRecord(1, "2026-09-27", "r1", null),
            frameNo = 24, earnedCount = 18, total = 42,
            rollLine = "the roll loaded 2026-09-16", edgePrint = "OLSAK FILM",
        )
        assertEquals("24", t.frameMark)
        assertEquals("CRAFT ACHIEVEMENT", t.eyebrow)
        assertEquals("PUSHER", t.title)
        assertEquals(Achievement.PUSHER.description, t.description)
        assertEquals(listOf("EARNED 2026-09-27", "ON THE ROLL LOADED 2026-09-16"), t.details)
        assertEquals("▸  ANALOG VAULT  ▸  18/42 EARNED  ▸  OLSAK FILM", t.rebate)
    }

    @Test fun `a frame-level badge names the frame, and a tiered one its tier and count`() {
        val t = BadgeShare.text(
            Achievement.FULL_ROLL, AchRecord(3, "2026-10-01"),
            frameNo = 2, earnedCount = 20, total = 42,
            rollLine = "the roll loaded 2026-08-01", shotIndex = 11,
        )
        assertEquals("02  III", t.frameMark)
        assertTrue(t.description.endsWith("(25 rolls.)"))
        assertEquals(listOf("EARNED 2026-10-01", "ON THE ROLL LOADED 2026-08-01", "FRAME 12"), t.details)
        assertFalse("no edge print, no empty segment", t.rebate.endsWith("▸  "))
    }

    @Test fun `a secret is shared as a secret found, and needs no roll`() {
        val t = BadgeShare.text(Achievement.UNDER_THE_RED_LIGHT, AchRecord(1, "2026-11-07"),
            frameNo = 34, earnedCount = 3, total = 42)
        assertEquals("SECRET FOUND", t.eyebrow)
        assertEquals(listOf("EARNED 2026-11-07"), t.details)
    }

    @Test fun `the file name is the badge and the day`() {
        assertEquals("analogvault_red_light_2026-11-07.png",
            BadgeShare.fileName(Achievement.UNDER_THE_RED_LIGHT, AchRecord(1, "2026-11-07")))
    }
}

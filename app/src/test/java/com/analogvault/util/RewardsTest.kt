package com.analogvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardsTest {

    private fun earned(vararg pairs: Pair<Achievement, Int>) =
        pairs.associate { (a, tier) -> a to AchRecord(tier, "2026-01-01") }

    @Test fun `an unlock needs its tier, not just the badge`() {
        val five = IrisStyle.FIVE.unlock!!
        assertFalse(five.isMet(earned(Achievement.FULL_ROLL to 2)))
        assertTrue(five.isMet(earned(Achievement.FULL_ROLL to 3)))
        assertTrue(five.isMet(earned(Achievement.FULL_ROLL to 4)))
        assertEquals("Full Roll III", five.label)
        assertEquals("Pusher", GrainStock.PUSHED.unlock!!.label)
    }

    @Test fun `a stored choice is used only while it is unlocked`() {
        assertEquals(GrainStock.PUSHED, GrainStock.resolve("pushed", earned(Achievement.PUSHER to 1)))
        assertEquals(GrainStock.DEFAULT, GrainStock.resolve("pushed", emptyMap()))
        assertEquals(GrainStock.DEFAULT, GrainStock.resolve(null, emptyMap()))
        assertEquals(IrisStyle.ROUNDED, IrisStyle.resolve("rounded", earned(Achievement.BULB_NIGHT to 1)))
        assertEquals(IrisStyle.DEFAULT, IrisStyle.resolve("five", earned(Achievement.FULL_ROLL to 2)))
        assertEquals(IrisStyle.DEFAULT, IrisStyle.resolve("no_such_iris", emptyMap()))
    }

    @Test fun `the defaults need nothing`() {
        assertTrue(GrainStock.DEFAULT.unlocked(emptyMap()))
        assertTrue(IrisStyle.DEFAULT.unlocked(emptyMap()))
    }

    @Test fun `edge print prints nothing until earned, and only what an edge printer can`() {
        val records = earned(Achievement.EVERY_FRAME_COUNTED to 1)
        assertEquals(EdgePrint(), EdgePrint.resolve("ADAM'S FILM", "arrow", emptyMap()))
        val p = EdgePrint.resolve("adam's film", "arrow", records)
        assertEquals("ADAM'S FILM", p.text)
        assertEquals(FrameNumberStyle.ARROW, p.frameStyle)
        assertEquals("SAFETY FILM 5063", EdgePrint.clean("Safety  film 5063!?"))
        assertEquals(EdgePrint.MAX_LENGTH, EdgePrint.clean("X".repeat(40)).length)
        assertEquals(FrameNumberStyle.PLAIN, EdgePrint.resolve("x", "nonsense", records).frameStyle)
    }

    @Test fun `frame numbers print as film edges print them`() {
        assertEquals("12", FrameNumberStyle.PLAIN.format(12))
        assertEquals("12→", FrameNumberStyle.ARROW.format(12))
        assertEquals("12A", FrameNumberStyle.HALF.format(12))
    }

    @Test fun `every look option has its own key, and secrets stay secret`() {
        assertEquals(GrainStock.entries.size, GrainStock.entries.map { it.key }.toSet().size)
        assertEquals(IrisStyle.entries.size, IrisStyle.entries.map { it.key }.toSet().size)
        // Each tier that unlocks something must exist.
        (GrainStock.entries.mapNotNull { it.unlock } + IrisStyle.entries.mapNotNull { it.unlock } + EdgePrint.UNLOCK)
            .forEach { u -> assertTrue("$u", u.tier in 1..u.achievement.tiers.size) }
        assertTrue(TECHNIQUE_CARD_BADGES.containsAll(Achievement.entries.filter { it.category == AchCategory.CRAFT }))
    }
}

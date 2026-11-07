package com.analogvault.util

import com.analogvault.ui.screens.DEFAULT_HOME_ORDER
import com.analogvault.ui.screens.HomeSection
import com.analogvault.ui.screens.parseHomeHidden
import com.analogvault.ui.screens.parseHomeOrder
import com.analogvault.ui.screens.parseHomeShown
import com.analogvault.work.AchievementNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home row's opt-in, and the words a notification uses. */
class AchievementPresentationTest {

    @Test fun `the achievements row starts hidden, for new and old layouts alike`() {
        assertEquals(setOf(HomeSection.ACHIEVEMENTS), parseHomeHidden(null))
        // A layout saved before the row existed, with Weather hidden.
        assertEquals(setOf(HomeSection.WEATHER, HomeSection.ACHIEVEMENTS), parseHomeHidden("WEATHER"))
        // And the old saved order still gets the new row, at the end.
        assertEquals(HomeSection.ACHIEVEMENTS, parseHomeOrder("NUDGES,HERO,PIPELINE,WEATHER,LIGHT").last())
        assertEquals(HomeSection.ACHIEVEMENTS, DEFAULT_HOME_ORDER.last())
    }

    @Test fun `turning the row on is remembered under home_shown`() {
        val shown = parseHomeShown("ACHIEVEMENTS")
        assertFalse(HomeSection.ACHIEVEMENTS in parseHomeHidden("WEATHER", shown))
        // home_shown only ever holds opt-in sections, and home_hidden cannot hide them on its own.
        assertTrue(parseHomeShown("WEATHER,ACHIEVEMENTS") == setOf(HomeSection.ACHIEVEMENTS))
        assertTrue(HomeSection.ACHIEVEMENTS in parseHomeHidden("ACHIEVEMENTS", emptySet()))
        assertFalse(HomeSection.ACHIEVEMENTS in parseHomeHidden("ACHIEVEMENTS", shown))
    }

    @Test fun `notifications name the badge, and call a secret a secret`() {
        assertEquals("Achievement earned: Round Trip",
            AchievementNotifier.describe(listOf(Achievement.ROUND_TRIP)).first)
        assertEquals("Secret found: Under the Red Light",
            AchievementNotifier.describe(listOf(Achievement.UNDER_THE_RED_LIGHT)).first)
        val both = AchievementNotifier.describe(listOf(Achievement.ROUND_TRIP, Achievement.UNDER_THE_RED_LIGHT))
        assertEquals("2 achievements earned", both.first)
        assertEquals("Round Trip, Under the Red Light", both.second)
    }
}

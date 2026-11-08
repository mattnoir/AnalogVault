package com.analogvault.ui.theme

import androidx.compose.ui.graphics.Color
import com.analogvault.util.AchCategory
import com.analogvault.util.AchRecord
import com.analogvault.util.Achievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteTest {

    /** WCAG relative luminance, from the colour's sRGB channels. */
    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun `every palette keeps its text readable and its disabled tone below the floor`() {
        for (p in Palette.entries) {
            val c = p.colors
            for (bg in listOf(c.void, c.film)) {
                assertTrue("${p.label}: halide ${contrast(c.halide, bg)}", contrast(c.halide, bg) >= 4.5)
                assertTrue("${p.label}: dim ${contrast(c.dim, bg)}", contrast(c.dim, bg) >= 4.5)
            }
            assertTrue("${p.label}: dead must stay a disabled tone", contrast(c.dead, c.film) < 4.5)
            for ((role, accent) in listOf("cyan" to c.cyan, "magenta" to c.magenta, "yellow" to c.yellow, "mask" to c.mask)) {
                assertTrue("${p.label}: $role ${contrast(accent, c.film)}", contrast(accent, c.film) >= 3.0)
            }
            assertTrue("${p.label} must not claim to be the safelight", !c.safelight)
        }
    }

    @Test fun `the four accents of a palette are told apart`() {
        // Not a perceptual metric, just a floor: no two roles may share a colour.
        for (p in Palette.entries) {
            val c = p.colors
            val accents = listOf(c.cyan, c.magenta, c.yellow, c.mask)
            assertEquals("${p.label} repeats an accent", accents.size, accents.toSet().size)
        }
    }

    @Test fun `each palette reward is its own secret`() {
        val rewards = Palette.entries.mapNotNull { it.reward }
        assertEquals(4, rewards.size)
        assertEquals(rewards.size, rewards.toSet().size)
        assertTrue(rewards.all { it.category == AchCategory.SECRET })
        assertEquals(Palette.EXPIRED, Palette.rewardedBy(Achievement.EXPIRED_WITH_HONOURS))
        assertEquals(null, Palette.rewardedBy(Achievement.FIRST_FRAME))
    }

    @Test fun `the stored choice is worn only while it is unlocked`() {
        val earned = mapOf(Achievement.SUNNY_16 to AchRecord(1, "2026-01-01"))
        assertEquals(Palette.SLIDE, Palette.resolve("slide", null, earned))
        assertEquals(Palette.DYE_LAYER, Palette.resolve("slide", null, emptyMap()))
        assertEquals(Palette.DYE_LAYER, Palette.resolve("no_such_palette", null, earned))
    }

    @Test fun `the old legacy amber switch carries over until a palette is picked`() {
        assertEquals(Palette.LEGACY_AMBER, Palette.resolve(null, "true", emptyMap()))
        assertEquals(Palette.DYE_LAYER, Palette.resolve(null, "false", emptyMap()))
        assertEquals(Palette.DYE_LAYER, Palette.resolve(null, null, emptyMap()))
        // Once picked, the old switch no longer decides.
        assertEquals(Palette.DYE_LAYER, Palette.resolve("dye_layer", "true", emptyMap()))
    }
}

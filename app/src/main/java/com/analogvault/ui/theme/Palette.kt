package com.analogvault.ui.theme

import androidx.compose.ui.graphics.Color
import com.analogvault.util.AchRecord
import com.analogvault.util.Achievement

/*
 * The four palettes a secret unlocks.
 *
 * Each fills the same twelve roles as Dye Layer, and the roles keep their
 * meaning — live, action, waiting, decay, structure — so every screen reads
 * the same in any of them. Text (`halide`, `dim`) clears 4.5:1 on every
 * background; `dead` stays under it as the disabled tone; the four accents
 * clear 3:1 against the card colour. PaletteContrastTest holds them to it.
 *
 * None of them may look like a setting the app already has. That ruled out
 * sepia (Legacy amber is already warm browns) and a faded "expired" look
 * (the saturation slider already fades).
 */

/**
 * Expired film, the way it actually goes: not faded but shifted. The base
 * fogs green, the dyes drift, and what comes back is acid yellow-green, coral
 * and teal — closer to a cross-processed roll than to an old photo.
 */
val ExpiredColors = FilmColors(
    cyan = Color(0xFF2FD3B5),       // teal — live
    magenta = Color(0xFFFF6F61),    // coral — action
    yellow = Color(0xFFD4F03A),     // acid yellow-green — waiting
    mask = Color(0xFFE05A9C),       // the magenta cast of a dead C-41 roll — decay
    violet = Color(0xFF4C7A3A),     // moss — structure
    void = Color(0xFF0A0F0B),
    film = Color(0xFF121A14),
    filmRaised = Color(0xFF1B251D),
    edge = Color(0xFF2C3A2E),
    halide = Color(0xFFE6EFD8),
    dim = Color(0xFF8FA088),
    dead = Color(0xFF3A4A3C),
    safelight = false,
)

/**
 * A cyanotype: Prussian blue paper, the white of what the light never
 * reached. The blues alone cannot carry five roles, so waiting takes the gold
 * of a tea-toned print and decay the rust of the iron salts that make one.
 */
val CyanotypeColors = FilmColors(
    cyan = Color(0xFF5CE1E6),       // aqua — live
    magenta = Color(0xFF8C9EFF),    // periwinkle — action
    yellow = Color(0xFFF2D27A),     // tea-toned gold — waiting
    mask = Color(0xFFE07A5F),       // iron rust — decay
    violet = Color(0xFF2541B2),     // ultramarine — structure
    void = Color(0xFF050B1A),
    film = Color(0xFF0B1630),
    filmRaised = Color(0xFF122047),
    edge = Color(0xFF22356A),
    halide = Color(0xFFE8F1FF),
    dim = Color(0xFF8FA6CC),
    dead = Color(0xFF2E4270),
    safelight = false,
)

/**
 * Slide film in sun: saturated reds and a hard yellow on a near-black base,
 * with one blue — the sky every slide of that era has — kept for "live".
 */
val SlideColors = FilmColors(
    cyan = Color(0xFF3FA9F5),       // slide-film sky — live
    magenta = Color(0xFFE8282B),    // red — action
    yellow = Color(0xFFFFC20E),     // box yellow — waiting
    mask = Color(0xFFFF7A1A),       // orange — decay
    violet = Color(0xFF7A1F1F),     // oxblood — structure
    void = Color(0xFF0A0605),
    film = Color(0xFF14100C),
    filmRaised = Color(0xFF1F1812),
    edge = Color(0xFF3A2C20),
    halide = Color(0xFFF6EBDD),
    dim = Color(0xFFA89482),
    dead = Color(0xFF4A3A2C),
    safelight = false,
)

/**
 * Colour infrared: foliage turns crimson, skies go cobalt, highlights burn
 * yellow, all on a plum-black base. The crimson takes "live", the colour seen
 * most, because the red trees are what an Aerochrome frame is; with cobalt on
 * the actions it reads as the reverse of Slide's blue-and-red, not a copy.
 */
val AerochromeColors = FilmColors(
    cyan = Color(0xFFFF3D6E),       // crimson foliage — live
    magenta = Color(0xFF4D7CFE),    // cobalt sky — action
    yellow = Color(0xFFF5D547),     // burnt highlight — waiting
    mask = Color(0xFFFF8A3D),       // orange — decay
    violet = Color(0xFF9B2D6F),     // plum — structure
    void = Color(0xFF0E0509),
    film = Color(0xFF1A0A12),
    filmRaised = Color(0xFF26101B),
    edge = Color(0xFF4A2034),
    halide = Color(0xFFFBE9EE),
    dim = Color(0xFFC0909F),
    dead = Color(0xFF4A2A38),
    safelight = false,
)

/**
 * The palettes the app can wear. [key] is the stored `palette` setting.
 * A palette with a [reward] is earned by that secret, and stays out of the
 * picker until it is — nothing hints that it exists.
 */
enum class Palette(
    val key: String,
    val label: String,
    val blurb: String,
    val colors: FilmColors,
    val reward: Achievement? = null,
) {
    DYE_LAYER("dye_layer", "Dye Layer", "The three dyes of a colour negative and its orange mask", DyeLayerColors),
    LEGACY_AMBER("legacy_amber", "Legacy amber", "The palette from before the redesign", LegacyAmberColors),
    EXPIRED("expired", "Expired", "Fogged green, coral and acid yellow", ExpiredColors,
        Achievement.EXPIRED_WITH_HONOURS),
    CYANOTYPE("cyanotype", "Cyanotype", "Prussian blue, paper white, rust", CyanotypeColors,
        Achievement.LATENT_IMAGE),
    SLIDE("slide", "Slide", "Saturated reds and yellow, one blue sky", SlideColors,
        Achievement.SUNNY_16),
    AEROCHROME("aerochrome", "Aerochrome", "Infrared: crimson, cobalt and yellow on plum", AerochromeColors,
        Achievement.SAME_SPOT),
    ;

    fun unlocked(records: Map<Achievement, AchRecord>): Boolean = reward == null || reward in records

    companion object {
        fun fromKey(key: String?): Palette? = entries.find { it.key == key }

        /** The palette a badge unlocks, if any. */
        fun rewardedBy(a: Achievement): Palette? = entries.find { it.reward == a }

        /**
         * The palette to wear: the stored choice if it is still unlocked, else
         * Dye Layer. Before the picker, Legacy amber was its own switch, and a
         * vault that has never picked a palette keeps that switch's answer.
         */
        fun resolve(stored: String?, legacyAmber: String?, records: Map<Achievement, AchRecord>): Palette {
            val chosen = fromKey(stored) ?: if (legacyAmber == "true") LEGACY_AMBER else DYE_LAYER
            return chosen.takeIf { it.unlocked(records) } ?: DYE_LAYER
        }
    }
}

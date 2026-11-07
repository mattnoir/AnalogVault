package com.analogvault.util

/**
 * The cosmetic rewards achievements unlock, other than palettes (those live
 * with the colours, in ui/theme/Palette.kt).
 *
 * Every one is cosmetic or informative. None of them gates a tool: a meter
 * mode locked behind ten-year-expired film would punish exactly the people
 * the app is for.
 *
 * No Android dependencies — unit-testable on the JVM.
 */

/** What earns a reward: a badge, at a tier for the tiered ones. */
data class Unlock(val achievement: Achievement, val tier: Int = 1) {
    fun isMet(records: Map<Achievement, AchRecord>): Boolean =
        (records[achievement]?.tier ?: 0) >= tier

    /** "Full Roll" or "Full Roll III" — tier shown only where there are tiers. */
    val label: String
        get() = if (achievement.tiered) "${achievement.title} ${roman(tier)}" else achievement.title

    private fun roman(n: Int) = listOf("I", "II", "III", "IV", "V").getOrElse(n - 1) { n.toString() }
}

/**
 * The grain drawn over the whole app. Generated, never shipped as an image:
 * [alpha] is how hard it prints, [tileSize] the noise tile, and [clump] how
 * many screen pixels each grain covers — pushed film's grain is not more of
 * the same specks, it is bigger ones.
 */
enum class GrainStock(
    val key: String,
    val label: String,
    val blurb: String,
    val alpha: Float,
    val tileSize: Int,
    val clump: Int,
    val unlock: Unlock? = null,
) {
    DEFAULT("default", "Box speed", "The grain the app ships with", 0.055f, 128, 1),
    FINE("fine", "T-grain", "Fine and quiet, like a modern 100-speed stock", 0.035f, 128, 1,
        Unlock(Achievement.MIXED_BAG)),
    PUSHED("pushed", "Pushed", "Two stops over: coarse, clumped grain", 0.085f, 96, 2,
        Unlock(Achievement.PUSHER)),
    SHEET("sheet", "Sheet film", "Barely there, the way a 4x5 negative enlarges", 0.02f, 128, 1,
        Unlock(Achievement.FORMAT_HOPPER)),
    ;

    fun unlocked(records: Map<Achievement, AchRecord>) = unlock?.isMet(records) ?: true

    companion object {
        fun resolve(stored: String?, records: Map<Achievement, AchRecord>): GrainStock =
            entries.find { it.key == stored }?.takeIf { it.unlocked(records) } ?: DEFAULT
    }
}

/**
 * The iris the shutter button fires. [blades] and [twistDegrees] feed the
 * shutter overlay's geometry; sixteen blades close to a near-circle, the
 * rounded diaphragm of a modern lens.
 */
enum class IrisStyle(
    val key: String,
    val label: String,
    val blurb: String,
    val blades: Int,
    val twistDegrees: Float,
    val unlock: Unlock? = null,
) {
    DEFAULT("default", "Eight blades", "The iris the app ships with", 8, 26f),
    FIVE("five", "Five blades", "The pentagon of an old zoom stopped down", 5, 32f,
        Unlock(Achievement.FULL_ROLL, tier = 3)),
    NINE("nine", "Nine blades", "Rounder, like a portrait lens", 9, 24f,
        Unlock(Achievement.THOUSAND_FRAMES)),
    ROUNDED("rounded", "Rounded", "Sixteen blades closing to a circle", 16, 12f,
        Unlock(Achievement.BULB_NIGHT)),
    ;

    fun unlocked(records: Map<Achievement, AchRecord>) = unlock?.isMet(records) ?: true

    companion object {
        fun resolve(stored: String?, records: Map<Achievement, AchRecord>): IrisStyle =
            entries.find { it.key == stored }?.takeIf { it.unlocked(records) } ?: DEFAULT
    }
}

/** How a frame number is printed in the shot log, as film edges print them. */
enum class FrameNumberStyle(val key: String, val label: String) {
    PLAIN("plain", "12"),
    ARROW("arrow", "12→"),
    HALF("half", "12A"),
    ;

    fun format(n: Int): String = when (this) {
        PLAIN -> "$n"
        ARROW -> "$n→"
        HALF -> "${n}A"
    }

    companion object {
        fun fromKey(key: String?): FrameNumberStyle = entries.find { it.key == key } ?: PLAIN
    }
}

/**
 * Your own edge printing: a line of text along the rebate of every roll and
 * stock, and the frame numbers in the shot log. Unlocked by logging every
 * frame of a roll — the one badge about the edges of the film.
 */
data class EdgePrint(
    val text: String = "",
    val frameStyle: FrameNumberStyle = FrameNumberStyle.PLAIN,
) {
    companion object {
        val UNLOCK = Unlock(Achievement.EVERY_FRAME_COUNTED)
        const val MAX_LENGTH = 24
        private val ALLOWED = Regex("[^A-Z0-9 .\\-/'&·]")

        /** Upper case, the characters an edge printer has, one line, [MAX_LENGTH] at most. */
        fun clean(text: String): String =
            text.uppercase().replace(ALLOWED, "").replace(Regex(" {2,}"), " ").take(MAX_LENGTH)

        /** What to print: nothing until the badge is earned, whatever is stored. */
        fun resolve(text: String?, style: String?, records: Map<Achievement, AchRecord>): EdgePrint =
            if (!UNLOCK.isMet(records)) EdgePrint()
            else EdgePrint(clean(text.orEmpty()).trim(), FrameNumberStyle.fromKey(style))
    }
}

/** The Craft badges, and Sunny 16, that open a technique card once earned. */
val TECHNIQUE_CARD_BADGES: List<Achievement> = listOf(
    Achievement.ZONE_THINKER,
    Achievement.PATIENT_EYE,
    Achievement.PUSHER,
    Achievement.GOLDEN_HOUR,
    Achievement.USED_WHAT_YOU_METER,
    Achievement.ADAPTED,
    Achievement.STAND_STILL,
    Achievement.SUNNY_16,
)

package com.analogvault.util

/**
 * The words on a shared badge image, worked out apart from the drawing so
 * they can be tested — what a stranger sees when the image is posted.
 *
 * No Android dependencies — unit-testable on the JVM.
 */
data class BadgeCardText(
    /** The frame number printed above the frame, "07" or "07  III". */
    val frameMark: String,
    val eyebrow: String,
    val title: String,
    val description: String,
    /** Monospaced lines under the description: when, and on what. */
    val details: List<String>,
    /** The rebate under the frame: app name, score, your edge print. */
    val rebate: String,
)

object BadgeShare {

    /**
     * @param frameNo the badge's place in the full list, 1-based
     * @param rollLine where it was earned ("the roll loaded 2026-09-16"), or null
     * @param shotIndex the frame on that roll, 0-based, or null
     */
    fun text(
        a: Achievement,
        record: AchRecord,
        frameNo: Int,
        earnedCount: Int,
        total: Int,
        rollLine: String? = null,
        shotIndex: Int? = null,
        edgePrint: String = "",
    ): BadgeCardText {
        val tier = if (a.tiered) "  " + roman(record.tier) else ""
        val eyebrow = when (a.category) {
            AchCategory.SECRET -> "Secret found"
            AchCategory.CRAFT -> "Craft achievement"
            AchCategory.MILESTONE -> "Achievement"
        }
        val description = if (a.tiered) "${a.description} ${tierGoal(a, record.tier)}" else a.description
        val details = buildList {
            add("EARNED ${record.unlockedAt}")
            // The frame on a line of its own: joined to the roll it ran off the
            // edge of the frame on a 1080-wide image.
            rollLine?.let { line ->
                add("ON $line".uppercase())
                shotIndex?.let { add("FRAME ${it + 1}") }
            }
        }
        val rebate = listOf("ANALOG VAULT", "$earnedCount/$total EARNED", edgePrint.trim())
            .filter { it.isNotBlank() }
            .joinToString("  ▸  ", prefix = "▸  ")
        return BadgeCardText(
            frameMark = frameNo.toString().padStart(2, '0') + tier,
            eyebrow = eyebrow.uppercase(),
            title = a.title.uppercase(),
            description = description,
            details = details,
            rebate = rebate,
        )
    }

    /** "(25 rolls.)" — the count behind the tier, so the image says what it took. */
    private fun tierGoal(a: Achievement, tier: Int): String {
        val goal = a.tiers.getOrNull(tier - 1) ?: return ""
        val unit = when (a) {
            Achievement.FULL_ROLL -> if (goal == 1) "roll" else "rolls"
            Achievement.MIXED_BAG -> "stocks"
            else -> return ""
        }
        return "($goal $unit.)"
    }

    /** A file name a share target will keep: the badge key and the date earned. */
    fun fileName(a: Achievement, record: AchRecord): String = "analogvault_${a.key}_${record.unlockedAt}.png"

    private fun roman(n: Int) = listOf("I", "II", "III", "IV", "V").getOrElse(n - 1) { n.toString() }
}

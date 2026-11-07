package com.analogvault.util

import com.analogvault.data.model.BulkRoll
import com.analogvault.data.model.Camera
import com.analogvault.data.model.Chemical
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Lens
import com.analogvault.data.model.Roll
import com.analogvault.data.model.Shot
import com.analogvault.data.model.isMonochrome
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Achievements, worked out from what the vault already holds.
 *
 * Nothing here is tracked as it happens: every badge is a question asked of
 * the rolls, stocks, cameras and chemistry, so a vault restored from a backup
 * earns exactly what it earned before. The few things the data cannot answer —
 * a darkroom timer run to the end, a frame logged from the meter — arrive as
 * [AchSnapshot.counters].
 *
 * The list rewards finishing things over shooting more. There are no streaks
 * and nothing daily: an app about film should never be the reason a roll gets
 * burnt.
 *
 * No Android dependencies — unit-testable on the JVM.
 */
enum class AchCategory(val label: String) {
    MILESTONE("Milestones"),
    CRAFT("Craft"),
    SECRET("Secret"),
}

/**
 * One badge. [key] is what the unlock record is stored under — fixed text
 * rather than the enum name, so a rename here never orphans a record.
 * [tiers] are the counts at which the badge is earned again; most have one.
 */
enum class Achievement(
    val key: String,
    val title: String,
    val description: String,
    val category: AchCategory,
    val tiers: List<Int> = listOf(1),
) {
    // ── Milestones ──────────────────────────────────────────────────────────
    FIRST_FRAME("first_frame", "First Frame", "Log your first shot.", AchCategory.MILESTONE),
    FULL_ROLL("full_roll", "Full Roll", "Finish a roll.", AchCategory.MILESTONE, listOf(1, 5, 25, 100)),
    THROUGH_THE_PIPELINE("pipeline", "Through the Pipeline",
        "Take a roll all the way from loaded to scanned.", AchCategory.MILESTONE),
    ROUND_TRIP("round_trip", "Round Trip",
        "Run a darkroom timer through every step to the end.", AchCategory.MILESTONE),
    SHELF_LIFE("shelf_life", "Shelf Life", "Load a roll before its expiry date.", AchCategory.MILESTONE),
    MIXED_BAG("mixed_bag", "Mixed Bag", "Shoot different film stocks.", AchCategory.MILESTONE, listOf(5, 10, 25)),
    FORMAT_HOPPER("format_hopper", "Format Hopper",
        "Shoot 135, 120 and sheet film.", AchCategory.MILESTONE, listOf(3)),
    FULL_KIT("full_kit", "Full Kit",
        "Shoot a roll on an interchangeable-lens camera with its lens picked.", AchCategory.MILESTONE),
    HUNDRED_ROLLS("hundred_rolls", "Hundred Rolls", "Load a hundred rolls.", AchCategory.MILESTONE, listOf(100)),
    THOUSAND_FRAMES("thousand_frames", "Thousand Frames", "Log a thousand frames.", AchCategory.MILESTONE, listOf(1000)),
    BULK_CUTTER("bulk_cutter", "Bulk Cutter", "Cut your first roll from bulk film.", AchCategory.MILESTONE),
    MAPPED("mapped", "Mapped", "Geotag frames in ten different places.", AchCategory.MILESTONE, listOf(10)),
    BACKED_UP("backed_up", "Backed Up", "Export a backup.", AchCategory.MILESTONE),
    EVERY_FRAME_COUNTED("every_frame", "Every Frame Counted",
        "Log every frame of a roll.", AchCategory.MILESTONE),
    HOME_BREW("home_brew", "Home Brew", "Develop a roll yourself.", AchCategory.MILESTONE),
    THREE_BATHS("three_baths", "Three Baths",
        "Develop C-41, E-6 and black-and-white rolls.", AchCategory.MILESTONE, listOf(3)),
    LAB_NOTES("lab_notes", "Lab Notes",
        "Record developer, dilution, temperature and time for ten rolls.", AchCategory.MILESTONE, listOf(10)),
    MIXOLOGIST("mixologist", "Mixologist", "Add your first chemistry.", AchCategory.MILESTONE),
    END_OF_THE_REEL("end_of_reel", "End of the Reel", "Use up a whole bulk roll.", AchCategory.MILESTONE),
    EVERY_LENS("every_lens", "Every Lens Gets a Turn",
        "Shoot with every lens you own — at least three of them.", AchCategory.MILESTONE),
    ONE_YEAR_IN("one_year_in", "One Year In",
        "Load a roll a year after your first.", AchCategory.MILESTONE),

    // ── Craft ───────────────────────────────────────────────────────────────
    ZONE_THINKER("zone_thinker", "Zone Thinker",
        "Log a frame from the meter with a zone placed.", AchCategory.CRAFT),
    PATIENT_EYE("patient_eye", "Patient Eye",
        "Log a frame from the meter with reciprocity corrected.", AchCategory.CRAFT),
    PUSHER("pusher", "Pusher", "Develop a roll shot away from box speed.", AchCategory.CRAFT),
    GOLDEN_HOUR("golden_hour", "Golden Hour",
        "Log geotagged frames in the hour after sunrise or before sunset.", AchCategory.CRAFT, listOf(10)),
    USED_WHAT_YOU_METER("metered", "Used What You Meter",
        "Log frames straight from the meter.", AchCategory.CRAFT, listOf(20)),
    ADAPTED("adapted", "Adapted",
        "Shoot a lens from another mount through an adapter.", AchCategory.CRAFT),
    STAND_STILL("stand_still", "Stand Still", "Stand-develop a roll.", AchCategory.CRAFT),

    // ── Secret ──────────────────────────────────────────────────────────────
    EXPIRED_WITH_HONOURS("expired_honours", "Expired with Honours",
        "Load a roll more than ten years past its expiry.", AchCategory.SECRET),
    SUNNY_16("sunny_16", "Sunny 16",
        "Log a frame at f/16 and one over the ISO, under a clear sky.", AchCategory.SECRET),
    SAME_SPOT("same_spot", "Same Spot, Different Year",
        "Shoot within a hundred metres of a frame from over a year before.", AchCategory.SECRET),
    LAST_FRAME_STANDING("last_frame", "Last Frame Standing",
        "Log a roll's final frame between one and five in the morning.", AchCategory.SECRET),
    UNDER_THE_RED_LIGHT("red_light", "Under the Red Light",
        "Run a darkroom timer to the end with Safelight on.", AchCategory.SECRET),
    EVERY_LAST_DROP("last_drop", "Every Last Drop",
        "Develop as many rolls as a batch of chemistry is rated for.", AchCategory.SECRET),
    BULB_NIGHT("bulb_night", "Bulb Night", "Log an exposure of thirty seconds or longer.", AchCategory.SECRET),
    HELLO_OLD_FRIEND("old_friend", "Hello, Old Friend",
        "Restore a backup into an empty vault.", AchCategory.SECRET),
    SINGIN_IN_THE_RAIN("rain", "Singin' in the Rain", "Log a frame in rain or snow.", AchCategory.SECRET),
    DUSTED_OFF("dusted_off", "Dusted Off",
        "Load a camera that sat unused for over a year.", AchCategory.SECRET),
    TWO_BODIES("two_bodies", "Two Bodies",
        "Log frames on two cameras within the same hour.", AchCategory.SECRET),
    MIDNIGHT_SUN("midnight_sun", "Midnight Sun", "Log a frame inside a polar circle.", AchCategory.SECRET),
    FIRST_LIGHT("first_light", "First Light",
        "Log a frame in the first hour of the new year.", AchCategory.SECRET),
    ;

    val tiered: Boolean get() = tiers.size > 1

    companion object {
        fun fromKey(key: String): Achievement? = entries.find { it.key == key }
    }
}

/** Names of the counters the data cannot answer for itself. */
object AchCounters {
    const val SETTING = "ach_counters"
    const val METER_LOGS = "meter_logs"
    const val ZONE_LOGS = "zone_logs"
    const val RECIPROCITY_LOGS = "reciprocity_logs"
    const val TIMER_RUNS = "timer_runs"
    const val SAFELIGHT_TIMER_RUNS = "safelight_timer_runs"
    const val BACKUP_EXPORTS = "backup_exports"
    const val FRESH_IMPORTS = "fresh_imports"

    fun decode(json: String?): Map<String, Int> = runCatching {
        JsonParser.parseString(json ?: return emptyMap()).asJsonObject.entrySet()
            .mapNotNull { (k, v) -> runCatching { k to v.asInt }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())

    fun encode(counters: Map<String, Int>): String =
        JsonObject().apply { counters.toSortedMap().forEach { (k, v) -> addProperty(k, v) } }.toString()
}

/** Where a badge was earned: a roll, and the frame on it when one frame did it. */
data class AchEvidence(
    val rollId: String? = null,
    val shotIndex: Int? = null,
    /** "yyyy-MM-dd" or "yyyy-MM-dd HH:mm", or blank when the data has no date. */
    val date: String = "",
)

data class AchProgress(
    val achievement: Achievement,
    /** The count measured against the tiers. */
    val current: Int,
    /** How many tiers are reached; 0 means not earned. */
    val tier: Int,
    /** What earned the highest tier reached, when it can be pinned to a roll. */
    val evidence: AchEvidence? = null,
    /** Overrides the tier list when the goal depends on the vault (Every Lens). */
    val targetOverride: Int? = null,
) {
    val earned: Boolean get() = tier > 0
    /** The next count to reach, or the last one once every tier is reached. */
    val target: Int get() = targetOverride
        ?: achievement.tiers.getOrElse(tier) { achievement.tiers.last() }
}

data class AchSnapshot(
    val rolls: List<Roll> = emptyList(),
    val films: List<FilmStock> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val lenses: List<Lens> = emptyList(),
    val chemicals: List<Chemical> = emptyList(),
    val bulkRolls: List<BulkRoll> = emptyList(),
    val counters: Map<String, Int> = emptyMap(),
    /** The zone shot times were written in — the phone's own. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

object Achievements {

    private val SHOT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** A logged frame with what the checks need to know about it. */
    private class Frame(val roll: Roll, val index: Int, val shot: Shot) {
        val time: LocalDateTime? = parseShotTime(shot.date)
        val latLon: Pair<Double, Double>? = parseLatLon(shot.location)
        val evidence get() = AchEvidence(roll.id, index, shot.date)
    }

    fun evaluate(s: AchSnapshot): List<AchProgress> {
        val films = s.films.associateBy { it.id }
        val cameras = s.cameras.associateBy { it.id }
        val lenses = s.lenses.associateBy { it.id }
        val frames = s.rolls.flatMap { r -> r.shots.mapIndexed { i, shot -> Frame(r, i, shot) } }
        val datedFrames = frames.filter { it.time != null }.sortedBy { it.time }
        val geoFrames = frames.filter { it.latLon != null }
        val developed = s.rolls.filter { it.developed }
        val shotRolls = s.rolls.filter { it.shots.isNotEmpty() }
        fun rollEvidence(r: Roll) = AchEvidence(r.id, null, lastDate(r))
        fun filmOf(r: Roll) = films[r.filmId]

        return Achievement.entries.map { a ->
            when (a) {
                Achievement.FIRST_FRAME -> counted(a, frames.map { it.evidence })
                Achievement.FULL_ROLL ->
                    counted(a, s.rolls.filter { it.finished || it.developed || it.scanned }.map(::rollEvidence))
                Achievement.THROUGH_THE_PIPELINE -> counted(a, s.rolls.filter { it.scanned }.map(::rollEvidence))
                Achievement.ROUND_TRIP -> counter(a, s.counters[AchCounters.TIMER_RUNS])
                Achievement.SHELF_LIFE -> counted(a, s.rolls.filter { r ->
                    val start = parseDay(r.startDate)
                    val expiry = filmOf(r)?.let { parseExpiry(it.expiryDate) }
                    start != null && expiry != null && !start.isAfter(expiry)
                }.map { AchEvidence(it.id, null, it.startDate) })
                Achievement.MIXED_BAG -> distinct(a, s.rolls.mapNotNull { r ->
                    val f = filmOf(r)?.takeIf { it.name.isNotBlank() } ?: return@mapNotNull null
                    "${f.brand.trim().lowercase()}|${f.name.trim().lowercase()}" to
                        AchEvidence(r.id, null, r.startDate)
                })
                Achievement.FORMAT_HOPPER -> distinct(a, s.rolls.mapNotNull { r ->
                    val format = filmOf(r)?.filmFormat ?: return@mapNotNull null
                    val group = when {
                        format.startsWith("135") -> "135"
                        format.startsWith("120") || format.startsWith("220") -> "120"
                        format.startsWith("4x5") || format.startsWith("8x10") -> "sheet"
                        else -> return@mapNotNull null
                    }
                    group to AchEvidence(r.id, null, r.startDate)
                })
                Achievement.FULL_KIT -> counted(a, shotRolls.filter { r ->
                    cameras[r.cameraId]?.lensSystem == "interchangeable" && lenses[r.cameraLensId] != null
                }.map { AchEvidence(it.id, null, it.startDate) })
                Achievement.HUNDRED_ROLLS -> counted(a, s.rolls.map { AchEvidence(it.id, null, it.startDate) })
                Achievement.THOUSAND_FRAMES -> counted(a, frames.map { it.evidence })
                Achievement.BULK_CUTTER -> {
                    val cut = s.films.any { it.storage == "Bulk" } || s.bulkRolls.any { it.usedFrames > 0 }
                    flag(a, cut)
                }
                Achievement.MAPPED -> distinct(a, geoFrames.map { f ->
                    val (lat, lon) = f.latLon!!
                    // About a kilometre square at mid latitudes: different
                    // places, not different ends of the same street.
                    "${Math.round(lat * 100)}|${Math.round(lon * 100)}" to f.evidence
                })
                Achievement.BACKED_UP -> counter(a, s.counters[AchCounters.BACKUP_EXPORTS])
                Achievement.EVERY_FRAME_COUNTED -> counted(a, s.rolls.mapNotNull { r ->
                    val total = frameTotal(r, filmOf(r))
                    if (total > 0 && r.shots.size >= total)
                        AchEvidence(r.id, total - 1, r.shots[total - 1].date) else null
                })
                Achievement.HOME_BREW -> counted(a, developed.filter { it.isSelfDev }.map(::rollEvidence))
                Achievement.THREE_BATHS -> distinct(a, developed.mapNotNull { r ->
                    processFamily(r, filmOf(r))?.let { it to rollEvidence(r) }
                })
                Achievement.LAB_NOTES -> counted(a, developed.filter { r ->
                    val d = r.devLog ?: return@filter false
                    d.developer.isNotBlank() && d.dilution.isNotBlank() &&
                        d.temp.isNotBlank() && d.devTime.isNotBlank()
                }.map(::rollEvidence))
                Achievement.MIXOLOGIST -> counted(a, s.chemicals.map { AchEvidence(date = it.mixDate) })
                Achievement.END_OF_THE_REEL ->
                    flag(a, s.bulkRolls.any { it.totalFrames > 0 && it.usedFrames >= it.totalFrames })
                Achievement.EVERY_LENS -> everyLens(a, s, shotRolls)
                Achievement.ONE_YEAR_IN -> {
                    val starts = s.rolls.mapNotNull { r -> parseDay(r.startDate)?.let { it to r } }
                        .sortedBy { it.first }
                    val first = starts.firstOrNull()?.first
                    val later = first?.let { f -> starts.firstOrNull { !it.first.isBefore(f.plusYears(1)) } }
                    flag(a, later != null, later?.second?.let { AchEvidence(it.id, null, it.startDate) })
                }

                Achievement.ZONE_THINKER -> counter(a, s.counters[AchCounters.ZONE_LOGS])
                Achievement.PATIENT_EYE -> counter(a, s.counters[AchCounters.RECIPROCITY_LOGS])
                Achievement.PUSHER -> counted(a, developed.filter { isPushedOrPulled(it, filmOf(it)) }
                    .map(::rollEvidence))
                Achievement.GOLDEN_HOUR -> counted(a, geoFrames.filter { inGoldenHour(it, s.zone) }
                    .map { it.evidence })
                Achievement.USED_WHAT_YOU_METER -> counter(a, s.counters[AchCounters.METER_LOGS])
                Achievement.ADAPTED -> counted(a, shotRolls.filter { r ->
                    val cam = cameras[r.cameraId] ?: return@filter false
                    val lens = lenses[r.cameraLensId] ?: return@filter false
                    val mount = lens.mount.trim()
                    mount.isNotBlank() && cam.mount.isNotBlank() &&
                        !mount.equals(cam.mount.trim(), ignoreCase = true) &&
                        cam.adapterMounts.any { it.trim().equals(mount, ignoreCase = true) }
                }.map { AchEvidence(it.id, null, it.startDate) })
                Achievement.STAND_STILL -> counted(a, developed.filter {
                    it.devLog?.process?.contains("stand", ignoreCase = true) == true
                }.map(::rollEvidence))

                Achievement.EXPIRED_WITH_HONOURS -> counted(a, s.rolls.filter { r ->
                    val start = parseDay(r.startDate)
                    val expiry = filmOf(r)?.let { parseExpiry(it.expiryDate) }
                    start != null && expiry != null && !start.isBefore(expiry.plusYears(10))
                }.map { AchEvidence(it.id, null, it.startDate) })
                Achievement.SUNNY_16 -> counted(a, frames.filter { isSunny16(it, filmOf(it.roll)) }
                    .map { it.evidence })
                Achievement.SAME_SPOT -> {
                    val hit = sameSpot(geoFrames)
                    flag(a, hit != null, hit?.evidence)
                }
                Achievement.LAST_FRAME_STANDING -> counted(a, s.rolls.mapNotNull { r ->
                    val total = frameTotal(r, filmOf(r))
                    if (total <= 0 || r.shots.size < total) return@mapNotNull null
                    val hour = parseShotTime(r.shots[total - 1].date)?.hour ?: return@mapNotNull null
                    if (hour in 1..4) AchEvidence(r.id, total - 1, r.shots[total - 1].date) else null
                })
                Achievement.UNDER_THE_RED_LIGHT -> counter(a, s.counters[AchCounters.SAFELIGHT_TIMER_RUNS])
                Achievement.EVERY_LAST_DROP -> flag(a, s.chemicals.any { c ->
                    val capacity = c.maxRolls.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@any false
                    chemicalRollCount(c, s.rolls) >= capacity
                })
                Achievement.BULB_NIGHT -> counted(a, frames.filter { f ->
                    val sh = f.shot.shutter.trim()
                    sh == "B" || (sh.isNotEmpty() && Constants.evalShutter(sh) >= 30.0)
                }.map { it.evidence })
                Achievement.HELLO_OLD_FRIEND -> counter(a, s.counters[AchCounters.FRESH_IMPORTS])
                Achievement.SINGIN_IN_THE_RAIN -> counted(a, frames.filter { f ->
                    val w = f.shot.weather.lowercase()
                    RAIN_WORDS.any { it in w }
                }.map { it.evidence })
                Achievement.DUSTED_OFF -> {
                    val gap = s.rolls.filter { it.cameraId.isNotBlank() }
                        .mapNotNull { r -> parseDay(r.startDate)?.let { it to r } }
                        .groupBy { it.second.cameraId }.values
                        .flatMap { loads ->
                            loads.sortedBy { it.first }.zipWithNext()
                                .filter { (a, b) -> !b.first.isBefore(a.first.plusYears(1)) }
                                .map { it.second.second }
                        }.minByOrNull { it.startDate }
                    flag(a, gap != null, gap?.let { AchEvidence(it.id, null, it.startDate) })
                }
                Achievement.TWO_BODIES -> {
                    val hit = twoBodies(datedFrames)
                    flag(a, hit != null, hit?.evidence)
                }
                Achievement.MIDNIGHT_SUN -> counted(a, geoFrames.filter { abs(it.latLon!!.first) >= POLAR_CIRCLE }
                    .map { it.evidence })
                Achievement.FIRST_LIGHT -> counted(a, datedFrames.filter { f ->
                    val t = f.time!!
                    t.monthValue == 1 && t.dayOfMonth == 1 && t.hour == 0
                }.map { it.evidence })
            }
        }
    }

    /**
     * Rolls developed in a batch of chemistry: the manual count when one is
     * set, otherwise developed rolls whose developer names it.
     */
    fun chemicalRollCount(chem: Chemical, rolls: List<Roll>): Int {
        if (chem.manualRolls >= 0) return chem.manualRolls
        return rolls.count { r ->
            r.developed && r.devLog?.developer?.contains(chem.name, ignoreCase = true) == true
        }
    }

    // ── Building progress ───────────────────────────────────────────────────

    /** Earned once per tier, counting [items] in the order they happened. */
    private fun counted(a: Achievement, items: List<AchEvidence>): AchProgress {
        val ordered = items.sortedBy { it.date.ifBlank { "9999" } }
        val tier = a.tiers.count { ordered.size >= it }
        return AchProgress(a, ordered.size, tier, if (tier > 0) ordered[a.tiers[tier - 1] - 1] else null)
    }

    /** Like [counted], but each key counts once, at its first appearance. */
    private fun distinct(a: Achievement, items: List<Pair<String, AchEvidence>>): AchProgress =
        counted(a, items.sortedBy { it.second.date.ifBlank { "9999" } }.distinctBy { it.first }.map { it.second })

    private fun counter(a: Achievement, value: Int?): AchProgress {
        val n = value ?: 0
        return AchProgress(a, n, a.tiers.count { n >= it })
    }

    private fun flag(a: Achievement, earned: Boolean, evidence: AchEvidence? = null): AchProgress =
        AchProgress(a, if (earned) 1 else 0, if (earned) 1 else 0, evidence)

    // ── Checks too long to sit inline ───────────────────────────────────────

    private const val POLAR_CIRCLE = 66.5622
    private val RAIN_WORDS = listOf("rain", "drizzle", "snow", "sleet", "thunderstorm", "shower")

    private fun everyLens(a: Achievement, s: AchSnapshot, shotRolls: List<Roll>): AchProgress {
        val owned = s.lenses
        val namesUsed = shotRolls.flatMap { r -> r.shots.map { it.lens.trim().lowercase() } }.toSet()
        val idsUsed = shotRolls.map { it.cameraLensId }.toSet()
        val used = owned.count { it.id in idsUsed || (it.name.isNotBlank() && it.name.trim().lowercase() in namesUsed) }
        val earned = owned.size >= 3 && used == owned.size
        return AchProgress(a, used, if (earned) 1 else 0, targetOverride = maxOf(3, owned.size))
    }

    private fun isPushedOrPulled(r: Roll, film: FilmStock?): Boolean {
        val shotAt = r.pushIso.trim().toIntOrNull()?.takeIf { it > 0 } ?: return false
        val box = film?.iso?.takeIf { it > 0 } ?: return false
        return shotAt != box
    }

    private fun processFamily(r: Roll, film: FilmStock?): String? {
        val process = r.devLog?.process.orEmpty()
        return when {
            process.contains("C-41", ignoreCase = true) -> "C-41"
            process.contains("E-6", ignoreCase = true) -> "E-6"
            process.contains("B&W", ignoreCase = true) -> "B&W"
            film == null -> null
            film.type.contains("C-41", ignoreCase = true) -> "C-41"
            film.type.contains("E-6", ignoreCase = true) -> "E-6"
            film.isMonochrome() -> "B&W"
            else -> null
        }
    }

    private fun inGoldenHour(f: Frame, zone: ZoneId): Boolean {
        val time = f.time ?: return false
        val (lat, lon) = f.latLon ?: return false
        val (rise, set) = SunTimes.riseAndSet(time.toLocalDate(), lat, lon) ?: return false
        val at = time.atZone(zone).toInstant()
        val hour = Duration.ofMinutes(60)
        return (!at.isBefore(rise) && !at.isAfter(rise.plus(hour))) ||
            (!at.isBefore(set.minus(hour)) && !at.isAfter(set))
    }

    /** f/16 within a third of a stop, and a shutter within half a stop of 1/ISO, on a clear day. */
    private fun isSunny16(f: Frame, film: FilmStock?): Boolean {
        val weather = f.shot.weather.lowercase()
        if ("clear" !in weather && "sun" !in weather) return false
        val aperture = f.shot.aperture.removePrefix("f/").removePrefix("F/").toDecimalOrNull() ?: return false
        if (aperture <= 0.0 || abs(2 * log2(aperture / 16.0)) > 0.34) return false
        val shutter = f.shot.shutter.trim()
        if (shutter.isEmpty() || shutter == "B") return false
        val iso = f.shot.iso.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: f.roll.pushIso.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: film?.iso?.takeIf { it > 0 }
            ?: return false
        return abs(log2(Constants.evalShutter(shutter) * iso)) <= 0.5
    }

    /** The later frame of a pair shot within 100 m of each other and a year or more apart. */
    private fun sameSpot(geo: List<Frame>): Frame? {
        val dated = geo.filter { it.time != null }.sortedBy { it.latLon!!.first }
        var best: Frame? = null
        for (i in dated.indices) {
            val a = dated[i]
            var j = i + 1
            // 100 m of latitude is under 0.0009°; anything further north is out of reach.
            while (j < dated.size && dated[j].latLon!!.first - a.latLon!!.first <= 0.0009) {
                val b = dated[j]
                if (metresBetween(a.latLon!!, b.latLon!!) <= 100.0) {
                    val (early, late) = if (a.time!! <= b.time!!) a to b else b to a
                    if (!late.time!!.isBefore(early.time!!.plusYears(1)) &&
                        (best == null || late.time!! < best.time!!)) best = late
                }
                j++
            }
        }
        return best
    }

    /** The first frame logged within an hour of a frame on a different camera. */
    private fun twoBodies(dated: List<Frame>): Frame? {
        val lastByCamera = HashMap<String, LocalDateTime>()
        for (f in dated) {
            val cam = f.roll.cameraId
            if (cam.isBlank()) continue
            val t = f.time!!
            if (lastByCamera.any { (other, at) -> other != cam && ChronoUnit.MINUTES.between(at, t) <= 60 }) return f
            lastByCamera[cam] = t
        }
        return null
    }

    private fun metresBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLon / 2).let { it * it }
        return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    // ── Reading the vault's dates ───────────────────────────────────────────

    private fun frameTotal(r: Roll, film: FilmStock?): Int =
        r.totalShots.takeIf { it > 0 } ?: film?.frameCount ?: 36

    private fun lastDate(r: Roll): String =
        r.shots.map { it.date }.filter { it.isNotBlank() }.maxOrNull() ?: r.startDate

    fun parseShotTime(s: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(s.trim().take(16), SHOT_TIME) }.getOrNull()

    fun parseDay(s: String): LocalDate? =
        runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()

    /** Expiry as the stash stores it: a day, a month (good to its last day) or a year. */
    fun parseExpiry(s: String): LocalDate? {
        val t = s.trim()
        return runCatching {
            when (t.length) {
                4 -> LocalDate.of(t.toInt(), 12, 31)
                7 -> YearMonth.parse(t).atEndOfMonth()
                else -> LocalDate.parse(t.take(10))
            }
        }.getOrNull()
    }

    /**
     * A stored `"lat, lon"`, or null. Shot locations are free text; this
     * mirrors the map's own reading so the two never disagree about which
     * frames are geotagged.
     */
    private fun parseLatLon(location: String): Pair<Double, Double>? =
        com.analogvault.ui.components.parseLatLon(location)
}

/** When and with what a badge was earned, as stored in the `achievements` setting. */
data class AchRecord(
    val tier: Int,
    /** "yyyy-MM-dd". */
    val unlockedAt: String,
    val rollId: String? = null,
    val shotIndex: Int? = null,
)

/**
 * The unlock records: kept, not recomputed, so a badge stays earned when the
 * roll that earned it is deleted, and the date it was earned survives.
 */
object AchievementRecords {
    const val SETTING = "achievements"

    fun decode(json: String?): Map<Achievement, AchRecord> = runCatching {
        decodeObject(JsonParser.parseString(json ?: return emptyMap()).asJsonObject)
    }.getOrDefault(emptyMap())

    fun decodeObject(obj: JsonObject): Map<Achievement, AchRecord> =
        obj.entrySet().mapNotNull { (key, value) ->
            val a = Achievement.fromKey(key) ?: return@mapNotNull null
            runCatching {
                val o = value.asJsonObject
                a to AchRecord(
                    tier = o.get("tier").asInt,
                    unlockedAt = o.get("at").asString,
                    rollId = o.get("roll")?.takeIf { !it.isJsonNull }?.asString,
                    shotIndex = o.get("shot")?.takeIf { !it.isJsonNull }?.asInt,
                )
            }.getOrNull()
        }.toMap()

    fun encodeObject(records: Map<Achievement, AchRecord>): JsonObject = JsonObject().apply {
        records.entries.sortedBy { it.key.ordinal }.forEach { (a, r) ->
            add(a.key, JsonObject().apply {
                addProperty("tier", r.tier)
                addProperty("at", r.unlockedAt)
                r.rollId?.let { addProperty("roll", it) }
                r.shotIndex?.let { addProperty("shot", it) }
            })
        }
    }

    fun encode(records: Map<Achievement, AchRecord>): String = encodeObject(records).toString()

    /**
     * Two sets of records as one: every badge either holds, at the higher
     * tier, and at the earlier date when both hold the same tier. Used when a
     * backup is restored on top of a vault that has earned things since.
     */
    fun merge(a: Map<Achievement, AchRecord>, b: Map<Achievement, AchRecord>): Map<Achievement, AchRecord> =
        (a.keys + b.keys).associateWith { key ->
            val x = a[key]
            val y = b[key]
            when {
                x == null -> y!!
                y == null -> x
                x.tier != y.tier -> if (x.tier > y.tier) x else y
                else -> if (x.unlockedAt <= y.unlockedAt) x else y
            }
        }

    /** What [reconcile] decided: the records to store, and what was newly earned. */
    data class Outcome(
        val records: Map<Achievement, AchRecord>,
        val newlyEarned: List<Achievement>,
    )

    /**
     * Folds fresh progress into the stored records. Records only ever move up:
     * a tier the data no longer shows (a roll deleted) stays earned.
     *
     * [backfill] is the first run after achievements arrive, when everything
     * already in the vault is earned at once. Those badges take the date their
     * evidence carries rather than today, so a vault five years old does not
     * claim to have earned its first frame this morning.
     */
    fun reconcile(
        stored: Map<Achievement, AchRecord>,
        progress: List<AchProgress>,
        today: LocalDate,
        backfill: Boolean,
    ): Outcome {
        val records = stored.toMutableMap()
        val newly = mutableListOf<Achievement>()
        for (p in progress) {
            if (!p.earned) continue
            val old = records[p.achievement]
            if (old != null && old.tier >= p.tier) continue
            val date = if (backfill) {
                p.evidence?.date?.let { Achievements.parseDay(it) }?.takeIf { !it.isAfter(today) } ?: today
            } else today
            records[p.achievement] = AchRecord(p.tier, date.toString(), p.evidence?.rollId, p.evidence?.shotIndex)
            newly += p.achievement
        }
        return Outcome(records, newly)
    }
}

package com.analogvault.util

import com.analogvault.data.model.BulkRoll
import com.analogvault.data.model.Camera
import com.analogvault.data.model.Chemical
import com.analogvault.data.model.DevLog
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Lens
import com.analogvault.data.model.Roll
import com.analogvault.data.model.ScanLog
import com.analogvault.data.model.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AchievementsTest {

    // ── Fixtures ────────────────────────────────────────────────────────────

    private val hp5 = FilmStock(id = "hp5", name = "HP5 Plus", brand = "Ilford", type = "Black & White",
        iso = 400, filmFormat = "135 (35mm)", frameCount = 36, expiryDate = "2027-06")
    private val portra = FilmStock(id = "portra", name = "Portra 400", brand = "Kodak",
        type = "Color Negative (C-41)", iso = 400, filmFormat = "120", frameCount = 12)

    private fun shot(date: String = "", shutter: String = "1/125", aperture: String = "8",
                     location: String = "", weather: String = "", lens: String = "", iso: String = "") =
        Shot(id = date + shutter, shutter = shutter, aperture = aperture, iso = iso, lens = lens,
            location = location, weather = weather, date = date)

    private fun roll(id: String, film: FilmStock = hp5, start: String = "2026-01-10",
                     shots: List<Shot> = emptyList(), camera: String = "", lens: String = "",
                     finished: Boolean = false, devLog: DevLog? = null, scanned: Boolean = false,
                     pushIso: String = "", total: Int = 0, selfDev: Boolean = false) =
        Roll(id = id, filmId = film.id, cameraId = camera, cameraLensId = lens, startDate = start,
            finished = finished || devLog != null, developed = devLog != null, scanned = scanned,
            scanLog = if (scanned) ScanLog(method = "Flatbed") else null,
            shots = shots, devLog = devLog, pushIso = pushIso, totalShots = total, isSelfDev = selfDev)

    private fun eval(
        rolls: List<Roll> = emptyList(),
        films: List<FilmStock> = listOf(hp5, portra),
        cameras: List<Camera> = emptyList(),
        lenses: List<Lens> = emptyList(),
        chemicals: List<Chemical> = emptyList(),
        bulk: List<BulkRoll> = emptyList(),
        counters: Map<String, Int> = emptyMap(),
    ) = Achievements.evaluate(AchSnapshot(rolls, films, cameras, lenses, chemicals, bulk, counters,
        zone = ZoneOffset.UTC)).associateBy { it.achievement }

    private fun Map<Achievement, AchProgress>.earned(a: Achievement) = getValue(a).earned

    private val developedBw = DevLog(process = "B&W (Standard)", developer = "HC-110",
        dilution = "B", temp = "20", devTime = "5:00")

    // ── Milestones ──────────────────────────────────────────────────────────

    @Test fun `empty vault earns nothing`() {
        assertTrue(eval().values.none { it.earned })
    }

    @Test fun `first frame points at the earliest shot`() {
        val p = eval(listOf(
            roll("b", shots = listOf(shot("2026-03-01 10:00"))),
            roll("a", shots = listOf(shot("2026-02-01 10:00"), shot("2026-02-02 10:00"))),
        )).getValue(Achievement.FIRST_FRAME)
        assertTrue(p.earned)
        assertEquals("a", p.evidence?.rollId)
        assertEquals(0, p.evidence?.shotIndex)
    }

    @Test fun `full roll climbs its tiers`() {
        val five = (1..5).map { roll("r$it", finished = true) }
        val p = eval(five + roll("open")).getValue(Achievement.FULL_ROLL)
        assertEquals(2, p.tier)
        assertEquals(25, p.target)
    }

    @Test fun `pipeline needs a scanned roll`() {
        assertFalse(eval(listOf(roll("a", devLog = developedBw))).earned(Achievement.THROUGH_THE_PIPELINE))
        assertTrue(eval(listOf(roll("a", devLog = developedBw, scanned = true))).earned(Achievement.THROUGH_THE_PIPELINE))
    }

    @Test fun `shelf life counts the whole expiry month`() {
        assertTrue(eval(listOf(roll("a", start = "2027-06-30"))).earned(Achievement.SHELF_LIFE))
        assertFalse(eval(listOf(roll("a", start = "2027-07-01"))).earned(Achievement.SHELF_LIFE))
    }

    @Test fun `mixed bag counts distinct stocks not rolls`() {
        val films = (1..5).map { FilmStock(id = "f$it", name = "Stock $it", brand = "B") } +
            FilmStock(id = "dup", name = "stock 1 ", brand = "b")
        val rolls = films.map { roll("r${it.id}", film = it) }
        val p = eval(rolls, films = films).getValue(Achievement.MIXED_BAG)
        assertEquals(5, p.current)
        assertEquals(1, p.tier)
    }

    @Test fun `format hopper needs 135, 120 and sheet`() {
        val sheet = FilmStock(id = "s", name = "Delta 100 4x5", filmFormat = "4x5")
        val films = listOf(hp5, portra, sheet)
        assertFalse(eval(listOf(roll("a"), roll("b", film = portra)), films = films).earned(Achievement.FORMAT_HOPPER))
        assertTrue(eval(listOf(roll("a"), roll("b", film = portra), roll("c", film = sheet)), films = films)
            .earned(Achievement.FORMAT_HOPPER))
    }

    @Test fun `full kit and adapted read the camera and lens`() {
        val body = Camera(id = "fm2", lensSystem = "interchangeable", mount = "Nikon F", adapterMounts = listOf("M42"))
        val nikkor = Lens(id = "n50", name = "Nikkor 50", mount = "Nikon F")
        val takumar = Lens(id = "t55", name = "Takumar 55", mount = "M42")
        val shots = listOf(shot("2026-01-10 12:00"))
        val kit = eval(listOf(roll("a", camera = "fm2", lens = "n50", shots = shots)),
            cameras = listOf(body), lenses = listOf(nikkor, takumar))
        assertTrue(kit.earned(Achievement.FULL_KIT))
        assertFalse(kit.earned(Achievement.ADAPTED))
        val adapted = eval(listOf(roll("a", camera = "fm2", lens = "t55", shots = shots)),
            cameras = listOf(body), lenses = listOf(nikkor, takumar))
        assertTrue(adapted.earned(Achievement.ADAPTED))
    }

    @Test fun `mapped counts kilometre cells`() {
        val sameCell = (0 until 5).map { shot("2026-01-10 12:0$it", location = "51.500$it, -0.120$it") }
        assertEquals(1, eval(listOf(roll("a", shots = sameCell))).getValue(Achievement.MAPPED).current)
        val spread = (0 until 10).map { shot("2026-01-10 12:0$it", location = "${50 + it}.0, 0.0") }
        assertTrue(eval(listOf(roll("a", shots = spread))).earned(Achievement.MAPPED))
    }

    @Test fun `every frame counted uses the roll's own length`() {
        val shots = (1..12).map { shot("2026-01-10 12:${it.toString().padStart(2, '0')}") }
        val p = eval(listOf(roll("a", film = portra, shots = shots))).getValue(Achievement.EVERY_FRAME_COUNTED)
        assertTrue(p.earned)
        assertEquals(11, p.evidence?.shotIndex)
        assertFalse(eval(listOf(roll("a", film = portra, shots = shots.take(11), total = 12)))
            .earned(Achievement.EVERY_FRAME_COUNTED))
    }

    @Test fun `development badges`() {
        val c41 = DevLog(process = "C-41 (Color)")
        val e6 = DevLog(process = "E-6 (Slide)")
        val stand = DevLog(process = "B&W (Stand)")
        val p = eval(listOf(
            roll("a", devLog = developedBw, selfDev = true),
            roll("b", film = portra, devLog = c41),
            roll("c", devLog = stand),
        ))
        assertTrue(p.earned(Achievement.HOME_BREW))
        assertTrue(p.earned(Achievement.STAND_STILL))
        assertEquals(2, p.getValue(Achievement.THREE_BATHS).current)
        assertEquals(1, p.getValue(Achievement.LAB_NOTES).current)
        val all = eval(listOf(roll("a", devLog = developedBw), roll("b", devLog = c41), roll("c", devLog = e6)))
        assertTrue(all.earned(Achievement.THREE_BATHS))
    }

    @Test fun `pusher needs a developed roll away from box speed`() {
        assertFalse(eval(listOf(roll("a", pushIso = "1600"))).earned(Achievement.PUSHER))
        assertFalse(eval(listOf(roll("a", pushIso = "400", devLog = developedBw))).earned(Achievement.PUSHER))
        assertTrue(eval(listOf(roll("a", pushIso = "1600", devLog = developedBw))).earned(Achievement.PUSHER))
        assertTrue(eval(listOf(roll("a", pushIso = "200", devLog = developedBw))).earned(Achievement.PUSHER))
    }

    @Test fun `bulk badges`() {
        assertTrue(eval(bulk = listOf(BulkRoll(id = "b", totalFrames = 600, usedFrames = 38)))
            .earned(Achievement.BULK_CUTTER))
        val ended = eval(bulk = listOf(BulkRoll(id = "b", totalFrames = 600, usedFrames = 600)))
        assertTrue(ended.earned(Achievement.END_OF_THE_REEL))
    }

    @Test fun `every lens needs three lenses and every one used`() {
        val lenses = listOf(Lens(id = "1", name = "A"), Lens(id = "2", name = "B"), Lens(id = "3", name = "C"))
        val s = listOf(shot("2026-01-10 12:00"))
        val two = eval(listOf(roll("a", lens = "1", shots = s), roll("b", lens = "2", shots = s)), lenses = lenses)
        assertFalse(two.earned(Achievement.EVERY_LENS))
        assertEquals(2, two.getValue(Achievement.EVERY_LENS).current)
        // The third one only appears on a shot's lens field — that counts too.
        val three = eval(listOf(roll("a", lens = "1", shots = s), roll("b", lens = "2",
            shots = listOf(shot("2026-01-10 12:00", lens = "C")))), lenses = lenses)
        assertTrue(three.earned(Achievement.EVERY_LENS))
    }

    @Test fun `one year in`() {
        assertFalse(eval(listOf(roll("a", start = "2025-01-10"), roll("b", start = "2026-01-09")))
            .earned(Achievement.ONE_YEAR_IN))
        assertTrue(eval(listOf(roll("a", start = "2025-01-10"), roll("b", start = "2026-01-10")))
            .earned(Achievement.ONE_YEAR_IN))
    }

    @Test fun `counters drive the tracked badges`() {
        val p = eval(counters = mapOf(AchCounters.METER_LOGS to 20, AchCounters.TIMER_RUNS to 1,
            AchCounters.BACKUP_EXPORTS to 1, AchCounters.ZONE_LOGS to 1))
        assertTrue(p.earned(Achievement.USED_WHAT_YOU_METER))
        assertTrue(p.earned(Achievement.ROUND_TRIP))
        assertTrue(p.earned(Achievement.BACKED_UP))
        assertTrue(p.earned(Achievement.ZONE_THINKER))
        assertFalse(p.earned(Achievement.PATIENT_EYE))
        assertFalse(eval(counters = mapOf(AchCounters.METER_LOGS to 19)).earned(Achievement.USED_WHAT_YOU_METER))
    }

    @Test fun `golden hour counts frames near sunrise and sunset`() {
        // Greenwich, 21 June: sunset 20:21 UTC. Times are written in UTC here.
        val at = "51.4779, 0.0"
        val golden = (0 until 10).map { shot("2026-06-21 19:5$it", location = at) }
        assertTrue(eval(listOf(roll("a", shots = golden))).earned(Achievement.GOLDEN_HOUR))
        val noon = (0 until 10).map { shot("2026-06-21 12:0$it", location = at) }
        assertEquals(0, eval(listOf(roll("a", shots = noon))).getValue(Achievement.GOLDEN_HOUR).current)
    }

    // ── Secrets ─────────────────────────────────────────────────────────────

    @Test fun `expired with honours needs ten years`() {
        val old = FilmStock(id = "old", name = "Old", expiryDate = "2010-05")
        assertTrue(eval(listOf(roll("a", film = old, start = "2020-06-01")), films = listOf(old))
            .earned(Achievement.EXPIRED_WITH_HONOURS))
        assertFalse(eval(listOf(roll("a", film = old, start = "2020-05-01")), films = listOf(old))
            .earned(Achievement.EXPIRED_WITH_HONOURS))
    }

    @Test fun `sunny 16`() {
        val clear = "18°C, clear sky, 0% cloud"
        assertTrue(eval(listOf(roll("a", shots = listOf(shot("2026-06-01 12:00", "1/500", "16", weather = clear)))))
            .earned(Achievement.SUNNY_16))
        assertFalse(eval(listOf(roll("a", shots = listOf(shot("2026-06-01 12:00", "1/500", "8", weather = clear)))))
            .earned(Achievement.SUNNY_16))
        assertFalse(eval(listOf(roll("a", shots = listOf(shot("2026-06-01 12:00", "1/500", "16",
            weather = "12°C, overcast clouds"))))).earned(Achievement.SUNNY_16))
        // ISO 100 written on the shot itself beats the stock's 400.
        assertTrue(eval(listOf(roll("a", shots = listOf(shot("2026-06-01 12:00", "1/125", "f/16",
            weather = clear, iso = "100"))))).earned(Achievement.SUNNY_16))
    }

    @Test fun `same spot needs a year and a hundred metres`() {
        val here = "48.85837, 2.29448"
        val near = "48.85880, 2.29448"   // ~48 m north
        val far = "48.86000, 2.29448"    // ~180 m north
        assertTrue(eval(listOf(roll("a", shots = listOf(shot("2024-05-01 10:00", location = here),
            shot("2025-05-02 10:00", location = near))))).earned(Achievement.SAME_SPOT))
        assertFalse(eval(listOf(roll("a", shots = listOf(shot("2024-05-01 10:00", location = here),
            shot("2025-05-02 10:00", location = far))))).earned(Achievement.SAME_SPOT))
        assertFalse(eval(listOf(roll("a", shots = listOf(shot("2024-05-01 10:00", location = here),
            shot("2025-04-01 10:00", location = near))))).earned(Achievement.SAME_SPOT))
    }

    @Test fun `last frame standing`() {
        val shots = (1..11).map { shot("2026-01-10 20:${it.toString().padStart(2, '0')}") } +
            shot("2026-01-11 02:30")
        assertTrue(eval(listOf(roll("a", film = portra, shots = shots))).earned(Achievement.LAST_FRAME_STANDING))
        val early = shots.dropLast(1) + shot("2026-01-11 05:00")
        assertFalse(eval(listOf(roll("a", film = portra, shots = early))).earned(Achievement.LAST_FRAME_STANDING))
    }

    @Test fun `every last drop uses the chemistry count`() {
        val chem = Chemical(id = "c", name = "HC-110", maxRolls = "2")
        val dev = listOf(roll("a", devLog = developedBw), roll("b", devLog = developedBw))
        assertTrue(eval(dev, chemicals = listOf(chem)).earned(Achievement.EVERY_LAST_DROP))
        assertFalse(eval(dev.take(1), chemicals = listOf(chem)).earned(Achievement.EVERY_LAST_DROP))
        assertTrue(eval(chemicals = listOf(chem.copy(manualRolls = 2))).earned(Achievement.EVERY_LAST_DROP))
    }

    @Test fun `bulb night, rain, midnight sun, first light`() {
        val p = eval(listOf(roll("a", shots = listOf(
            shot("2026-01-01 00:20", shutter = "30s"),
            shot("2026-01-02 12:00", weather = "4°C, light rain"),
            shot("2026-06-21 23:50", location = "69.6492, 18.9553"),
        ))))
        assertTrue(p.earned(Achievement.BULB_NIGHT))
        assertTrue(p.earned(Achievement.SINGIN_IN_THE_RAIN))
        assertTrue(p.earned(Achievement.MIDNIGHT_SUN))
        assertTrue(p.earned(Achievement.FIRST_LIGHT))
        assertFalse(eval(listOf(roll("a", shots = listOf(shot(shutter = "15s"))))).earned(Achievement.BULB_NIGHT))
        assertTrue(eval(listOf(roll("a", shots = listOf(shot(shutter = "B"))))).earned(Achievement.BULB_NIGHT))
    }

    @Test fun `dusted off and two bodies`() {
        val dusted = eval(listOf(roll("a", camera = "x", start = "2024-01-01"),
            roll("b", camera = "y", start = "2024-06-01"), roll("c", camera = "x", start = "2025-02-01")))
        assertEquals("c", dusted.getValue(Achievement.DUSTED_OFF).evidence?.rollId)

        val twoBodies = eval(listOf(
            roll("a", camera = "x", shots = listOf(shot("2026-01-10 12:00"))),
            roll("b", camera = "y", shots = listOf(shot("2026-01-10 12:45"))),
        ))
        assertTrue(twoBodies.earned(Achievement.TWO_BODIES))
        val apart = eval(listOf(
            roll("a", camera = "x", shots = listOf(shot("2026-01-10 12:00"))),
            roll("b", camera = "y", shots = listOf(shot("2026-01-10 13:30"))),
        ))
        assertFalse(apart.earned(Achievement.TWO_BODIES))
    }

    // ── Records ─────────────────────────────────────────────────────────────

    @Test fun `badges are dated by their evidence, and by today without any`() {
        val progress = Achievements.evaluate(AchSnapshot(
            rolls = listOf(roll("a", shots = listOf(shot("2023-04-05 10:00")))), films = listOf(hp5),
            counters = mapOf(AchCounters.BACKUP_EXPORTS to 1)))
        val today = LocalDate.of(2026, 11, 7)
        val out = AchievementRecords.reconcile(emptyMap(), progress, today)
        assertEquals("2023-04-05", out.records.getValue(Achievement.FIRST_FRAME).unlockedAt)
        assertEquals("2026-11-07", out.records.getValue(Achievement.BACKED_UP).unlockedAt)
        assertTrue(Achievement.FIRST_FRAME in out.newlyEarned)
        // Evidence dated after today (a clock set wrong) falls back to today.
        val future = Achievements.evaluate(AchSnapshot(
            rolls = listOf(roll("a", shots = listOf(shot("2030-01-01 10:00")))), films = listOf(hp5)))
        assertEquals("2026-11-07", AchievementRecords.reconcile(emptyMap(), future, today)
            .records.getValue(Achievement.FIRST_FRAME).unlockedAt)
    }

    @Test fun `standard development is not stand development`() {
        assertFalse(eval(listOf(roll("a", devLog = DevLog(process = "B&W (Standard)")))).earned(Achievement.STAND_STILL))
        assertTrue(eval(listOf(roll("a", devLog = DevLog(process = "B&W (Semi-Stand)")))).earned(Achievement.STAND_STILL))
        assertTrue(eval(listOf(roll("a", devLog = DevLog(process = "Rodinal stand 1:100")))).earned(Achievement.STAND_STILL))
    }

    @Test fun `records are sticky and only move up`() {
        val stored = mapOf(Achievement.FULL_ROLL to AchRecord(2, "2025-01-01"))
        val oneRoll = Achievements.evaluate(AchSnapshot(rolls = listOf(roll("a", finished = true))))
        val out = AchievementRecords.reconcile(stored, oneRoll, LocalDate.of(2026, 1, 1))
        assertEquals(2, out.records.getValue(Achievement.FULL_ROLL).tier)
        assertTrue(out.newlyEarned.isEmpty())
    }

    @Test fun `records survive a round trip and merge to the best of both`() {
        val a = mapOf(
            Achievement.FIRST_FRAME to AchRecord(1, "2024-01-01", "r1", 0),
            Achievement.FULL_ROLL to AchRecord(1, "2024-02-01"),
        )
        assertEquals(a, AchievementRecords.decode(AchievementRecords.encode(a)))
        val b = mapOf(
            Achievement.FIRST_FRAME to AchRecord(1, "2025-01-01"),
            Achievement.FULL_ROLL to AchRecord(2, "2025-03-01"),
            Achievement.MIXED_BAG to AchRecord(1, "2025-04-01"),
        )
        val m = AchievementRecords.merge(a, b)
        assertEquals("2024-01-01", m.getValue(Achievement.FIRST_FRAME).unlockedAt)
        assertEquals(2, m.getValue(Achievement.FULL_ROLL).tier)
        assertEquals(3, m.size)
    }

    @Test fun `unreadable records and counters decode as empty`() {
        assertTrue(AchievementRecords.decode("not json").isEmpty())
        assertTrue(AchievementRecords.decode(null).isEmpty())
        assertTrue(AchievementRecords.decode("""{"no_such_badge":{"tier":1,"at":"2025-01-01"}}""").isEmpty())
        assertTrue(AchCounters.decode("[1,2]").isEmpty())
        assertNull(AchievementRecords.decode("{}")[Achievement.FIRST_FRAME])
    }

    @Test fun `latent image needs a year between the last frame and development`() {
        val shots = listOf(shot("2024-03-01 10:00"))
        assertTrue(eval(listOf(roll("a", shots = shots, devLog = developedBw).copy(developedAt = "2025-03-02")))
            .earned(Achievement.LATENT_IMAGE))
        assertFalse(eval(listOf(roll("a", shots = shots, devLog = developedBw).copy(developedAt = "2025-02-01")))
            .earned(Achievement.LATENT_IMAGE))
        // A recorded finish date beats the last frame.
        assertFalse(eval(listOf(roll("a", shots = shots, devLog = developedBw)
            .copy(finishedAt = "2024-12-01", developedAt = "2025-03-02"))).earned(Achievement.LATENT_IMAGE))
        // Without a development date it cannot be told, so it is not earned.
        assertFalse(eval(listOf(roll("a", shots = shots, devLog = developedBw))).earned(Achievement.LATENT_IMAGE))
    }

    @Test fun `stage dates date the evidence when a roll has them`() {
        val r = roll("a", shots = listOf(shot("2026-01-10 12:00")), devLog = developedBw, scanned = true, selfDev = true)
            .copy(finishedAt = "2026-02-01", developedAt = "2026-03-01", scannedAt = "2026-04-01")
        val p = eval(listOf(r))
        assertEquals("2026-02-01", p.getValue(Achievement.FULL_ROLL).evidence?.date)
        assertEquals("2026-03-01", p.getValue(Achievement.HOME_BREW).evidence?.date)
        assertEquals("2026-04-01", p.getValue(Achievement.THROUGH_THE_PIPELINE).evidence?.date)
        val old = eval(listOf(roll("a", shots = listOf(shot("2026-01-10 12:00")), finished = true)))
        assertEquals("2026-01-10 12:00", old.getValue(Achievement.FULL_ROLL).evidence?.date)
    }

    @Test fun `counters increment and merge by the larger count`() {
        val once = AchCounters.increment(null, listOf(AchCounters.METER_LOGS, AchCounters.ZONE_LOGS))
        val twice = AchCounters.increment(once, listOf(AchCounters.METER_LOGS))
        assertEquals(mapOf(AchCounters.METER_LOGS to 2, AchCounters.ZONE_LOGS to 1), AchCounters.decode(twice))
        val merged = AchCounters.merge(mapOf("a" to 3, "b" to 1), mapOf("a" to 2, "c" to 5))
        assertEquals(mapOf("a" to 3, "b" to 1, "c" to 5), merged)
    }

    @Test fun `a backup counts its own export`() {
        assertEquals(mapOf(AchCounters.BACKUP_EXPORTS to 1),
            AchCounters.decodeObject(AchCounters.forBackup(null)))
        val stored = AchCounters.encode(mapOf(AchCounters.BACKUP_EXPORTS to 2, AchCounters.TIMER_RUNS to 3))
        assertEquals(mapOf(AchCounters.BACKUP_EXPORTS to 3, AchCounters.TIMER_RUNS to 3),
            AchCounters.decodeObject(AchCounters.forBackup(stored)))
        // Unreadable stored counters still give a backup that knows it was made.
        assertEquals(mapOf(AchCounters.BACKUP_EXPORTS to 1),
            AchCounters.decodeObject(AchCounters.forBackup("not json")))
    }

    @Test fun `a vault restored from the first backup has Backed Up`() {
        // The first export: nothing counted yet when the file is written.
        val carried = AchCounters.decodeObject(AchCounters.forBackup(null))
        // Restored into an empty vault: the counters are merged into none.
        val restored = AchCounters.merge(emptyMap(), carried)
        assertTrue(eval(counters = restored).earned(Achievement.BACKED_UP))
    }

    @Test fun `restoring a backup onto the vault that made it counts the export once`() {
        val before = AchCounters.encode(mapOf(AchCounters.BACKUP_EXPORTS to 4))
        val carried = AchCounters.decodeObject(AchCounters.forBackup(before))
        // After a successful export the app bumps its own setting too.
        val after = AchCounters.decode(AchCounters.increment(before, listOf(AchCounters.BACKUP_EXPORTS)))
        assertEquals(5, AchCounters.merge(after, carried)[AchCounters.BACKUP_EXPORTS])
    }

    @Test fun `every key is unique`() {
        assertEquals(Achievement.entries.size, Achievement.entries.map { it.key }.toSet().size)
    }
}

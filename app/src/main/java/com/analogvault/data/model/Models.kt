package com.analogvault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.analogvault.util.Constants

// ─── Type Converters ────────────────────────────────────────────────────────

class StringListConverter {
    private val gson = Gson()
    @TypeConverter fun fromList(list: List<String>?): String = gson.toJson(list ?: emptyList<String>())
    @TypeConverter fun toList(json: String?): List<String> = if (json.isNullOrBlank()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
}

class ShotListConverter {
    private val gson = Gson()
    @TypeConverter fun fromList(list: List<Shot>?): String = gson.toJson(list ?: emptyList<Shot>())
    @TypeConverter fun toList(json: String?): List<Shot> = if (json.isNullOrBlank()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<Shot>>() {}.type)
}

class DevLogConverter {
    private val gson = Gson()
    @TypeConverter fun from(v: DevLog?): String? = v?.let { gson.toJson(it) }
    @TypeConverter fun to(json: String?): DevLog? = json?.let { gson.fromJson(it, DevLog::class.java) }
}

class KitStepListConverter {
    private val gson = Gson()
    @TypeConverter fun fromList(list: List<KitStep>?): String = gson.toJson(list ?: emptyList<KitStep>())
    @TypeConverter fun toList(json: String?): List<KitStep> = if (json.isNullOrBlank()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<KitStep>>() {}.type)
}

class ScanLogConverter {
    private val gson = Gson()
    @TypeConverter fun from(v: ScanLog?): String? = v?.let { gson.toJson(it) }
    @TypeConverter fun to(json: String?): ScanLog? = json?.let { gson.fromJson(it, ScanLog::class.java) }
}

// ─── Film Stock ──────────────────────────────────────────────────────────────

@Entity(tableName = "films")
@TypeConverters(StringListConverter::class)
data class FilmStock(
    @PrimaryKey val id: String,
    val name: String = "",
    val brand: String = "",
    val type: String = "Color Negative (C-41)",
    val iso: Int = 400,
    val shots: Int = 36,          // legacy — kept so existing data survives; use frameCount
    val filmFormat: String = "135 (35mm)",   // value from FILM_FORMATS_DISPLAY
    val frameCount: Int = 36,     // actual frame count for this roll
    val expiryDate: String = "",
    val purchaseDate: String = "",  // when acquired; "" = unknown
    val storage: String = "Shelf",
    val quantity: Int = 1,
    val notes: String = "",
    val costPerRoll: Double = 0.0,  // purchase price per roll; 0 = gifted / unknown
    /**
     * Override for the stock's accent colour, as "#RRGGBB" or "#RRGGBB,#RRGGBB"
     * for a gradient. Blank means "derive it" — see StockAccent, which knows the
     * box art of the common stocks and falls back to the process otherwise.
     */
    val stockAccent: String = ""
)

/**
 * Whether this stock records the scene in grey. Infrared counts: the IR stocks
 * people shoot (Rollei IR, SFX) are black and white. The type is free text in
 * older data, so match loosely rather than on the exact FILM_TYPES label.
 */
fun FilmStock.isMonochrome(): Boolean =
    type.contains("Black", ignoreCase = true) ||
        type.contains("B&W", ignoreCase = true) ||
        type.contains("Infrared", ignoreCase = true)

// ─── Camera ──────────────────────────────────────────────────────────────────

@Entity(tableName = "cameras")
@TypeConverters(StringListConverter::class)
data class Camera(
    @PrimaryKey val id: String,
    val name: String = "",
    val brand: String = "",
    val format: String = "35mm",
    val mfFormat: String = "",          // MF shooting format: "6x4.5"|"6x6"|"6x7"|"6x9"|"6x17"
    val lensSystem: String = "fixed",   // "fixed" | "interchangeable"
    val condition: String = "Good",
    val mount: String = "",
    val adapterMounts: List<String> = emptyList(),
    val notes: String = "",
    /**
     * The shutter range this body can actually be set to, as SHUTTER_SPEEDS
     * strings ("1/500", "1s"). Blank means unknown, and unknown means unclamped —
     * the meter suggests whatever the light asks for, which is what it did before
     * these columns existed. A Zenit E carries "1/500" and "1/30"; a Nikon F5
     * carries "1/8000" and "30s".
     */
    val fastestShutter: String = "",
    val slowestShutter: String = "",
    /** Bulb lets the body go past [slowestShutter] on a cable release. */
    val hasBulb: Boolean = false,
    /**
     * The aperture range of a fixed-lens body's built-in lens, stated like
     * [Lens.maxAperture] and [Lens.minAperture]. Ignored on interchangeable
     * bodies, where the mounted lens carries it. Blank means unknown, and
     * unknown means unclamped — the same rule as the shutter range.
     */
    val maxAperture: String = "",
    val minAperture: String = ""
)

/**
 * The built-in lens of a fixed-lens body, shaped as a [Lens] so the meter and
 * the shot sheet can treat it like a mounted one. Null on interchangeable
 * bodies, and when neither end of the aperture range is known.
 */
fun Camera.fixedLens(): Lens? =
    if (lensSystem != "fixed" || (maxAperture.isBlank() && minAperture.isBlank())) null
    else Lens(id = "", name = "${name.ifBlank { "Built-in" }} lens", brand = brand,
        focalLength = "", maxAperture = maxAperture, minAperture = minAperture)

/**
 * Frames a roll of [film] gives in this body. A 120 roll's count is set by the
 * camera's frame size, not the film — the same roll is 8 frames in a 6x9 and 16
 * in a 645 — so a medium-format body with a shooting format overrides the
 * stock's count. 220 is twice the length of 120, so twice the frames.
 * Everything else keeps the stock's own count.
 */
fun Camera?.framesFor(film: FilmStock?): Int {
    val stock = film?.frameCount?.takeIf { it > 0 } ?: 36
    val perRoll = this?.let { Constants.MF_FRAME_COUNTS[it.mfFormat] } ?: return stock
    return when (film?.filmFormat) {
        "120" -> perRoll
        "220" -> perRoll * 2
        else  -> stock
    }
}

// ─── Lens ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "lenses")
data class Lens(
    @PrimaryKey val id: String,
    val name: String = "",
    val brand: String = "",
    val focalLength: String = "50",
    /** Widest opening — the number on the barrel, so the *smallest* f-number. */
    val maxAperture: String = "1.8",
    /**
     * Narrowest opening, the far end of the aperture ring ("16", "22"). Blank
     * means unknown, and unknown means that end is unclamped. Most lenses stop
     * down to f/16 or f/22, but guessing would put a hazard stripe on a rung the
     * lens can reach, which is worse than not clamping at all.
     */
    val minAperture: String = "",
    val mount: String = "",
    val condition: String = "Good"
)

// ─── Accessory ───────────────────────────────────────────────────────────────

@Entity(tableName = "accessories")
data class Accessory(
    @PrimaryKey val id: String,
    val name: String = "",
    val type: String = "Filter",
    val brand: String = "",
    val condition: String = "Good",
    val notes: String = ""
)

// ─── Shot (nested in Roll) ────────────────────────────────────────────────────

data class Shot(
    val id: String = "",
    val shutter: String = "",
    val aperture: String = "",
    val iso: String = "",
    val lens: String = "",
    val location: String = "",
    val notes: String = "",
    val weather: String = "",
    val date: String = "",
    val photoThumbPath: String = ""  // local file path instead of data URL
)

// ─── Dev & Scan Logs ─────────────────────────────────────────────────────────

data class DevLog(
    val process: String = "",
    val developer: String = "",
    val dilution: String = "",
    val temp: String = "",
    val devTime: String = "",
    val notes: String = ""
)

data class ScanLog(
    val method: String = "",
    val dpi: String = "",
    val software: String = "",
    val notes: String = ""
)

// ─── Roll ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "rolls")
@TypeConverters(ShotListConverter::class, DevLogConverter::class, ScanLogConverter::class)
data class Roll(
    @PrimaryKey val id: String,
    val filmId: String = "",
    val cameraId: String = "",
    /**
     * The lens mounted for this roll. Blank on fixed-lens bodies, and blank on
     * rolls loaded before the field was asked for — the meter treats both as
     * "no lens known" and clamps on the body alone.
     */
    val cameraLensId: String = "",
    val startDate: String = "",
    val finished: Boolean = false,
    val developed: Boolean = false,
    val scanned: Boolean = false,
    val shots: List<Shot> = emptyList(),
    val devLog: DevLog? = null,
    val scanLog: ScanLog? = null,
    /** ISO override — if blank, use film box speed */
    val pushIso: String = "",
    /** Total exposures on this roll */
    val totalShots: Int = 36,
    // ── Cost tracking ───────────────────────────────────────────────────────
    val devCost: Double = 0.0,     // lab dev cost OR self-dev chemical cost
    val scanCost: Double = 0.0,    // scanning cost (lab or home scanner)
    val isSelfDev: Boolean = false // true = self-developed (cost is chemicals, not lab)
)

// ─── Chemical ─────────────────────────────────────────────────────────────────

@Entity(tableName = "chemicals")
data class Chemical(
    @PrimaryKey val id: String,
    val name: String = "",
    val type: String = "Developer",
    val dilution: String = "",
    val volume: String = "",
    val volumeUnit: String = "ml",
    val mixDate: String = "",
    val maxRolls: String = "",
    val baseDevTime: String = "",
    val timeAdjPerRoll: String = "",
    val manualRolls: Int = -1,   // -1 = auto-count from rolls
    val notes: String = "",
    /**
     * The baths of a kit, in the order they run. Empty for a single chemical.
     * A kit is bought, mixed and worn out as one thing, so it shares the one
     * mix date, capacity and roll count above — but every bath keeps its own
     * extension per roll, because a developer slows as it ages while a blix
     * usually does not.
     */
    val kitSteps: List<KitStep> = emptyList(),
    /** "C-41", "E-6" or "B&W" for a kit; decides which temperature rules apply. */
    val process: String = "",
    /**
     * The one temperature a colour kit runs at, in °C, or blank for the
     * process's standard. Kits differ — Cinestill's Cs41 is 39 °C, Kodak's
     * Flexicolor 37.8 °C — and their times are only true at their own.
     */
    val kitTempC: String = ""
) {
    val isKit: Boolean get() = type == KIT_TYPE
}

const val KIT_TYPE = "Kit"

/**
 * One bath in a chemistry kit. Times are minutes, written either as "3:30" or
 * as "3.5" — see parseMinutes. [role] is a StepRole name, kept as text so the
 * data layer does not depend on the timer's UI types.
 */
data class KitStep(
    val name: String = "",
    val role: String = "DEVELOP",
    val baseTime: String = "",
    val adjPerRoll: String = "",
)

// ─── Bulk Roll (canister inventory) ──────────────────────────────────────────

@Entity(tableName = "bulk_rolls")
data class BulkRoll(
    @PrimaryKey val id: String,
    val name: String = "",           // film name, e.g. "Ilford HP5+"
    val brand: String = "",
    val type: String = "B&W",
    val iso: Int = 400,
    val totalFrames: Int = 0,        // total frames available in this canister
    val usedFrames: Int = 0,         // frames already loaded into rolls
    val notes: String = "",
    val purchaseDate: String = "",
    val expiryDate: String = "",
    val totalCost: Double = 0.0      // total price of the bulk canister; 0 = gifted / unknown
)

// ─── Zoom Level (for light meter) ─────────────────────────────────────────────

@Entity(tableName = "zoom_levels")
data class ZoomLevel(
    @PrimaryKey val id: String,
    val label: String,
    val mm: Int
)

// ─── Settings ─────────────────────────────────────────────────────────────────

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)

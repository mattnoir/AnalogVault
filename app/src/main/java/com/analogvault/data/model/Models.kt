package com.analogvault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    val hasBulb: Boolean = false
)

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
    val notes: String = ""
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

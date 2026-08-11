package com.analogvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.analogvault.data.model.*

/** Add pushIso/totalShots to rolls, adapterMounts to cameras (v1 → v2) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rolls ADD COLUMN pushIso TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE rolls ADD COLUMN totalShots INTEGER NOT NULL DEFAULT 36")
        // adapterMounts was added to the Camera entity at the same time but was never migrated,
        // causing INSERT crashes on existing installs.  Empty JSON array "[]" is the correct
        // default — matches what StringListConverter.fromList(emptyList()) produces.
        db.execSQL("ALTER TABLE cameras ADD COLUMN adapterMounts TEXT NOT NULL DEFAULT '[]'")
    }
}

/** Add bulk_rolls table (v2 → v3) */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bulk_rolls` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL DEFAULT '',
                `brand` TEXT NOT NULL DEFAULT '',
                `type` TEXT NOT NULL DEFAULT 'B&W',
                `iso` INTEGER NOT NULL DEFAULT 400,
                `totalFrames` INTEGER NOT NULL DEFAULT 0,
                `usedFrames` INTEGER NOT NULL DEFAULT 0,
                `notes` TEXT NOT NULL DEFAULT '',
                `purchaseDate` TEXT NOT NULL DEFAULT '',
                `expiryDate` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

/**
 * Retroactive fixes for installs that went v1→v2→v3 WITHOUT the adapterMounts column fix,
 * and to add expiryDate to bulk_rolls if it was created without it.
 * Uses pragma-based existence checks instead of try/catch to be safe on all SQLite versions.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Check if adapterMounts already exists before adding it
        val camerasCursor = db.query("PRAGMA table_info(cameras)")
        val hasAdapterMounts = generateSequence { if (camerasCursor.moveToNext()) camerasCursor else null }
            .any { it.getString(it.getColumnIndexOrThrow("name")) == "adapterMounts" }
        camerasCursor.close()
        if (!hasAdapterMounts) {
            db.execSQL("ALTER TABLE cameras ADD COLUMN adapterMounts TEXT NOT NULL DEFAULT '[]'")
        }

        // Check if expiryDate already exists in bulk_rolls
        val bulkCursor = db.query("PRAGMA table_info(bulk_rolls)")
        val hasExpiry = generateSequence { if (bulkCursor.moveToNext()) bulkCursor else null }
            .any { it.getString(it.getColumnIndexOrThrow("name")) == "expiryDate" }
        bulkCursor.close()
        if (!hasExpiry) {
            db.execSQL("ALTER TABLE bulk_rolls ADD COLUMN expiryDate TEXT NOT NULL DEFAULT ''")
        }
    }
}

/** Add mfFormat to cameras; filmFormat and frameCount to films (v4 → v5) */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val camCursor = db.query("PRAGMA table_info(cameras)")
        val hasMfFormat = generateSequence { if (camCursor.moveToNext()) camCursor else null }
            .any { it.getString(it.getColumnIndexOrThrow("name")) == "mfFormat" }
        camCursor.close()
        if (!hasMfFormat) {
            db.execSQL("ALTER TABLE cameras ADD COLUMN mfFormat TEXT NOT NULL DEFAULT ''")
        }

        val filmCursor = db.query("PRAGMA table_info(films)")
        val cols = generateSequence { if (filmCursor.moveToNext()) filmCursor else null }
            .map { it.getString(it.getColumnIndexOrThrow("name")) }.toSet()
        filmCursor.close()
        if ("filmFormat" !in cols)
            db.execSQL("ALTER TABLE films ADD COLUMN filmFormat TEXT NOT NULL DEFAULT '135 (35mm)'")
        if ("frameCount" !in cols)
            db.execSQL("ALTER TABLE films ADD COLUMN frameCount INTEGER NOT NULL DEFAULT 36")
    }
}

/** Add cost tracking fields: costPerRoll to films, totalCost to bulk_rolls, devCost/scanCost/isSelfDev to rolls (v5 → v6) */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE films ADD COLUMN costPerRoll REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE rolls ADD COLUMN devCost REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE rolls ADD COLUMN scanCost REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE rolls ADD COLUMN isSelfDev INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bulk_rolls ADD COLUMN totalCost REAL NOT NULL DEFAULT 0.0")
    }
}

/** Add purchaseDate to films (v6 → v7) */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE films ADD COLUMN purchaseDate TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Add stockAccent to films (v7 → v8).
 *
 * Blank is the meaningful default, not a placeholder: it means "derive the
 * colour from the stock name and process", so existing rows keep working and
 * only stocks the user deliberately recolours carry a stored value.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE films ADD COLUMN stockAccent TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        FilmStock::class, Camera::class, Lens::class,
        Accessory::class, Roll::class, Chemical::class,
        ZoomLevel::class, Setting::class, BulkRoll::class
    ],
    version = 8,
    // Schemas are exported to app/schemas (see build.gradle.kts) so future
    // migrations can be written/tested against exact historical definitions
    exportSchema = true
)
@TypeConverters(
    StringListConverter::class,
    ShotListConverter::class,
    DevLogConverter::class,
    ScanLogConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filmDao(): FilmDao
    abstract fun cameraDao(): CameraDao
    abstract fun lensDao(): LensDao
    abstract fun accessoryDao(): AccessoryDao
    abstract fun rollDao(): RollDao
    abstract fun chemicalDao(): ChemicalDao
    abstract fun zoomLevelDao(): ZoomLevelDao
    abstract fun settingDao(): SettingDao
    abstract fun bulkRollDao(): BulkRollDao
}

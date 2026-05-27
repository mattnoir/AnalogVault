package com.analogvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.analogvault.data.model.*

/** Add pushIso/totalShots to rolls, adapterMounts to cameras (v1 → v2) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE rolls ADD COLUMN pushIso TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE rolls ADD COLUMN totalShots INTEGER NOT NULL DEFAULT 36")
        // adapterMounts was added to the Camera entity at the same time but was never migrated,
        // causing INSERT crashes on existing installs.  Empty JSON array "[]" is the correct
        // default — matches what StringListConverter.fromList(emptyList()) produces.
        database.execSQL("ALTER TABLE cameras ADD COLUMN adapterMounts TEXT NOT NULL DEFAULT '[]'")
    }
}

/** Add bulk_rolls table (v2 → v3) */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
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
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try { database.execSQL("ALTER TABLE cameras ADD COLUMN adapterMounts TEXT NOT NULL DEFAULT '[]'") }
        catch (_: Exception) { /* already present */ }
        try { database.execSQL("ALTER TABLE bulk_rolls ADD COLUMN expiryDate TEXT NOT NULL DEFAULT ''") }
        catch (_: Exception) { /* already present */ }
    }
}

/** Add mfFormat to cameras; filmFormat and frameCount to films (v4 → v5) */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE cameras ADD COLUMN mfFormat TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE films ADD COLUMN filmFormat TEXT NOT NULL DEFAULT '135 (35mm)'")
        database.execSQL("ALTER TABLE films ADD COLUMN frameCount INTEGER NOT NULL DEFAULT 36")
    }
}

@Database(
    entities = [
        FilmStock::class, Camera::class, Lens::class,
        Accessory::class, Roll::class, Chemical::class,
        ZoomLevel::class, Setting::class, BulkRoll::class
    ],
    version = 5,
    exportSchema = false
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

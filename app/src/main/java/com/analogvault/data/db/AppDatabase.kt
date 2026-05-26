package com.analogvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.analogvault.data.model.*

/** Add pushIso and totalShots columns to rolls table (v1 → v2) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE rolls ADD COLUMN pushIso TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE rolls ADD COLUMN totalShots INTEGER NOT NULL DEFAULT 36")
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
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

@Database(
    entities = [
        FilmStock::class, Camera::class, Lens::class,
        Accessory::class, Roll::class, Chemical::class,
        ZoomLevel::class, Setting::class, BulkRoll::class
    ],
    version = 3,
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

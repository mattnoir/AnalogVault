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

@Database(
    entities = [
        FilmStock::class, Camera::class, Lens::class,
        Accessory::class, Roll::class, Chemical::class,
        ZoomLevel::class, Setting::class
    ],
    version = 2,
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
}

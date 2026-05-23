package com.analogvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.analogvault.data.model.*

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

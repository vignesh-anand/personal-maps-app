package com.scoot.transit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        CalendarEntity::class,
        CalendarDateEntity::class,
        LegCacheEntity::class,
        UserPrefEntity::class,
        FavoriteEntity::class,
        PresetEntity::class,
        AlertHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ScootDatabase : RoomDatabase() {
    abstract fun stops(): StopDao
    abstract fun routes(): RouteDao
    abstract fun trips(): TripDao
    abstract fun stopTimes(): StopTimeDao
    abstract fun calendar(): CalendarDao
    abstract fun legCache(): LegCacheDao
    abstract fun userPrefs(): UserPrefDao
    abstract fun favorites(): FavoritesDao
    abstract fun presets(): PresetDao
    abstract fun alertHistory(): AlertHistoryDao

    companion object {
        const val NAME = "scoot.db"
    }
}

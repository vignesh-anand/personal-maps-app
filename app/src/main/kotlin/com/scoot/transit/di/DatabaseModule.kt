package com.scoot.transit.di

import android.content.Context
import androidx.room.Room
import com.scoot.transit.data.db.AlertHistoryDao
import com.scoot.transit.data.db.CalendarDao
import com.scoot.transit.data.db.FavoritesDao
import com.scoot.transit.data.db.LegCacheDao
import com.scoot.transit.data.db.PresetDao
import com.scoot.transit.data.db.RouteDao
import com.scoot.transit.data.db.ScootDatabase
import com.scoot.transit.data.db.StopDao
import com.scoot.transit.data.db.StopTimeDao
import com.scoot.transit.data.db.TripDao
import com.scoot.transit.data.db.UserPrefDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): ScootDatabase =
        Room.databaseBuilder(ctx, ScootDatabase::class.java, ScootDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun stops(db: ScootDatabase): StopDao = db.stops()
    @Provides fun routes(db: ScootDatabase): RouteDao = db.routes()
    @Provides fun trips(db: ScootDatabase): TripDao = db.trips()
    @Provides fun stopTimes(db: ScootDatabase): StopTimeDao = db.stopTimes()
    @Provides fun calendar(db: ScootDatabase): CalendarDao = db.calendar()
    @Provides fun legCache(db: ScootDatabase): LegCacheDao = db.legCache()
    @Provides fun userPrefs(db: ScootDatabase): UserPrefDao = db.userPrefs()
    @Provides fun favorites(db: ScootDatabase): FavoritesDao = db.favorites()
    @Provides fun presets(db: ScootDatabase): PresetDao = db.presets()
    @Provides fun alertHistory(db: ScootDatabase): AlertHistoryDao = db.alertHistory()
}

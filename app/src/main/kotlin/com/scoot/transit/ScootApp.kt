package com.scoot.transit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.libraries.places.api.Places
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.domain.Agency
import com.scoot.transit.work.ScootWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class ScootApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workScheduler: ScootWorkScheduler
    @Inject lateinit var gtfsRepo: GtfsStaticRepo

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        if (BuildConfig.API_GOOGLE_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.API_GOOGLE_KEY)
        }

        createNotificationChannels()
        workScheduler.scheduleAll()

        appScope.launch {
            for (agency in listOf(Agency.CALTRAIN, Agency.BART)) {
                if (!gtfsRepo.isLoaded(agency)) {
                    Timber.i("No GTFS data for %s on first launch - kicking off one-time fetch", agency)
                    workScheduler.refreshGtfsNow(agency)
                }
            }
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Service alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Caltrain / BART / bus disruptions on lines you follow" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LAST_TRAIN,
                "Last-train warning",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts before the last train of the night" }
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_LAST_TRAIN = "last_train"
    }
}

package com.scoot.transit.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.scoot.transit.domain.Agency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules background refreshes:
 *  - GTFS static for Caltrain + BART weekly
 *  - GTFS-RT alert poller every ~10 minutes
 *  - Last-train-of-night daily check
 */
@Singleton
class ScootWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleAll() {
        val wm = WorkManager.getInstance(context)
        Agency.entries.filter { it == Agency.CALTRAIN || it == Agency.BART }.forEach { agency ->
            val req = PeriodicWorkRequestBuilder<GtfsRefreshWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setInputData(GtfsRefreshWorker.input(agency))
                .build()
            wm.enqueueUniquePeriodicWork(
                "gtfs_refresh_${agency.operatorId}",
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
        val alertReq = PeriodicWorkRequestBuilder<AlertPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(
            "alert_poll",
            ExistingPeriodicWorkPolicy.UPDATE,
            alertReq,
        )

        val lastTrainReq = PeriodicWorkRequestBuilder<LastTrainWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(
            "last_train_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            lastTrainReq,
        )
    }

    /**
     * Enqueues a one-time GTFS download for the given agency. Allows any network type so it can
     * run on cellular when the user explicitly asks for fresh data (e.g. first launch / Settings
     * pull-to-refresh button).
     */
    fun refreshGtfsNow(agency: Agency) {
        val req = OneTimeWorkRequestBuilder<GtfsRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(GtfsRefreshWorker.input(agency))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "gtfs_refresh_now_${agency.operatorId}",
            ExistingWorkPolicy.KEEP,
            req,
        )
    }
}

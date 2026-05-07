package com.scoot.transit.work

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scoot.transit.MainActivity
import com.scoot.transit.R
import com.scoot.transit.ScootApp
import com.scoot.transit.data.DepartureRepo
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.data.LocationRepo
import com.scoot.transit.data.RoutingRepo
import com.scoot.transit.data.UserPrefsRepo
import com.scoot.transit.data.db.FavoritesDao
import com.scoot.transit.domain.Agency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import timber.log.Timber

/**
 * Once-an-hour check: is the user's nearest favorite Caltrain station's last train of the night
 * within their reachable scoot window? If yes, fire a notification with leave-by time.
 */
@HiltWorker
class LastTrainWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val location: LocationRepo,
    private val statics: GtfsStaticRepo,
    private val departures: DepartureRepo,
    private val routing: RoutingRepo,
    private val prefs: UserPrefsRepo,
    private val favorites: FavoritesDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!prefs.getBool(UserPrefsRepo.KEY_NOTIF_LAST_TRAIN, default = true)) return Result.success()
        val now = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"))
        if (now.hour !in 21..23) return Result.success()

        val origin = location.current() ?: return Result.success()
        val favStops = favorites.forAgency(Agency.CALTRAIN.operatorId)
        val candidates = favStops.mapNotNull { statics.stationById(Agency.CALTRAIN, it.stop_id) }
            .ifEmpty { statics.nearestStation(Agency.CALTRAIN, origin)?.let { listOf(it) }.orEmpty() }

        for (station in candidates) {
            val deps = departures.upcomingDepartures(Agency.CALTRAIN, station.stopId, now, limit = 50)
            val today = now.toLocalDate()
            val lastToday = deps.lastOrNull { dep ->
                val instant = dep.realtime ?: dep.scheduled
                ZonedDateTime.ofInstant(instant, now.zone).toLocalDate() == today
            } ?: continue

            val depInstant = lastToday.realtime ?: lastToday.scheduled
            val scootLeg = routing.scootLeg(origin, station.location) ?: continue
            val leaveBy = depInstant.minusSeconds(scootLeg.durationSeconds.toLong())
            val minutesUntilLeave = Duration.between(Instant.now(), leaveBy).toMinutes()
            if (minutesUntilLeave in 0..45) {
                fire(station.name, lastToday.direction.name.lowercase(), minutesUntilLeave)
                return Result.success()
            }
        }
        return Result.success()
    }

    private fun fire(stationName: String, direction: String, minutesUntilLeave: Long) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val pi = android.app.PendingIntent.getActivity(
            applicationContext,
            5511,
            Intent(applicationContext, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, ScootApp.CHANNEL_LAST_TRAIN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Last $direction train soon")
            .setContentText("Leave for $stationName in $minutesUntilLeave min")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify(5511, n)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission missing")
        }
    }
}

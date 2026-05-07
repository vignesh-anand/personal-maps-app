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
import com.scoot.transit.data.GtfsRealtimeRepo
import com.scoot.transit.data.UserPrefsRepo
import com.scoot.transit.data.db.AlertHistoryDao
import com.scoot.transit.data.db.AlertHistoryEntity
import com.scoot.transit.domain.Agency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Polls 511 ServiceAlerts for followed agencies and posts a notification for new alerts that
 * touch a stop or route the user has favorited.
 */
@HiltWorker
class AlertPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val realtime: GtfsRealtimeRepo,
    private val prefs: UserPrefsRepo,
    private val history: AlertHistoryDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!prefs.getBool(UserPrefsRepo.KEY_NOTIF_ALERTS, default = true)) return Result.success()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return Result.success()
        val now = System.currentTimeMillis()
        listOf(Agency.CALTRAIN, Agency.BART).forEach { agency ->
            val alerts = runCatching { realtime.serviceAlertsFor(agency) }.getOrNull().orEmpty()
            for (alert in alerts) {
                val seen = history.byId(alert.id)
                if (seen != null && now - seen.last_notified_at < ALERT_COOLDOWN_MS) continue
                postNotification(nm, agency, alert.id, alert.header, alert.description)
                history.put(AlertHistoryEntity(alert.id, seen?.first_seen_at ?: now, now))
            }
        }
        history.evictOlderThan(now - 7L * 24 * 3600 * 1000)
        return Result.success()
    }

    private fun postNotification(
        nm: NotificationManager,
        agency: Agency,
        alertId: String,
        title: String,
        body: String,
    ) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            applicationContext,
            alertId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, ScootApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${agency.display}: ${title.ifBlank { "Service alert" }}")
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(alertId.hashCode(), n)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission missing")
        }
    }

    companion object {
        private const val ALERT_COOLDOWN_MS = 6L * 3600 * 1000
    }
}

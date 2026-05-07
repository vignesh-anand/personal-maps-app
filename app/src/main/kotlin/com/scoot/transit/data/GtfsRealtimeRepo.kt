package com.scoot.transit.data

import com.google.transit.realtime.GtfsRealtime
import com.scoot.transit.BuildConfig
import com.scoot.transit.data.remote.TransitFeedApi
import com.scoot.transit.domain.Agency
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Caches GTFS-RT TripUpdates and ServiceAlerts per agency for [CACHE_MS] to limit 511 calls.
 * Provides indexed lookups by trip id + stop id for fast merging into scheduled departures.
 */
@Singleton
class GtfsRealtimeRepo @Inject constructor(
    private val api: TransitFeedApi,
) {
    private val tripUpdateCache = mutableMapOf<Agency, CacheEntry<Map<TripStopKey, StopUpdate>>>()
    private val alertsCache = mutableMapOf<Agency, CacheEntry<List<ServiceAlert>>>()
    private val mutex = Mutex()

    suspend fun tripUpdatesFor(agency: Agency): Map<TripStopKey, StopUpdate> {
        if (BuildConfig.API_511_KEY.isBlank()) return emptyMap()
        val now = System.currentTimeMillis()
        mutex.withLock {
            val cached = tripUpdateCache[agency]
            if (cached != null && now - cached.fetchedAt < CACHE_MS) return cached.value
        }
        return runCatching {
            val body = api.tripUpdates(BuildConfig.API_511_KEY, agency.operatorId)
            val feed = body.byteStream().use { GtfsRealtime.FeedMessage.parseFrom(it) }
            val map = HashMap<TripStopKey, StopUpdate>()
            for (entity in feed.entityList) {
                if (!entity.hasTripUpdate()) continue
                val tu = entity.tripUpdate
                val tripId = tu.trip.tripId ?: continue
                val cancelled = tu.trip.scheduleRelationship == GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED
                for (stu in tu.stopTimeUpdateList) {
                    val stopId = stu.stopId ?: continue
                    val arrival = if (stu.hasArrival() && stu.arrival.hasTime()) Instant.ofEpochSecond(stu.arrival.time) else null
                    val departure = if (stu.hasDeparture() && stu.departure.hasTime()) Instant.ofEpochSecond(stu.departure.time) else null
                    val delay = if (stu.hasDeparture() && stu.departure.hasDelay()) stu.departure.delay else null
                    val skipped = stu.scheduleRelationship == GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED
                    map[TripStopKey(tripId, stopId)] = StopUpdate(
                        tripId = tripId,
                        stopId = stopId,
                        arrival = arrival,
                        departure = departure,
                        delaySeconds = delay,
                        cancelled = cancelled || skipped,
                    )
                }
            }
            mutex.withLock {
                tripUpdateCache[agency] = CacheEntry(map, now)
            }
            map
        }.onFailure { Timber.w(it, "tripUpdates fetch failed for ${agency.display}") }
            .getOrDefault(emptyMap())
    }

    suspend fun serviceAlertsFor(agency: Agency): List<ServiceAlert> {
        if (BuildConfig.API_511_KEY.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        mutex.withLock {
            val cached = alertsCache[agency]
            if (cached != null && now - cached.fetchedAt < CACHE_MS) return cached.value
        }
        return runCatching {
            val body = api.serviceAlerts(BuildConfig.API_511_KEY, agency.operatorId)
            val feed = body.byteStream().use { GtfsRealtime.FeedMessage.parseFrom(it) }
            val alerts = feed.entityList.mapNotNull { entity ->
                if (!entity.hasAlert()) return@mapNotNull null
                val alert = entity.alert
                val header = alert.headerText.translationList.firstOrNull()?.text.orEmpty()
                val description = alert.descriptionText.translationList.firstOrNull()?.text.orEmpty()
                val routes = alert.informedEntityList.mapNotNull { it.routeId.takeIf { rid -> rid.isNotBlank() } }
                val stops = alert.informedEntityList.mapNotNull { it.stopId.takeIf { sid -> sid.isNotBlank() } }
                ServiceAlert(
                    id = entity.id ?: "",
                    agency = agency,
                    header = header,
                    description = description,
                    routeIds = routes,
                    stopIds = stops,
                )
            }
            mutex.withLock { alertsCache[agency] = CacheEntry(alerts, now) }
            alerts
        }.onFailure { Timber.w(it, "serviceAlerts fetch failed for ${agency.display}") }
            .getOrDefault(emptyList())
    }

    private data class CacheEntry<T>(val value: T, val fetchedAt: Long)

    companion object {
        private const val CACHE_MS = 25_000L
    }
}

data class TripStopKey(val tripId: String, val stopId: String)

data class StopUpdate(
    val tripId: String,
    val stopId: String,
    val arrival: Instant?,
    val departure: Instant?,
    val delaySeconds: Int?,
    val cancelled: Boolean,
)

data class ServiceAlert(
    val id: String,
    val agency: Agency,
    val header: String,
    val description: String,
    val routeIds: List<String>,
    val stopIds: List<String>,
)

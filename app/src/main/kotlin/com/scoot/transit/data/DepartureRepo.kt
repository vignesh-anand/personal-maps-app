package com.scoot.transit.data

import com.scoot.transit.data.db.CalendarDao
import com.scoot.transit.data.db.DepartureRow
import com.scoot.transit.data.db.PairTripRow
import com.scoot.transit.data.db.StopTimeDao
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.DataSource
import com.scoot.transit.domain.Departure
import com.scoot.transit.domain.Direction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Joins scheduled stop_times (filtered by active service ids) with GTFS-RT trip updates to produce
 * domain [Departure]s for any stop.
 */
@Singleton
class DepartureRepo @Inject constructor(
    private val stopTimes: StopTimeDao,
    private val calendar: CalendarDao,
    private val realtime: GtfsRealtimeRepo,
    private val statics: GtfsStaticRepo,
) {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    suspend fun upcomingDepartures(
        agency: Agency,
        stopId: String,
        from: ZonedDateTime = ZonedDateTime.now(zone),
        limit: Int = 20,
    ): List<Departure> {
        val family = statics.stopFamily(agency, stopId)
        val nowInstant = from.toInstant()
        // Yesterday's service may still have post-midnight trips that depart "today" - check that first.
        val prevDay = from.toLocalDate().minusDays(1)
        val shifted = family.flatMap {
            upcomingForDate(
                agency = agency,
                stopId = it,
                serviceDate = prevDay,
                afterInstant = nowInstant,
                limit = limit,
                isPrevDay = true,
            )
        }
        val sameDay = family.flatMap {
            upcomingForDate(
                agency = agency,
                stopId = it,
                serviceDate = from.toLocalDate(),
                afterInstant = nowInstant,
                limit = limit * 2,
                isPrevDay = false,
            )
        }
        return (shifted + sameDay)
            .distinctBy { it.tripId to it.stopId }
            .filter { it.scheduled.isAfter(nowInstant.minusSeconds(30)) }
            .sortedBy { it.scheduled }
            .take(limit)
    }

    suspend fun pairTrips(
        agency: Agency,
        fromStopId: String,
        toStopId: String,
        from: ZonedDateTime = ZonedDateTime.now(zone),
        limit: Int = 20,
    ): List<PairResult> {
        val date = from.toLocalDate()
        val midnight = date.atStartOfDay(zone).toInstant()
        val secs = from.toLocalTime().toSecondOfDay()
        val active = calendar.activeServiceIds(agency.operatorId, dateFmt.format(date), date.dayOfWeek.value).toHashSet()
        val updates = realtime.tripUpdatesFor(agency)
        // Expand parent station ids to all platform stop ids and run the pair query for every
        // valid (from-platform, to-platform) combination. Most combos return zero rows; the
        // ones that share a direction yield the trips we want.
        val fromPlatforms = statics.stopFamily(agency, fromStopId)
        val toPlatforms = statics.stopFamily(agency, toStopId)
        val rows = mutableListOf<Pair<com.scoot.transit.data.db.PairTripRow, Pair<String, String>>>()
        for (f in fromPlatforms) {
            for (t in toPlatforms) {
                if (f == t) continue
                stopTimes.pairTrips(agency.operatorId, f, t, secs, limit * 4)
                    .forEach { rows += it to (f to t) }
            }
        }
        return rows.asSequence()
            .filter { (row, _) -> row.service_id in active }
            .distinctBy { (row, _) -> row.trip_id }
            .sortedBy { (row, _) -> row.departure_seconds }
            .take(limit)
            .map { (row, ft) ->
                val (f, t) = ft
                val depUpdate = updates[com.scoot.transit.data.TripStopKey(row.trip_id, f)]
                val arrUpdate = updates[com.scoot.transit.data.TripStopKey(row.trip_id, t)]
                val schedDep = midnight.plusSeconds(row.departure_seconds.toLong())
                val schedArr = midnight.plusSeconds(row.to_arrival_seconds.toLong())
                PairResult(
                    agency = agency,
                    tripId = row.trip_id,
                    routeId = row.route_id,
                    routeShortName = row.route_short_name,
                    routeLongName = row.route_long_name,
                    direction = Direction.fromGtfs(row.direction_id),
                    headsign = row.headsign,
                    fromStopId = f,
                    toStopId = t,
                    scheduledDeparture = schedDep,
                    scheduledArrival = schedArr,
                    realtimeDeparture = depUpdate?.departure,
                    realtimeArrival = arrUpdate?.arrival,
                    cancelled = depUpdate?.cancelled == true || arrUpdate?.cancelled == true,
                )
            }
            .toList()
    }

    private suspend fun upcomingForDate(
        agency: Agency,
        stopId: String,
        serviceDate: LocalDate,
        afterInstant: Instant,
        limit: Int,
        isPrevDay: Boolean,
    ): List<Departure> {
        val active = calendar.activeServiceIds(agency.operatorId, dateFmt.format(serviceDate), serviceDate.dayOfWeek.value).toHashSet()
        if (active.isEmpty()) return emptyList()
        // Lower bound for the SQL filter: how many seconds after midnight of the service date does
        // `afterInstant` correspond to? (negative if afterInstant is before that midnight.)
        val serviceMidnight = serviceDate.atStartOfDay(zone).toInstant()
        val secondsSinceServiceMidnight = (afterInstant.epochSecond - serviceMidnight.epochSecond).toInt().coerceAtLeast(0)
        val rows = stopTimes.departuresFromStop(agency.operatorId, stopId, secondsSinceServiceMidnight, limit * 4)
        val updates = realtime.tripUpdatesFor(agency)
        return rows.asSequence()
            .filter { it.service_id in active }
            .map { it.toDomain(agency, updates, serviceDate, zone) }
            .take(limit)
            .toList()
    }
}

data class PairResult(
    val agency: Agency,
    val tripId: String,
    val routeId: String,
    val routeShortName: String?,
    val routeLongName: String?,
    val direction: Direction,
    val headsign: String?,
    val fromStopId: String,
    val toStopId: String,
    val scheduledDeparture: Instant,
    val scheduledArrival: Instant,
    val realtimeDeparture: Instant?,
    val realtimeArrival: Instant?,
    val cancelled: Boolean,
)

private fun DepartureRow.toDomain(
    agency: Agency,
    updates: Map<com.scoot.transit.data.TripStopKey, com.scoot.transit.data.StopUpdate>,
    serviceDate: LocalDate,
    zone: ZoneId,
): Departure {
    val scheduledInstant = serviceDate.atStartOfDay(zone).toInstant().plusSeconds(departure_seconds.toLong())
    val update = updates[com.scoot.transit.data.TripStopKey(trip_id, stop_id)]
    return Departure(
        agency = agency,
        stopId = stop_id,
        tripId = trip_id,
        routeId = route_id,
        routeShortName = route_short_name,
        routeLongName = route_long_name,
        direction = Direction.fromGtfs(direction_id),
        headsign = headsign,
        scheduled = scheduledInstant,
        realtime = update?.departure,
        delaySeconds = update?.delaySeconds,
        cancelled = update?.cancelled == true,
        source = if (update != null) DataSource.GTFS_RT else DataSource.SCHEDULE,
    )
}

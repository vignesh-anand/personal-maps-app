package com.scoot.transit.data

import com.scoot.transit.data.db.CalendarDao
import com.scoot.transit.data.db.DepartureRow
import com.scoot.transit.data.db.PairTripRow
import com.scoot.transit.data.db.StopTimeDao
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.DataSource
import com.scoot.transit.domain.Departure
import com.scoot.transit.domain.Direction
import java.time.LocalDate
import java.time.LocalTime
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
        val sameDay = family.flatMap {
            upcomingForDate(agency, it, from.toLocalDate(), from.toLocalTime(), limit, isPrevDay = false)
        }.distinctBy { it.tripId to it.stopId }
        if (sameDay.size >= limit) {
            return sameDay.sortedBy { it.scheduled }.take(limit)
        }
        val prevDay = from.toLocalDate().minusDays(1)
        val shifted = family.flatMap {
            upcomingForDate(
                agency = agency,
                stopId = it,
                date = prevDay,
                after = from.toLocalTime(),
                limit = limit - sameDay.size,
                isPrevDay = true,
            )
        }
        return (sameDay + shifted).sortedBy { it.scheduled }.take(limit)
    }

    suspend fun pairTrips(
        agency: Agency,
        fromStopId: String,
        toStopId: String,
        from: ZonedDateTime = ZonedDateTime.now(zone),
        limit: Int = 20,
    ): List<PairResult> {
        val date = from.toLocalDate()
        val secs = from.toLocalTime().toSecondOfDay()
        val active = calendar.activeServiceIds(agency.operatorId, dateFmt.format(date), date.dayOfWeek.value).toHashSet()
        val rows = stopTimes.pairTrips(agency.operatorId, fromStopId, toStopId, secs, limit * 4)
        val updates = realtime.tripUpdatesFor(agency)
        return rows.filter { it.service_id in active }
            .take(limit)
            .map { row ->
                val depUpdate = updates[com.scoot.transit.data.TripStopKey(row.trip_id, fromStopId)]
                val arrUpdate = updates[com.scoot.transit.data.TripStopKey(row.trip_id, toStopId)]
                val schedDep = LocalTime.ofSecondOfDay(((row.departure_seconds % 86_400) + 86_400) % 86_400.toLong())
                val schedArr = LocalTime.ofSecondOfDay(((row.to_arrival_seconds % 86_400) + 86_400) % 86_400.toLong())
                PairResult(
                    agency = agency,
                    tripId = row.trip_id,
                    routeId = row.route_id,
                    routeShortName = row.route_short_name,
                    routeLongName = row.route_long_name,
                    direction = Direction.fromGtfs(row.direction_id),
                    headsign = row.headsign,
                    fromStopId = fromStopId,
                    toStopId = toStopId,
                    scheduledDeparture = schedDep,
                    scheduledArrival = schedArr,
                    realtimeDeparture = depUpdate?.departure,
                    realtimeArrival = arrUpdate?.arrival,
                    cancelled = depUpdate?.cancelled == true || arrUpdate?.cancelled == true,
                )
            }
    }

    private suspend fun upcomingForDate(
        agency: Agency,
        stopId: String,
        date: LocalDate,
        after: LocalTime,
        limit: Int,
        isPrevDay: Boolean,
    ): List<Departure> {
        val active = calendar.activeServiceIds(agency.operatorId, dateFmt.format(date), date.dayOfWeek.value).toHashSet()
        if (active.isEmpty()) return emptyList()
        val afterSecs = if (isPrevDay) after.toSecondOfDay() + 86_400 else after.toSecondOfDay()
        val rows = stopTimes.departuresFromStop(agency.operatorId, stopId, afterSecs, limit * 4)
        val updates = realtime.tripUpdatesFor(agency)
        return rows.asSequence()
            .filter { it.service_id in active }
            .map { it.toDomain(agency, updates) }
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
    val scheduledDeparture: LocalTime,
    val scheduledArrival: LocalTime,
    val realtimeDeparture: java.time.Instant?,
    val realtimeArrival: java.time.Instant?,
    val cancelled: Boolean,
)

private fun DepartureRow.toDomain(
    agency: Agency,
    updates: Map<com.scoot.transit.data.TripStopKey, com.scoot.transit.data.StopUpdate>,
): Departure {
    val scheduled = LocalTime.ofSecondOfDay(((departure_seconds % 86_400) + 86_400) % 86_400.toLong())
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
        scheduled = scheduled,
        realtime = update?.departure,
        delaySeconds = update?.delaySeconds,
        cancelled = update?.cancelled == true,
        source = if (update != null) DataSource.GTFS_RT else DataSource.SCHEDULE,
    )
}

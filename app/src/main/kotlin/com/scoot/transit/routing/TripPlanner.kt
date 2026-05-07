package com.scoot.transit.routing

import com.scoot.transit.data.DepartureRepo
import com.scoot.transit.data.GtfsRealtimeRepo
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.data.PairResult
import com.scoot.transit.data.RouteLeg
import com.scoot.transit.data.RoutingRepo
import com.scoot.transit.data.UserPrefsRepo
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.Direction
import com.scoot.transit.domain.Geo
import com.scoot.transit.domain.LatLng
import com.scoot.transit.domain.Place
import com.scoot.transit.domain.ScooterOnTransitRule
import com.scoot.transit.domain.Station
import com.scoot.transit.domain.TripLeg
import com.scoot.transit.domain.TripPlan
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-rolled multimodal trip planner. The algorithm:
 *  1. Pick K nearest stations to origin within scooter range (per agency).
 *  2. Pick K nearest stations to dest within scooter range.
 *  3. For each (access, egress) pair on the same agency, query DB for matching scheduled
 *     trips, merge GTFS-RT, score by total time, return top N.
 *  4. If no train option works, the caller can attempt bus fallback.
 */
@Singleton
class TripPlanner @Inject constructor(
    private val statics: GtfsStaticRepo,
    private val departures: DepartureRepo,
    private val routing: RoutingRepo,
    private val realtime: GtfsRealtimeRepo,
    private val prefs: UserPrefsRepo,
) {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")

    suspend fun plan(
        from: Place,
        to: Place,
        timing: TripTiming = TripTiming.DepartAt(Instant.now()),
        agencies: List<Agency> = listOf(Agency.CALTRAIN, Agency.BART),
        maxResults: Int = 5,
    ): List<TripPlan> {
        val maxRange = prefs.maxScootRangeMiles()
        val targetTime = when (timing) {
            is TripTiming.DepartAt -> timing.at
            is TripTiming.ArriveBy -> timing.by
        }
        val basis = ZonedDateTime.ofInstant(targetTime, zone)

        // First: pure scooter direct option (often beats short-distance multimodal).
        val direct = directScoot(from, to, basis, timing)

        val plans = mutableListOf<TripPlan>()
        direct?.let { plans += it }

        // Disrupted route IDs (skip plans that include them).
        val disruptedRoutes = agencies.flatMap { agency ->
            realtime.serviceAlertsFor(agency).flatMap { it.routeIds.map { rid -> agency to rid } }
        }.toSet()

        for (agency in agencies) {
            val accessCandidates = statics.stationsWithinMiles(agency, from.location, maxRange, limit = 4)
            val egressCandidates = statics.stationsWithinMiles(agency, to.location, maxRange, limit = 4)
            if (accessCandidates.isEmpty() || egressCandidates.isEmpty()) continue
            for (access in accessCandidates) {
                for (egress in egressCandidates) {
                    if (access.stopId == egress.stopId) continue
                    val accessLeg = routing.scootLeg(from.location, access.location) ?: continue
                    val egressLeg = routing.scootLeg(egress.location, to.location) ?: continue
                    if (accessLeg.distanceMiles + egressLeg.distanceMiles > maxRange) continue

                    val pairOptions = pairOptionsFor(agency, access, egress, accessLeg, egressLeg, timing, basis)
                    for (option in pairOptions.take(2)) {
                        val transit = option.legs.firstOrNull { it is TripLeg.Transit } as? TripLeg.Transit
                        val routeKey = transit?.let { agency to (it.routeShortName ?: it.routeLongName ?: it.tripId) }
                        if (routeKey != null && routeKey in disruptedRoutes) {
                            plans += option.copy(notes = option.notes + "Possible disruption on this route - check service alerts")
                        } else {
                            plans += option
                        }
                    }
                }
            }
        }

        return plans
            .distinctBy { it.signature() }
            .sortedBy { it.arrivalTime }
            .take(maxResults)
    }

    private suspend fun directScoot(
        from: Place,
        to: Place,
        basis: ZonedDateTime,
        timing: TripTiming,
    ): TripPlan? {
        val maxRange = prefs.maxScootRangeMiles()
        val crow = Geo.distanceMiles(from.location, to.location)
        if (crow > maxRange) return null
        val leg = routing.scootLeg(from.location, to.location) ?: return null
        if (leg.distanceMiles > maxRange) return null
        val depart = when (timing) {
            is TripTiming.DepartAt -> timing.at
            is TripTiming.ArriveBy -> timing.by.minusSeconds(leg.durationSeconds.toLong())
        }
        val arrival = depart.plusSeconds(leg.durationSeconds.toLong())
        return TripPlan(
            legs = listOf(
                TripLeg.Scoot(
                    from = from,
                    to = to,
                    duration = Duration.ofSeconds(leg.durationSeconds.toLong()),
                    polyline = leg.polyline,
                    distanceMiles = leg.distanceMiles,
                ),
            ),
            totalDuration = Duration.ofSeconds(leg.durationSeconds.toLong()),
            departureTime = depart,
            arrivalTime = arrival,
            totalScootMiles = leg.distanceMiles,
            transferCount = 0,
            notes = if (leg.polyline == null) listOf("Estimated route") else emptyList(),
        )
    }

    private suspend fun pairOptionsFor(
        agency: Agency,
        access: Station,
        egress: Station,
        accessLeg: RouteLeg,
        egressLeg: RouteLeg,
        timing: TripTiming,
        basis: ZonedDateTime,
    ): List<TripPlan> {
        val accessSeconds = accessLeg.durationSeconds.toLong()
        val egressSeconds = egressLeg.durationSeconds.toLong()

        val queryStart = when (timing) {
            is TripTiming.DepartAt -> ZonedDateTime.ofInstant(timing.at, zone).plusSeconds(accessSeconds)
            // Conservative: still query forward from now, then filter for arrival <= deadline.
            is TripTiming.ArriveBy -> ZonedDateTime.now(zone)
        }

        val pairs: List<PairResult> = departures.pairTrips(agency, access.stopId, egress.stopId, queryStart, limit = 30)

        val results = mutableListOf<TripPlan>()
        for (pair in pairs) {
            if (pair.cancelled) continue
            val depTime = pair.computeInstant(pair.realtimeDeparture, pair.scheduledDeparture, basis.toLocalDate())
            val arrTime = pair.computeInstant(pair.realtimeArrival, pair.scheduledArrival, basis.toLocalDate())
            if (timing is TripTiming.ArriveBy && arrTime.plusSeconds(egressSeconds).isAfter(timing.by)) continue
            if (timing is TripTiming.DepartAt) {
                val earliestBoard = timing.at.plusSeconds(accessSeconds)
                if (depTime.isBefore(earliestBoard)) continue
            }
            val tripFrom = Place(name = access.name, location = access.location, stationId = access.stopId)
            val tripTo = Place(name = egress.name, location = egress.location, stationId = egress.stopId)
            val originPlace = Place("Origin", access.location)
            val destPlace = Place("Destination", egress.location)
            val transit = TripLeg.Transit(
                from = tripFrom,
                to = tripTo,
                duration = Duration.between(depTime, arrTime),
                polyline = null,
                agency = agency,
                routeShortName = pair.routeShortName,
                routeLongName = pair.routeLongName,
                tripId = pair.tripId,
                headsign = pair.headsign,
                direction = pair.direction,
                departureTime = depTime,
                arrivalTime = arrTime,
                intermediateStops = emptyList(),
                rule = scootRuleFor(agency, depTime),
            )
            val accessScoot = TripLeg.Scoot(
                from = originPlace.copy(name = "Origin"),
                to = tripFrom,
                duration = Duration.ofSeconds(accessSeconds),
                polyline = accessLeg.polyline,
                distanceMiles = accessLeg.distanceMiles,
            )
            val egressScoot = TripLeg.Scoot(
                from = tripTo,
                to = destPlace.copy(name = "Destination"),
                duration = Duration.ofSeconds(egressSeconds),
                polyline = egressLeg.polyline,
                distanceMiles = egressLeg.distanceMiles,
            )
            val totalDeparture = depTime.minusSeconds(accessSeconds)
            val totalArrival = arrTime.plusSeconds(egressSeconds)
            results += TripPlan(
                legs = listOf(accessScoot, transit, egressScoot),
                totalDuration = Duration.between(totalDeparture, totalArrival),
                departureTime = totalDeparture,
                arrivalTime = totalArrival,
                totalScootMiles = accessLeg.distanceMiles + egressLeg.distanceMiles,
                transferCount = 0,
                notes = buildList { if (pair.realtimeDeparture == null) add("Schedule only") },
            )
        }
        return results
            .distinctBy { (it.legs[1] as TripLeg.Transit).tripId }
            .sortedBy { it.arrivalTime }
    }

    /** Route-aware scooter-on-transit rule. Caltrain: OK always. BART: peak weekday morning/evening = restricted. */
    private fun scootRuleFor(agency: Agency, depart: Instant): ScooterOnTransitRule {
        if (agency == Agency.CALTRAIN) return ScooterOnTransitRule.OK
        if (agency != Agency.BART) return ScooterOnTransitRule.OK
        val zdt = ZonedDateTime.ofInstant(depart, zone)
        if (zdt.dayOfWeek.value >= 6) return ScooterOnTransitRule.OK
        val hour = zdt.hour
        return if (hour in 7..8 || hour in 16..18) ScooterOnTransitRule.PEAK_RESTRICTED else ScooterOnTransitRule.OK
    }

    private fun PairResult.computeInstant(
        realtime: Instant?,
        scheduledLocalTime: LocalTime,
        date: LocalDate,
    ): Instant {
        if (realtime != null) return realtime
        return ZonedDateTime.of(date, scheduledLocalTime, zone).toInstant()
    }

    private fun TripPlan.signature(): String = legs.joinToString("|") { leg ->
        when (leg) {
            is TripLeg.Transit -> "T:${leg.agency}:${leg.tripId}:${leg.from.stationId}-${leg.to.stationId}"
            is TripLeg.Scoot -> "S:${leg.from.stationId ?: ""}-${leg.to.stationId ?: ""}"
            is TripLeg.Walk -> "W:${leg.from.stationId ?: ""}-${leg.to.stationId ?: ""}"
        }
    }
}

sealed interface TripTiming {
    data class DepartAt(val at: Instant) : TripTiming
    data class ArriveBy(val by: Instant) : TripTiming
}

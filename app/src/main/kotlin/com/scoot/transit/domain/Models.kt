package com.scoot.transit.domain

import java.time.Instant

data class LatLng(val lat: Double, val lng: Double)

data class Station(
    val agency: Agency,
    val stopId: String,
    val name: String,
    val location: LatLng,
    val parentStation: String? = null,
)

enum class Direction(val gtfsCode: Int) {
    NORTHBOUND(0),
    SOUTHBOUND(1);

    companion object {
        fun fromGtfs(code: Int): Direction = if (code == 0) NORTHBOUND else SOUTHBOUND
    }
}

data class Departure(
    val agency: Agency,
    val stopId: String,
    val tripId: String,
    val routeId: String,
    val routeShortName: String?,
    val routeLongName: String?,
    val direction: Direction,
    val headsign: String?,
    val scheduled: Instant,
    val realtime: Instant?,
    val delaySeconds: Int? = null,
    val cancelled: Boolean = false,
    val source: DataSource = DataSource.SCHEDULE,
) {
    val isLive: Boolean get() = realtime != null && source != DataSource.SCHEDULE
    val displayMinutesUntil: Long?
        get() = realtime?.let { java.time.Duration.between(Instant.now(), it).toMinutes() }
}

enum class DataSource { SCHEDULE, GTFS_RT, BART_ETD }

/**
 * A computed multimodal trip plan: scoot -> transit -> scoot, with optional bus fallback.
 */
data class TripPlan(
    val legs: List<TripLeg>,
    val totalDuration: java.time.Duration,
    val departureTime: Instant,
    val arrivalTime: Instant,
    val totalScootMiles: Double,
    val transferCount: Int,
    val notes: List<String> = emptyList(),
) {
    val leaveByTime: Instant get() = departureTime
}

sealed interface TripLeg {
    val duration: java.time.Duration
    val from: Place
    val to: Place
    val polyline: String?

    data class Scoot(
        override val from: Place,
        override val to: Place,
        override val duration: java.time.Duration,
        override val polyline: String?,
        val distanceMiles: Double,
    ) : TripLeg

    data class Walk(
        override val from: Place,
        override val to: Place,
        override val duration: java.time.Duration,
        override val polyline: String?,
        val distanceMiles: Double,
    ) : TripLeg

    data class Transit(
        override val from: Place,
        override val to: Place,
        override val duration: java.time.Duration,
        override val polyline: String?,
        val agency: Agency,
        val routeShortName: String?,
        val routeLongName: String?,
        val tripId: String,
        val headsign: String?,
        val direction: Direction,
        val departureTime: Instant,
        val arrivalTime: Instant,
        val intermediateStops: List<Station>,
        val rule: ScooterOnTransitRule = ScooterOnTransitRule.OK,
    ) : TripLeg
}

data class Place(
    val name: String,
    val location: LatLng,
    val stationId: String? = null,
)

enum class ScooterOnTransitRule(val label: String) {
    OK("Scooter OK"),
    PEAK_RESTRICTED("Peak-hour restriction"),
    NOT_ALLOWED("No scooters");
}

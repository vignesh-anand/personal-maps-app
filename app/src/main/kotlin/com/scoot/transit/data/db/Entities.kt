package com.scoot.transit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GTFS static data is keyed by (agency, gtfsId) - each agency uses its own ID space.
 */
@Entity(tableName = "stops", primaryKeys = ["agency", "stop_id"], indices = [Index(value = ["lat", "lng"])])
data class StopEntity(
    val agency: String,
    val stop_id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val parent_station: String? = null,
    val location_type: Int = 0,
)

@Entity(tableName = "routes", primaryKeys = ["agency", "route_id"])
data class RouteEntity(
    val agency: String,
    val route_id: String,
    val short_name: String?,
    val long_name: String?,
    val type: Int,
    val color: String?,
)

@Entity(
    tableName = "trips",
    primaryKeys = ["agency", "trip_id"],
    indices = [Index(value = ["agency", "route_id"]), Index(value = ["agency", "service_id"])]
)
data class TripEntity(
    val agency: String,
    val trip_id: String,
    val route_id: String,
    val service_id: String,
    val direction_id: Int,
    val headsign: String?,
)

@Entity(
    tableName = "stop_times",
    primaryKeys = ["agency", "trip_id", "stop_sequence"],
    indices = [
        Index(value = ["agency", "stop_id", "departure_seconds"]),
        Index(value = ["agency", "trip_id"])
    ]
)
data class StopTimeEntity(
    val agency: String,
    val trip_id: String,
    val stop_id: String,
    val stop_sequence: Int,
    /** Seconds from noon-12h, can exceed 86400 for after-midnight times (per GTFS spec). */
    val arrival_seconds: Int,
    val departure_seconds: Int,
)

@Entity(tableName = "calendar", primaryKeys = ["agency", "service_id"])
data class CalendarEntity(
    val agency: String,
    val service_id: String,
    val monday: Int,
    val tuesday: Int,
    val wednesday: Int,
    val thursday: Int,
    val friday: Int,
    val saturday: Int,
    val sunday: Int,
    /** YYYYMMDD inclusive */
    val start_date: String,
    val end_date: String,
)

@Entity(tableName = "calendar_dates", primaryKeys = ["agency", "service_id", "date"])
data class CalendarDateEntity(
    val agency: String,
    val service_id: String,
    val date: String,
    val exception_type: Int,
)

/** Cached ORS leg between origin/dest grid cells and a station. */
@Entity(
    tableName = "leg_cache",
    primaryKeys = ["origin_key", "dest_key", "profile"],
    indices = [Index(value = ["fetched_at"])]
)
data class LegCacheEntity(
    val origin_key: String,
    val dest_key: String,
    val profile: String,
    val distance_meters: Double,
    val duration_seconds: Double,
    val polyline: String?,
    val fetched_at: Long,
)

@Entity(tableName = "user_prefs", primaryKeys = ["key"])
data class UserPrefEntity(
    val key: String,
    val value: String,
)

@Entity(tableName = "favorites", primaryKeys = ["agency", "stop_id"])
data class FavoriteEntity(
    val agency: String,
    val stop_id: String,
    val sort_order: Int = 0,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val label: String,
    val from_name: String,
    val from_lat: Double,
    val from_lng: Double,
    val to_name: String,
    val to_lat: Double,
    val to_lng: Double,
    val sort_order: Int = 0,
)

@Entity(tableName = "alert_history", primaryKeys = ["alert_id"])
data class AlertHistoryEntity(
    val alert_id: String,
    val first_seen_at: Long,
    val last_notified_at: Long,
)

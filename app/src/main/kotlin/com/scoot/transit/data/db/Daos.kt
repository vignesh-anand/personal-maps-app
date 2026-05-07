package com.scoot.transit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM stops WHERE agency = :agency")
    suspend fun forAgency(agency: String): List<StopEntity>

    @Query("SELECT * FROM stops WHERE agency = :agency AND stop_id = :stopId LIMIT 1")
    suspend fun byId(agency: String, stopId: String): StopEntity?

    /** Children of a parent station, used to find every platform that belongs to a logical station. */
    @Query("SELECT * FROM stops WHERE agency = :agency AND parent_station = :parentId")
    suspend fun children(agency: String, parentId: String): List<StopEntity>

    @Query("SELECT * FROM stops WHERE agency = :agency AND name LIKE :query LIMIT 25")
    suspend fun searchByName(agency: String, query: String): List<StopEntity>

    /**
     * Returns stops within a bounding box; caller filters by exact distance.
     */
    @Query("SELECT * FROM stops WHERE agency = :agency AND lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng")
    suspend fun nearby(agency: String, minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<StopEntity>

    @Query("SELECT * FROM stops WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng")
    suspend fun nearbyAllAgencies(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<StopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stops: List<StopEntity>)

    @Query("DELETE FROM stops WHERE agency = :agency")
    suspend fun deleteAgency(agency: String)
}

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(routes: List<RouteEntity>)

    @Query("SELECT * FROM routes WHERE agency = :agency AND route_id = :routeId LIMIT 1")
    suspend fun byId(agency: String, routeId: String): RouteEntity?

    @Query("SELECT * FROM routes WHERE agency = :agency")
    suspend fun forAgency(agency: String): List<RouteEntity>

    @Query("DELETE FROM routes WHERE agency = :agency")
    suspend fun deleteAgency(agency: String)
}

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(trips: List<TripEntity>)

    @Query("SELECT * FROM trips WHERE agency = :agency AND trip_id = :tripId LIMIT 1")
    suspend fun byId(agency: String, tripId: String): TripEntity?

    @Query("DELETE FROM trips WHERE agency = :agency")
    suspend fun deleteAgency(agency: String)
}

@Dao
interface StopTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<StopTimeEntity>)

    @Query("DELETE FROM stop_times WHERE agency = :agency")
    suspend fun deleteAgency(agency: String)

    /**
     * Departures from a single stop after a given seconds-from-midnight. Includes route + headsign + trip metadata.
     */
    @Query(
        """
        SELECT
            st.agency        AS agency,
            st.trip_id       AS trip_id,
            st.stop_id       AS stop_id,
            st.stop_sequence AS stop_sequence,
            st.arrival_seconds AS arrival_seconds,
            st.departure_seconds AS departure_seconds,
            t.route_id       AS route_id,
            t.service_id     AS service_id,
            t.direction_id   AS direction_id,
            t.headsign       AS headsign,
            r.short_name     AS route_short_name,
            r.long_name      AS route_long_name
        FROM stop_times st
        JOIN trips t  ON t.agency = st.agency  AND t.trip_id = st.trip_id
        LEFT JOIN routes r ON r.agency = st.agency AND r.route_id = t.route_id
        WHERE st.agency = :agency
          AND st.stop_id = :stopId
          AND st.departure_seconds >= :afterSeconds
        ORDER BY st.departure_seconds ASC
        LIMIT :limit
        """
    )
    suspend fun departuresFromStop(
        agency: String,
        stopId: String,
        afterSeconds: Int,
        limit: Int = 50,
    ): List<DepartureRow>

    /**
     * Trips that visit fromStop and toStop in correct order, with departure time at fromStop after :afterSeconds.
     */
    @Query(
        """
        SELECT
            a.agency          AS agency,
            a.trip_id         AS trip_id,
            a.stop_id         AS stop_id,
            a.stop_sequence   AS stop_sequence,
            a.arrival_seconds AS arrival_seconds,
            a.departure_seconds AS departure_seconds,
            t.route_id        AS route_id,
            t.service_id      AS service_id,
            t.direction_id    AS direction_id,
            t.headsign        AS headsign,
            r.short_name      AS route_short_name,
            r.long_name       AS route_long_name,
            b.arrival_seconds AS to_arrival_seconds,
            b.stop_sequence   AS to_stop_sequence
        FROM stop_times a
        JOIN stop_times b
            ON b.agency = a.agency
            AND b.trip_id = a.trip_id
            AND b.stop_id = :toStop
            AND b.stop_sequence > a.stop_sequence
        JOIN trips t ON t.agency = a.agency AND t.trip_id = a.trip_id
        LEFT JOIN routes r ON r.agency = a.agency AND r.route_id = t.route_id
        WHERE a.agency = :agency
          AND a.stop_id = :fromStop
          AND a.departure_seconds >= :afterSeconds
        ORDER BY a.departure_seconds ASC
        LIMIT :limit
        """
    )
    suspend fun pairTrips(
        agency: String,
        fromStop: String,
        toStop: String,
        afterSeconds: Int,
        limit: Int = 50,
    ): List<PairTripRow>

    @Query(
        """
        SELECT
            st.agency        AS agency,
            st.trip_id       AS trip_id,
            st.stop_id       AS stop_id,
            st.stop_sequence AS stop_sequence,
            st.arrival_seconds AS arrival_seconds,
            st.departure_seconds AS departure_seconds,
            s.name           AS stop_name,
            s.lat            AS stop_lat,
            s.lng            AS stop_lng
        FROM stop_times st
        JOIN stops s ON s.agency = st.agency AND s.stop_id = st.stop_id
        WHERE st.agency = :agency AND st.trip_id = :tripId
        ORDER BY st.stop_sequence ASC
        """
    )
    suspend fun stopTimesForTrip(agency: String, tripId: String): List<TripStopRow>
}

data class DepartureRow(
    val agency: String,
    val trip_id: String,
    val stop_id: String,
    val stop_sequence: Int,
    val arrival_seconds: Int,
    val departure_seconds: Int,
    val route_id: String,
    val service_id: String,
    val direction_id: Int,
    val headsign: String?,
    val route_short_name: String?,
    val route_long_name: String?,
)

data class PairTripRow(
    val agency: String,
    val trip_id: String,
    val stop_id: String,
    val stop_sequence: Int,
    val arrival_seconds: Int,
    val departure_seconds: Int,
    val route_id: String,
    val service_id: String,
    val direction_id: Int,
    val headsign: String?,
    val route_short_name: String?,
    val route_long_name: String?,
    val to_arrival_seconds: Int,
    val to_stop_sequence: Int,
)

data class TripStopRow(
    val agency: String,
    val trip_id: String,
    val stop_id: String,
    val stop_sequence: Int,
    val arrival_seconds: Int,
    val departure_seconds: Int,
    val stop_name: String,
    val stop_lat: Double,
    val stop_lng: Double,
)

@Dao
interface CalendarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDates(rows: List<CalendarDateEntity>)

    @Query("SELECT * FROM calendar WHERE agency = :agency AND service_id = :serviceId LIMIT 1")
    suspend fun forService(agency: String, serviceId: String): CalendarEntity?

    @Query("SELECT * FROM calendar_dates WHERE agency = :agency AND date = :date")
    suspend fun exceptionsOn(agency: String, date: String): List<CalendarDateEntity>

    @Query("DELETE FROM calendar WHERE agency = :agency")
    suspend fun deleteCalendarAgency(agency: String)

    @Query("DELETE FROM calendar_dates WHERE agency = :agency")
    suspend fun deleteDatesAgency(agency: String)

    /** Resolved set of service_ids that are active on a given YYYYMMDD + day-of-week column name. */
    @Transaction
    @Query(
        """
        SELECT service_id FROM calendar
        WHERE agency = :agency
          AND start_date <= :date AND end_date >= :date
          AND ((:dow = 1 AND monday = 1)
            OR (:dow = 2 AND tuesday = 1)
            OR (:dow = 3 AND wednesday = 1)
            OR (:dow = 4 AND thursday = 1)
            OR (:dow = 5 AND friday = 1)
            OR (:dow = 6 AND saturday = 1)
            OR (:dow = 7 AND sunday = 1))
        UNION
        SELECT service_id FROM calendar_dates
        WHERE agency = :agency AND date = :date AND exception_type = 1
        EXCEPT
        SELECT service_id FROM calendar_dates
        WHERE agency = :agency AND date = :date AND exception_type = 2
        """
    )
    suspend fun activeServiceIds(agency: String, date: String, dow: Int): List<String>
}

@Dao
interface LegCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LegCacheEntity)

    @Query("SELECT * FROM leg_cache WHERE origin_key = :origin AND dest_key = :dest AND profile = :profile LIMIT 1")
    suspend fun get(origin: String, dest: String, profile: String): LegCacheEntity?

    @Query("DELETE FROM leg_cache WHERE fetched_at < :olderThan")
    suspend fun evictOlderThan(olderThan: Long)
}

@Dao
interface UserPrefDao {
    @Query("SELECT * FROM user_prefs WHERE key = :key LIMIT 1")
    suspend fun get(key: String): UserPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: UserPrefEntity)

    @Query("SELECT * FROM user_prefs")
    fun observeAll(): Flow<List<UserPrefEntity>>
}

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY sort_order")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE agency = :agency ORDER BY sort_order")
    suspend fun forAgency(agency: String): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE agency = :agency AND stop_id = :stopId")
    suspend fun remove(agency: String, stopId: String)

    @Query("DELETE FROM favorites WHERE agency = :agency")
    suspend fun clearAgency(agency: String)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY sort_order")
    fun observe(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(p: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AlertHistoryDao {
    @Query("SELECT * FROM alert_history WHERE alert_id = :id LIMIT 1")
    suspend fun byId(id: String): AlertHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: AlertHistoryEntity)

    @Query("DELETE FROM alert_history WHERE last_notified_at < :olderThan")
    suspend fun evictOlderThan(olderThan: Long)
}

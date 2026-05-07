package com.scoot.transit.data

import com.scoot.transit.BuildConfig
import com.scoot.transit.data.db.CalendarDao
import com.scoot.transit.data.db.RouteDao
import com.scoot.transit.data.db.ScootDatabase
import com.scoot.transit.data.db.StopDao
import com.scoot.transit.data.db.StopTimeDao
import com.scoot.transit.data.db.TripDao
import androidx.room.withTransaction
import com.scoot.transit.data.gtfs.GtfsParser
import com.scoot.transit.data.remote.TransitFeedApi
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.Direction
import com.scoot.transit.domain.Geo
import com.scoot.transit.domain.LatLng
import com.scoot.transit.domain.Station
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class GtfsStaticRepo @Inject constructor(
    private val api: TransitFeedApi,
    private val db: ScootDatabase,
    private val stops: StopDao,
    private val routes: RouteDao,
    private val trips: TripDao,
    private val stopTimes: StopTimeDao,
    private val calendar: CalendarDao,
    private val prefs: UserPrefsRepo,
) {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Refresh static GTFS for the given agency. Replaces the whole agency dataset atomically per-table. */
    suspend fun refresh(agency: Agency) {
        if (BuildConfig.API_511_KEY.isBlank()) {
            Timber.w("No 511 API key - skipping static GTFS refresh for ${agency.display}")
            return
        }
        Timber.i("Refreshing static GTFS for ${agency.display}...")
        val zip = api.gtfsZip(BuildConfig.API_511_KEY, agency.operatorId)
        val parsed = zip.byteStream().use { GtfsParser(agency.operatorId).parse(it) }
        db.withTransaction {
            stops.deleteAgency(agency.operatorId)
            routes.deleteAgency(agency.operatorId)
            trips.deleteAgency(agency.operatorId)
            stopTimes.deleteAgency(agency.operatorId)
            calendar.deleteCalendarAgency(agency.operatorId)
            calendar.deleteDatesAgency(agency.operatorId)
            stops.upsertAll(parsed.stops)
            routes.upsertAll(parsed.routes)
            trips.upsertAll(parsed.trips)
            parsed.stopTimes.chunked(5_000).forEach { stopTimes.insertAll(it) }
            calendar.upsertAll(parsed.calendar)
            calendar.upsertDates(parsed.calendarDates)
        }
        prefs.setString(prefsKeyLastRefresh(agency), System.currentTimeMillis().toString())
        Timber.i("Refreshed ${agency.display}: ${parsed.stops.size} stops, ${parsed.stopTimes.size} stop_times")
    }

    suspend fun isLoaded(agency: Agency): Boolean = stops.forAgency(agency.operatorId).isNotEmpty()

    suspend fun lastRefreshMillis(agency: Agency): Long? =
        prefs.getString(prefsKeyLastRefresh(agency))?.toLongOrNull()

    suspend fun stationsForAgency(agency: Agency): List<Station> =
        stops.forAgency(agency.operatorId).asCanonical(agency)

    /**
     * Returns the canonical (logical) station for any stop id. Promotes a directional/platform
     * child stop to its parent station so the UI never exposes "...Northbound" / "...Southbound"
     * platform-level pseudo-stations.
     */
    suspend fun stationById(agency: Agency, stopId: String): Station? {
        val self = stops.byId(agency.operatorId, stopId) ?: return null
        val parent = self.parent_station?.takeIf { it.isNotBlank() }
            ?.let { stops.byId(agency.operatorId, it) }
        return (parent ?: self).toDomain(agency)
    }

    /**
     * For a given stop, return every stop_id that belongs to the same logical station
     * (parent + all siblings, or the stop itself if it has no parent). This is essential for
     * Caltrain where each direction has its own stop_id but users think of "Palo Alto" as one station.
     */
    suspend fun stopFamily(agency: Agency, stopId: String): List<String> {
        val self = stops.byId(agency.operatorId, stopId) ?: return listOf(stopId)
        val parentId = self.parent_station?.takeIf { it.isNotBlank() } ?: self.stop_id
        val siblings = stops.children(agency.operatorId, parentId).map { it.stop_id }
        return (siblings + parentId + self.stop_id).distinct()
    }

    suspend fun searchStations(agency: Agency, query: String): List<Station> {
        if (query.isBlank()) return stationsForAgency(agency).take(25)
        val q = "%${query.trim()}%"
        return stops.searchByName(agency.operatorId, q).asCanonical(agency)
    }

    suspend fun nearestStation(agency: Agency, location: LatLng): Station? {
        val box = boundingBox(location, milesRadius = 25.0)
        return stops.nearby(agency.operatorId, box.minLat, box.maxLat, box.minLng, box.maxLng)
            .asCanonical(agency)
            .minByOrNull { Geo.distanceMiles(it.location, location) }
    }

    suspend fun stationsWithinMiles(agency: Agency?, location: LatLng, miles: Double, limit: Int = 5): List<Station> {
        val box = boundingBox(location, miles)
        val candidates = if (agency == null)
            stops.nearbyAllAgencies(box.minLat, box.maxLat, box.minLng, box.maxLng)
        else
            stops.nearby(agency.operatorId, box.minLat, box.maxLat, box.minLng, box.maxLng)
        // Promote children to parent stations and dedupe per agency before scoring.
        val canonical = candidates.groupBy { it.agency }.flatMap { (op, list) ->
            val ag = Agency.fromOperatorId(op) ?: agency ?: return@flatMap emptyList()
            list.asCanonical(ag)
        }
        return canonical
            .map { it to Geo.distanceMiles(it.location, location) }
            .filter { it.second <= miles }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Promote a list of raw stops to canonical stations: parent stations are kept as-is, child
     * stops are replaced by their parents (one entry per parent), and standalone stops with no
     * parent are kept. Entrances/exits (location_type=2+) are dropped.
     */
    private suspend fun List<com.scoot.transit.data.db.StopEntity>.asCanonical(agency: Agency): List<Station> {
        val parentIds = mutableSetOf<String>()
        val out = mutableListOf<Station>()
        for (s in this) {
            when (s.location_type) {
                1 -> {
                    if (parentIds.add(s.stop_id)) out += s.toDomain(agency)
                }
                0 -> {
                    val parentId = s.parent_station?.takeIf { it.isNotBlank() }
                    if (parentId == null) {
                        if (parentIds.add(s.stop_id)) out += s.toDomain(agency)
                    } else if (parentIds.add(parentId)) {
                        val parent = stops.byId(agency.operatorId, parentId)
                        if (parent != null) out += parent.toDomain(agency)
                        else out += s.toDomain(agency)
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    /** Active service ids for the given local date. Splits after-midnight trips by checking previous day. */
    suspend fun activeServiceIds(agency: Agency, date: LocalDate): List<String> {
        val dow = isoDow(date.dayOfWeek)
        return calendar.activeServiceIds(agency.operatorId, dateFmt.format(date), dow)
    }

    private fun isoDow(d: DayOfWeek): Int = d.value

    private fun prefsKeyLastRefresh(a: Agency) = "gtfs.last_refresh.${a.operatorId}"

    private fun boundingBox(c: LatLng, milesRadius: Double): Box {
        val latDelta = milesRadius / 69.0
        val lngDelta = milesRadius / (69.0 * Math.cos(Math.toRadians(c.lat)).coerceAtLeast(0.01))
        return Box(c.lat - latDelta, c.lat + latDelta, c.lng - lngDelta, c.lng + lngDelta)
    }
}

private data class Box(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

private fun com.scoot.transit.data.db.StopEntity.toDomain(agency: Agency): Station = Station(
    agency = agency,
    stopId = stop_id,
    name = name,
    location = LatLng(lat, lng),
    parentStation = parent_station,
)

internal fun Int.secondsToLocalTime(): LocalTime {
    val s = ((this % 86_400) + 86_400) % 86_400
    return LocalTime.ofSecondOfDay(s.toLong())
}

internal fun Int.gtfsDirection(): Direction = Direction.fromGtfs(this)

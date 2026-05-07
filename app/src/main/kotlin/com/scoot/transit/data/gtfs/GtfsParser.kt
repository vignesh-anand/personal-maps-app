package com.scoot.transit.data.gtfs

import com.scoot.transit.data.db.CalendarDateEntity
import com.scoot.transit.data.db.CalendarEntity
import com.scoot.transit.data.db.RouteEntity
import com.scoot.transit.data.db.StopEntity
import com.scoot.transit.data.db.StopTimeEntity
import com.scoot.transit.data.db.TripEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Streaming GTFS-zip parser. We never hold the whole file in memory - we extract one entry at a time
 * and emit batches. RFC 4180 CSV (quoted strings) is handled by [parseCsvLine].
 */
class GtfsParser(private val agency: String) {

    data class Result(
        val stops: List<StopEntity>,
        val routes: List<RouteEntity>,
        val trips: List<TripEntity>,
        val stopTimes: List<StopTimeEntity>,
        val calendar: List<CalendarEntity>,
        val calendarDates: List<CalendarDateEntity>,
    )

    fun parse(zip: InputStream): Result {
        var stops: List<StopEntity> = emptyList()
        var routes: List<RouteEntity> = emptyList()
        var trips: List<TripEntity> = emptyList()
        var stopTimes: List<StopTimeEntity> = emptyList()
        var calendar: List<CalendarEntity> = emptyList()
        var calendarDates: List<CalendarDateEntity> = emptyList()

        ZipInputStream(zip).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase().substringAfterLast('/')
                if (!entry.isDirectory) {
                    when (name) {
                        "stops.txt" -> stops = parseStops(zin)
                        "routes.txt" -> routes = parseRoutes(zin)
                        "trips.txt" -> trips = parseTrips(zin)
                        "stop_times.txt" -> stopTimes = parseStopTimes(zin)
                        "calendar.txt" -> calendar = parseCalendar(zin)
                        "calendar_dates.txt" -> calendarDates = parseCalendarDates(zin)
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return Result(stops, routes, trips, stopTimes, calendar, calendarDates)
    }

    private fun parseStops(stream: InputStream): List<StopEntity> = readCsv(stream) { row ->
        StopEntity(
            agency = agency,
            stop_id = row.req("stop_id"),
            name = row.req("stop_name"),
            lat = row.req("stop_lat").toDouble(),
            lng = row.req("stop_lon").toDouble(),
            parent_station = row.opt("parent_station").takeIf { !it.isNullOrBlank() },
            location_type = row.opt("location_type")?.toIntOrNull() ?: 0,
        )
    }

    private fun parseRoutes(stream: InputStream): List<RouteEntity> = readCsv(stream) { row ->
        RouteEntity(
            agency = agency,
            route_id = row.req("route_id"),
            short_name = row.opt("route_short_name"),
            long_name = row.opt("route_long_name"),
            type = row.opt("route_type")?.toIntOrNull() ?: 0,
            color = row.opt("route_color"),
        )
    }

    private fun parseTrips(stream: InputStream): List<TripEntity> = readCsv(stream) { row ->
        TripEntity(
            agency = agency,
            trip_id = row.req("trip_id"),
            route_id = row.req("route_id"),
            service_id = row.req("service_id"),
            direction_id = row.opt("direction_id")?.toIntOrNull() ?: 0,
            headsign = row.opt("trip_headsign"),
        )
    }

    private fun parseStopTimes(stream: InputStream): List<StopTimeEntity> = readCsv(stream) { row ->
        StopTimeEntity(
            agency = agency,
            trip_id = row.req("trip_id"),
            stop_id = row.req("stop_id"),
            stop_sequence = row.req("stop_sequence").toInt(),
            arrival_seconds = parseGtfsTime(row.opt("arrival_time")),
            departure_seconds = parseGtfsTime(row.opt("departure_time")),
        )
    }

    private fun parseCalendar(stream: InputStream): List<CalendarEntity> = readCsv(stream) { row ->
        CalendarEntity(
            agency = agency,
            service_id = row.req("service_id"),
            monday = row.opt("monday")?.toIntOrNull() ?: 0,
            tuesday = row.opt("tuesday")?.toIntOrNull() ?: 0,
            wednesday = row.opt("wednesday")?.toIntOrNull() ?: 0,
            thursday = row.opt("thursday")?.toIntOrNull() ?: 0,
            friday = row.opt("friday")?.toIntOrNull() ?: 0,
            saturday = row.opt("saturday")?.toIntOrNull() ?: 0,
            sunday = row.opt("sunday")?.toIntOrNull() ?: 0,
            start_date = row.req("start_date"),
            end_date = row.req("end_date"),
        )
    }

    private fun parseCalendarDates(stream: InputStream): List<CalendarDateEntity> = readCsv(stream) { row ->
        CalendarDateEntity(
            agency = agency,
            service_id = row.req("service_id"),
            date = row.req("date"),
            exception_type = row.req("exception_type").toInt(),
        )
    }

    /** "HH:MM:SS" or "H:MM:SS"; GTFS allows hours > 23 to encode after-midnight times. */
    private fun parseGtfsTime(s: String?): Int {
        if (s.isNullOrBlank()) return -1
        val parts = s.split(":")
        if (parts.size != 3) return -1
        return parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
    }

    private inline fun <T> readCsv(stream: InputStream, transform: (Row) -> T): List<T> {
        val out = ArrayList<T>(1024)
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val header = reader.readLine() ?: return emptyList()
        val cols = parseCsvLine(stripBom(header))
        val index = cols.withIndex().associate { (i, h) -> h.trim() to i }
        var line = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                val values = parseCsvLine(line)
                try {
                    out += transform(Row(values, index))
                } catch (_: Exception) {
                    // skip malformed row
                }
            }
            line = reader.readLine()
        }
        return out
    }

    private fun stripBom(s: String): String =
        if (s.isNotEmpty() && s[0] == '\uFEFF') s.substring(1) else s

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>(8)
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private class Row(val values: List<String>, val index: Map<String, Int>) {
        fun opt(col: String): String? = index[col]?.let { i -> values.getOrNull(i)?.takeIf { it.isNotEmpty() } }
        fun req(col: String): String = opt(col) ?: error("missing $col")
    }
}

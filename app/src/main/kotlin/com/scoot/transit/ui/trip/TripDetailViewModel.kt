package com.scoot.transit.ui.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoot.transit.data.GtfsRealtimeRepo
import com.scoot.transit.data.StopUpdate
import com.scoot.transit.data.TripStopKey
import com.scoot.transit.data.db.StopTimeDao
import com.scoot.transit.data.db.TripStopRow
import com.scoot.transit.domain.Agency
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val stopTimes: StopTimeDao,
    private val realtime: GtfsRealtimeRepo,
) : ViewModel() {

    private val agencyArg: String = saved["agency"] ?: "CT"
    private val tripIdArg: String = saved["tripId"] ?: ""
    private val focusStopId: String = saved["focusStopId"] ?: ""
    private val agency = Agency.fromOperatorId(agencyArg) ?: Agency.CALTRAIN
    private val zone = ZoneId.of("America/Los_Angeles")

    private val _state = MutableStateFlow(TripDetailState(focusStopId = focusStopId))
    val state: StateFlow<TripDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load()
            while (true) {
                delay(30_000)
                load()
            }
        }
    }

    private suspend fun load() {
        val rows = withContext(Dispatchers.IO) { stopTimes.stopTimesForTrip(agency.operatorId, tripIdArg) }
        if (rows.isEmpty()) {
            _state.value = _state.value.copy(isLoading = false, error = "Trip $tripIdArg not found")
            return
        }
        val updates = withContext(Dispatchers.IO) { realtime.tripUpdatesFor(agency) }
        val today: LocalDate = ZonedDateTime.now(zone).toLocalDate()
        val midnight = today.atStartOfDay(zone).toInstant()

        val stops = rows.map { row -> row.toStopVisit(midnight, updates, tripIdArg) }
        val nowInstant = Instant.now()
        val nextIdx = stops.indexOfFirst { stop -> (stop.realtimeArrival ?: stop.realtimeDeparture ?: stop.scheduledArrival).isAfter(nowInstant) }
            .let { if (it == -1) stops.lastIndex else it }

        _state.value = TripDetailState(
            tripId = tripIdArg,
            agency = agency,
            stops = stops,
            focusStopId = focusStopId,
            nextStopIndex = nextIdx,
            isLoading = false,
        )
    }
}

private fun TripStopRow.toStopVisit(
    serviceMidnight: Instant,
    updates: Map<TripStopKey, StopUpdate>,
    tripId: String,
): StopVisit {
    val update = updates[TripStopKey(tripId, stop_id)]
    return StopVisit(
        stopId = stop_id,
        stopName = stop_name,
        sequence = stop_sequence,
        scheduledArrival = serviceMidnight.plusSeconds(arrival_seconds.toLong()),
        scheduledDeparture = serviceMidnight.plusSeconds(departure_seconds.toLong()),
        realtimeArrival = update?.arrival,
        realtimeDeparture = update?.departure,
        cancelled = update?.cancelled == true,
    )
}

data class TripDetailState(
    val tripId: String = "",
    val agency: Agency = Agency.CALTRAIN,
    val stops: List<StopVisit> = emptyList(),
    val focusStopId: String = "",
    val nextStopIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class StopVisit(
    val stopId: String,
    val stopName: String,
    val sequence: Int,
    val scheduledArrival: Instant,
    val scheduledDeparture: Instant,
    val realtimeArrival: Instant?,
    val realtimeDeparture: Instant?,
    val cancelled: Boolean,
)

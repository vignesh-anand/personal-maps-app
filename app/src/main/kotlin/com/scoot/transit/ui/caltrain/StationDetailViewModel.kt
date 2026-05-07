package com.scoot.transit.ui.caltrain

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoot.transit.data.DepartureRepo
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.Departure
import com.scoot.transit.domain.Station
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class StationDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val statics: GtfsStaticRepo,
    private val departures: DepartureRepo,
) : ViewModel() {

    private val agencyArg: String = saved["agency"] ?: "CT"
    private val stopIdArg: String = saved["stopId"] ?: ""
    private val agency = Agency.fromOperatorId(agencyArg) ?: Agency.CALTRAIN

    private val _state = MutableStateFlow(StationDetailState())
    val state: StateFlow<StationDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val station = withContext(Dispatchers.IO) { statics.stationById(agency, stopIdArg) }
            val displayName = station?.let { displayName(it.name) } ?: "Station"
            _state.update { it.copy(station = station, displayName = displayName) }
            loadAll()
            while (true) { delay(45_000); loadAll() }
        }
    }

    /** Strips "Northbound/Southbound" platform suffix and "Caltrain Station" filler. */
    private fun displayName(raw: String): String =
        raw.replace(Regex("\\s*(Northbound|Southbound)$"), "")
            .replace(Regex("\\s*Caltrain Station$"), "")
            .trim()

    private suspend fun loadAll() {
        if (_state.value.station == null) return
        val now = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"))
        val deps = withContext(Dispatchers.IO) {
            departures.upcomingDepartures(agency, stopIdArg, now, limit = 60)
        }
        _state.update { it.copy(departures = deps, isLoading = false) }
    }
}

data class StationDetailState(
    val station: Station? = null,
    val displayName: String = "",
    val departures: List<Departure> = emptyList(),
    val isLoading: Boolean = true,
)

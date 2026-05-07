package com.scoot.transit.ui.bart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoot.transit.data.BartEtdRepo
import com.scoot.transit.data.DepartureRepo
import com.scoot.transit.data.GtfsRealtimeRepo
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.data.LocationRepo
import com.scoot.transit.data.PairResult
import com.scoot.transit.data.ServiceAlert
import com.scoot.transit.data.db.FavoriteEntity
import com.scoot.transit.data.db.FavoritesDao
import com.scoot.transit.data.remote.BartEtdRoute
import com.scoot.transit.domain.Agency
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
class BartViewModel @Inject constructor(
    private val statics: GtfsStaticRepo,
    private val departures: DepartureRepo,
    private val location: LocationRepo,
    private val favorites: FavoritesDao,
    private val etd: BartEtdRepo,
    private val realtime: GtfsRealtimeRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(BartState())
    val state: StateFlow<BartState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadCards()
            loadAllStations()
            startAutoRefresh()
        }
    }

    fun runPair(fromId: String, toId: String) {
        viewModelScope.launch {
            _state.update { it.copy(pairLoading = true, pairResults = emptyList()) }
            val results = withContext(Dispatchers.IO) {
                departures.pairTrips(Agency.BART, fromId, toId, ZonedDateTime.now(zone), 12)
            }
            _state.update { it.copy(pairLoading = false, pairResults = results, pairFromId = fromId, pairToId = toId) }
        }
    }

    fun toggleFavorite(stopId: String) {
        viewModelScope.launch {
            val existing = favorites.forAgency(Agency.BART.operatorId).any { it.stop_id == stopId }
            if (existing) favorites.remove(Agency.BART.operatorId, stopId)
            else favorites.add(FavoriteEntity(Agency.BART.operatorId, stopId, sort_order = 99))
            loadCards()
        }
    }

    private suspend fun loadAllStations() {
        val all = withContext(Dispatchers.IO) { statics.stationsForAgency(Agency.BART) }
        _state.update { it.copy(allStations = all.distinctBy { st -> st.name }.sortedBy { st -> st.name }) }
    }

    private suspend fun loadCards() {
        if (!statics.isLoaded(Agency.BART)) {
            _state.update { it.copy(loadingMessage = "BART schedule not yet downloaded - check Settings", isLoading = false) }
            return
        }
        _state.update { it.copy(isLoading = true) }
        val favStations = favorites.forAgency(Agency.BART.operatorId)
            .mapNotNull { statics.stationById(Agency.BART, it.stop_id) }
        val gpsStation = withContext(Dispatchers.IO) {
            location.current()?.let { statics.nearestStation(Agency.BART, it) }
        }
        val displayed = (favStations + listOfNotNull(gpsStation)).distinctBy { it.stopId }.take(4)
        val cards = displayed.map { station ->
            val abbr = bartAbbrFor(station)
            val routes = if (abbr != null) etd.etdFor(abbr) else null
            BartCard(station = station, etdRoutes = routes.orEmpty())
        }
        val alerts = withContext(Dispatchers.IO) { realtime.serviceAlertsFor(Agency.BART) }
        _state.update {
            it.copy(
                isLoading = false,
                cards = cards,
                gpsStationId = gpsStation?.stopId,
                serviceAlerts = alerts,
            )
        }
    }

    /** BART GTFS stop_ids encode the station abbreviation; we extract the 4-letter code. */
    private fun bartAbbrFor(station: Station): String? {
        val s = station.parentStation ?: station.stopId
        // Examples: place_PLZA, ASBY, EMBR. Strip a "place_" prefix if present.
        return s.removePrefix("place_").take(5).takeIf { it.isNotBlank() }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(45_000)
                loadCards()
            }
        }
    }

    private val zone = ZoneId.of("America/Los_Angeles")
}

data class BartState(
    val isLoading: Boolean = true,
    val loadingMessage: String? = null,
    val cards: List<BartCard> = emptyList(),
    val allStations: List<Station> = emptyList(),
    val gpsStationId: String? = null,
    val pairFromId: String? = null,
    val pairToId: String? = null,
    val pairLoading: Boolean = false,
    val pairResults: List<PairResult> = emptyList(),
    val serviceAlerts: List<ServiceAlert> = emptyList(),
)

data class BartCard(
    val station: Station,
    val etdRoutes: List<BartEtdRoute>,
)

package com.scoot.transit.ui.caltrain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoot.transit.data.DepartureRepo
import com.scoot.transit.data.GtfsRealtimeRepo
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.data.LocationRepo
import com.scoot.transit.data.PairResult
import com.scoot.transit.data.ServiceAlert
import com.scoot.transit.data.db.FavoritesDao
import com.scoot.transit.data.db.FavoriteEntity
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.Departure
import com.scoot.transit.domain.Direction
import com.scoot.transit.domain.LatLng
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

private val DEFAULT_FAVS = listOf("70171", "70191", "70211") // Palo Alto, California Ave, San Antonio Caltrain stop_ids

@HiltViewModel
class CaltrainViewModel @Inject constructor(
    private val statics: GtfsStaticRepo,
    private val departures: DepartureRepo,
    private val location: LocationRepo,
    private val favorites: FavoritesDao,
    private val realtime: GtfsRealtimeRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(CaltrainState())
    val state: StateFlow<CaltrainState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ensureDefaultFavorites()
            loadCards()
            startAutoRefresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { loadCards() }
    }

    fun runPair(fromStopId: String, toStopId: String) {
        viewModelScope.launch {
            _state.update { it.copy(pairLoading = true, pairResults = emptyList()) }
            val results = withContext(Dispatchers.IO) {
                departures.pairTrips(Agency.CALTRAIN, fromStopId, toStopId, ZonedDateTime.now(zone), 12)
            }
            _state.update { it.copy(pairLoading = false, pairResults = results, pairFromId = fromStopId, pairToId = toStopId) }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { statics.searchStations(Agency.CALTRAIN, query) }
            _state.update { it.copy(searchQuery = query, searchResults = list) }
        }
    }

    private suspend fun ensureDefaultFavorites() {
        val existing = favorites.forAgency(Agency.CALTRAIN.operatorId)
        if (existing.isEmpty()) {
            DEFAULT_FAVS.forEachIndexed { idx, id ->
                favorites.add(FavoriteEntity(Agency.CALTRAIN.operatorId, id, idx))
            }
        }
    }

    private suspend fun loadCards() {
        if (!statics.isLoaded(Agency.CALTRAIN)) {
            _state.update { it.copy(loadingMessage = "Caltrain schedule not yet downloaded - check Settings", isLoading = false) }
            return
        }
        _state.update { it.copy(isLoading = true) }
        val favIds = favorites.forAgency(Agency.CALTRAIN.operatorId).map { it.stop_id }
        val favStations = favIds.mapNotNull { statics.stationById(Agency.CALTRAIN, it) }
        val gpsStation = withContext(Dispatchers.IO) {
            location.current()?.let { statics.nearestStation(Agency.CALTRAIN, it) }
        }
        val displayedStations = (favStations + listOfNotNull(gpsStation)).distinctBy { it.stopId }.take(4)
        val cards = displayedStations.map { s -> buildCard(s) }
        val alerts = withContext(Dispatchers.IO) { realtime.serviceAlertsFor(Agency.CALTRAIN) }
        _state.update {
            it.copy(
                isLoading = false,
                cards = cards,
                gpsStationId = gpsStation?.stopId,
                serviceAlerts = alerts,
            )
        }
    }

    private suspend fun buildCard(station: Station): StationCardData {
        val now = ZonedDateTime.now(zone)
        val all = withContext(Dispatchers.IO) {
            departures.upcomingDepartures(Agency.CALTRAIN, station.stopId, now, limit = 30)
        }
        val nb = all.firstOrNull { it.direction == Direction.NORTHBOUND }
        val sb = all.firstOrNull { it.direction == Direction.SOUTHBOUND }
        return StationCardData(station = station, nextNorth = nb, nextSouth = sb)
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

data class CaltrainState(
    val isLoading: Boolean = true,
    val loadingMessage: String? = null,
    val cards: List<StationCardData> = emptyList(),
    val gpsStationId: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Station> = emptyList(),
    val pairFromId: String? = null,
    val pairToId: String? = null,
    val pairLoading: Boolean = false,
    val pairResults: List<PairResult> = emptyList(),
    val serviceAlerts: List<ServiceAlert> = emptyList(),
)

data class StationCardData(
    val station: Station,
    val nextNorth: Departure?,
    val nextSouth: Departure?,
)

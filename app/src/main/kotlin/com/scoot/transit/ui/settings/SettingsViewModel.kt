package com.scoot.transit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoot.transit.BuildConfig
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.data.LocationRepo
import com.scoot.transit.data.NamedPlace
import com.scoot.transit.data.PlacesRepo
import com.scoot.transit.data.UserPrefsRepo
import com.scoot.transit.data.db.FavoriteEntity
import com.scoot.transit.data.db.FavoritesDao
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.LatLng
import com.scoot.transit.domain.Station
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPrefsRepo,
    private val statics: GtfsStaticRepo,
    private val location: LocationRepo,
    private val favorites: FavoritesDao,
    private val places: PlacesRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    private suspend fun refresh() {
        val home = prefs.getNamedPlace(UserPrefsRepo.KEY_HOME)
        val work = prefs.getNamedPlace(UserPrefsRepo.KEY_WORK)
        val range = prefs.maxScootRangeMiles()
        val notifAlerts = prefs.getBool(UserPrefsRepo.KEY_NOTIF_ALERTS, default = true)
        val notifLastTrain = prefs.getBool(UserPrefsRepo.KEY_NOTIF_LAST_TRAIN, default = true)

        val ctFavs = favorites.forAgency(Agency.CALTRAIN.operatorId)
            .mapNotNull { statics.stationById(Agency.CALTRAIN, it.stop_id) }
        val baFavs = favorites.forAgency(Agency.BART.operatorId)
            .mapNotNull { statics.stationById(Agency.BART, it.stop_id) }

        val ctLoaded = statics.isLoaded(Agency.CALTRAIN)
        val baLoaded = statics.isLoaded(Agency.BART)
        val ctLastRefresh = statics.lastRefreshMillis(Agency.CALTRAIN)
        val baLastRefresh = statics.lastRefreshMillis(Agency.BART)

        _state.update {
            it.copy(
                home = home,
                work = work,
                maxScootMiles = range,
                notifAlerts = notifAlerts,
                notifLastTrain = notifLastTrain,
                caltrainFavs = ctFavs,
                bartFavs = baFavs,
                caltrainLoaded = ctLoaded,
                bartLoaded = baLoaded,
                caltrainLastRefresh = ctLastRefresh,
                bartLastRefresh = baLastRefresh,
                hasFiveOneOneKey = BuildConfig.API_511_KEY.isNotBlank(),
                hasGoogleKey = BuildConfig.API_GOOGLE_KEY.isNotBlank(),
            )
        }
    }

    fun searchPlaces(query: String) {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { places.autocomplete(query) }
            _state.update { it.copy(placeQuery = query, placeResults = results) }
        }
    }

    fun setHomeFromPlace(suggestion: PlacesRepo.PlaceSuggestion) = setNamedPlace(UserPrefsRepo.KEY_HOME, suggestion)
    fun setWorkFromPlace(suggestion: PlacesRepo.PlaceSuggestion) = setNamedPlace(UserPrefsRepo.KEY_WORK, suggestion)

    private fun setNamedPlace(key: String, suggestion: PlacesRepo.PlaceSuggestion) {
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) { places.fetch(suggestion.placeId) } ?: return@launch
            prefs.setNamedPlace(key, details.name.ifBlank { suggestion.primary }, details.location)
            refresh()
        }
    }

    fun setHomeFromCurrentLocation() = setFromGps(UserPrefsRepo.KEY_HOME, "Home")
    fun setWorkFromCurrentLocation() = setFromGps(UserPrefsRepo.KEY_WORK, "Work")

    private fun setFromGps(key: String, label: String) {
        viewModelScope.launch {
            val loc: LatLng = location.current() ?: return@launch
            prefs.setNamedPlace(key, label, loc)
            refresh()
        }
    }

    fun setMaxRangeMiles(v: Double) {
        viewModelScope.launch {
            prefs.setMaxScootRangeMiles(v)
            refresh()
        }
    }

    fun setAlertsEnabled(v: Boolean) {
        viewModelScope.launch { prefs.setBool(UserPrefsRepo.KEY_NOTIF_ALERTS, v); refresh() }
    }

    fun setLastTrainEnabled(v: Boolean) {
        viewModelScope.launch { prefs.setBool(UserPrefsRepo.KEY_NOTIF_LAST_TRAIN, v); refresh() }
    }

    fun addFavoriteCaltrain(stopId: String) = addFavorite(Agency.CALTRAIN, stopId)
    fun addFavoriteBart(stopId: String) = addFavorite(Agency.BART, stopId)
    fun removeFavorite(agency: Agency, stopId: String) {
        viewModelScope.launch {
            favorites.remove(agency.operatorId, stopId); refresh()
        }
    }

    private fun addFavorite(agency: Agency, stopId: String) {
        viewModelScope.launch {
            favorites.add(FavoriteEntity(agency.operatorId, stopId, sort_order = 99))
            refresh()
        }
    }

    fun searchStation(agency: Agency, q: String) {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { statics.searchStations(agency, q) }
            _state.update {
                if (agency == Agency.CALTRAIN) it.copy(caltrainSearch = results)
                else it.copy(bartSearch = results)
            }
        }
    }
}

data class SettingsState(
    val home: NamedPlace? = null,
    val work: NamedPlace? = null,
    val maxScootMiles: Double = 15.0,
    val notifAlerts: Boolean = true,
    val notifLastTrain: Boolean = true,
    val caltrainFavs: List<Station> = emptyList(),
    val bartFavs: List<Station> = emptyList(),
    val caltrainLoaded: Boolean = false,
    val bartLoaded: Boolean = false,
    val caltrainLastRefresh: Long? = null,
    val bartLastRefresh: Long? = null,
    val hasFiveOneOneKey: Boolean = false,
    val hasGoogleKey: Boolean = false,
    val placeQuery: String = "",
    val placeResults: List<PlacesRepo.PlaceSuggestion> = emptyList(),
    val caltrainSearch: List<Station> = emptyList(),
    val bartSearch: List<Station> = emptyList(),
)

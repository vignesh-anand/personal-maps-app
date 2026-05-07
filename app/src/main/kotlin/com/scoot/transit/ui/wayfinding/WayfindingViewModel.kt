package com.scoot.transit.ui.wayfinding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoot.transit.data.LocationRepo
import com.scoot.transit.data.NamedPlace
import com.scoot.transit.data.PlacesRepo
import com.scoot.transit.data.PresetShortcutManager
import com.scoot.transit.data.UserPrefsRepo
import com.scoot.transit.data.db.PresetDao
import com.scoot.transit.data.db.PresetEntity
import com.scoot.transit.domain.LatLng
import com.scoot.transit.domain.Place
import com.scoot.transit.domain.TripPlan
import com.scoot.transit.routing.TripPlanner
import com.scoot.transit.routing.TripTiming
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class WayfindingViewModel @Inject constructor(
    private val planner: TripPlanner,
    private val location: LocationRepo,
    private val prefs: UserPrefsRepo,
    private val places: PlacesRepo,
    private val presets: PresetDao,
    private val shortcuts: PresetShortcutManager,
) : ViewModel() {

    private val _state = MutableStateFlow(WayfindingState())
    val state: StateFlow<WayfindingState> = _state.asStateFlow()

    val presetsFlow: StateFlow<List<PresetEntity>> = presets.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val home = prefs.getNamedPlace(UserPrefsRepo.KEY_HOME)
            val work = prefs.getNamedPlace(UserPrefsRepo.KEY_WORK)
            val maxRange = prefs.maxScootRangeMiles()
            _state.update { it.copy(home = home, work = work, maxScootMiles = maxRange) }
            ensurePresets(home, work)
        }
    }

    fun setFromCurrentLocation() {
        viewModelScope.launch {
            val loc = location.current() ?: return@launch
            _state.update { it.copy(from = Place(name = "Current location", location = loc)) }
        }
    }

    fun setToCurrentLocation() {
        viewModelScope.launch {
            val loc = location.current() ?: return@launch
            _state.update { it.copy(to = Place(name = "Current location", location = loc)) }
        }
    }

    fun setFromHome() {
        val h = state.value.home ?: return
        _state.update { it.copy(from = Place(name = "Home", location = h.location)) }
    }

    fun setToHome() {
        val h = state.value.home ?: return
        _state.update { it.copy(to = Place(name = "Home", location = h.location)) }
    }

    fun setFromWork() {
        val w = state.value.work ?: return
        _state.update { it.copy(from = Place(name = "Work", location = w.location)) }
    }

    fun setToWork() {
        val w = state.value.work ?: return
        _state.update { it.copy(to = Place(name = "Work", location = w.location)) }
    }

    fun searchFrom(q: String) = searchPlaces(q, isFrom = true)
    fun searchTo(q: String) = searchPlaces(q, isFrom = false)

    private fun searchPlaces(q: String, isFrom: Boolean) {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { places.autocomplete(q) }
            _state.update {
                if (isFrom) it.copy(fromQuery = q, fromSuggestions = results)
                else it.copy(toQuery = q, toSuggestions = results)
            }
        }
    }

    fun pickFrom(s: PlacesRepo.PlaceSuggestion) = pickPlace(s, isFrom = true)
    fun pickTo(s: PlacesRepo.PlaceSuggestion) = pickPlace(s, isFrom = false)

    private fun pickPlace(s: PlacesRepo.PlaceSuggestion, isFrom: Boolean) {
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) { places.fetch(s.placeId) } ?: return@launch
            val place = Place(name = s.primary, location = details.location)
            _state.update {
                if (isFrom) it.copy(from = place, fromQuery = "", fromSuggestions = emptyList())
                else it.copy(to = place, toQuery = "", toSuggestions = emptyList())
            }
        }
    }

    fun setTiming(timing: TripTiming) = _state.update { it.copy(timing = timing) }

    fun plan() {
        val from = _state.value.from ?: return
        val to = _state.value.to ?: return
        viewModelScope.launch {
            _state.update { it.copy(isPlanning = true, plans = emptyList()) }
            val timing = _state.value.timing
            val plans = withContext(Dispatchers.IO) { planner.plan(from, to, timing = timing) }
            _state.update { it.copy(isPlanning = false, plans = plans) }
        }
    }

    fun loadPreset(presetId: String) {
        viewModelScope.launch {
            val p = presets.byId(presetId) ?: return@launch
            _state.update {
                it.copy(
                    from = Place(p.from_name, LatLng(p.from_lat, p.from_lng)),
                    to = Place(p.to_name, LatLng(p.to_lat, p.to_lng)),
                )
            }
            plan()
        }
    }

    fun saveCurrentAsPreset(label: String) {
        val from = _state.value.from ?: return
        val to = _state.value.to ?: return
        viewModelScope.launch {
            val id = "preset-${System.currentTimeMillis()}"
            presets.put(
                PresetEntity(
                    id = id,
                    label = label,
                    from_name = from.name,
                    from_lat = from.location.lat,
                    from_lng = from.location.lng,
                    to_name = to.name,
                    to_lat = to.location.lat,
                    to_lng = to.location.lng,
                )
            )
            shortcuts.rebuildFromCurrentPresets()
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            presets.delete(id)
            shortcuts.rebuildFromCurrentPresets()
        }
    }

    private suspend fun ensurePresets(home: NamedPlace?, work: NamedPlace?) {
        if (home == null || work == null) return
        if (presets.byId("home-to-work") == null) {
            presets.put(
                PresetEntity(
                    id = "home-to-work",
                    label = "Home → Work",
                    from_name = "Home",
                    from_lat = home.location.lat,
                    from_lng = home.location.lng,
                    to_name = "Work",
                    to_lat = work.location.lat,
                    to_lng = work.location.lng,
                    sort_order = 0,
                )
            )
        }
        if (presets.byId("work-to-home") == null) {
            presets.put(
                PresetEntity(
                    id = "work-to-home",
                    label = "Work → Home",
                    from_name = "Work",
                    from_lat = work.location.lat,
                    from_lng = work.location.lng,
                    to_name = "Home",
                    to_lat = home.location.lat,
                    to_lng = home.location.lng,
                    sort_order = 1,
                )
            )
        }
        shortcuts.rebuildFromCurrentPresets()
    }
}

data class WayfindingState(
    val home: NamedPlace? = null,
    val work: NamedPlace? = null,
    val maxScootMiles: Double = 15.0,
    val from: Place? = null,
    val to: Place? = null,
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromSuggestions: List<PlacesRepo.PlaceSuggestion> = emptyList(),
    val toSuggestions: List<PlacesRepo.PlaceSuggestion> = emptyList(),
    val timing: TripTiming = TripTiming.DepartAt(Instant.now()),
    val isPlanning: Boolean = false,
    val plans: List<TripPlan> = emptyList(),
)

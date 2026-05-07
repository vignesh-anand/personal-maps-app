package com.scoot.transit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scoot.transit.data.NamedPlace
import com.scoot.transit.data.PlacesRepo
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.Station
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ApiKeyStatus(state) }
            item { GtfsStatus(state, vm) }
            item { PlacesSection(state, vm) }
            item { RangeSection(state, vm) }
            item { NotificationsSection(state, vm) }
            item { FavoritesSection(state, vm) }
        }
    }
}

@Composable
private fun ApiKeyStatus(s: SettingsState) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("API keys", style = MaterialTheme.typography.titleMedium)
            KeyRow("511.org", s.hasFiveOneOneKey)
            KeyRow("Google (Maps + Places + Directions)", s.hasGoogleKey)
            Text(
                "Set in local.properties as SCOOT_511_API_KEY and SCOOT_GOOGLE_MAPS_API_KEY. Rebuild to apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun KeyRow(label: String, present: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (text, color) = if (present) "configured" to MaterialTheme.colorScheme.tertiary
        else "missing" to MaterialTheme.colorScheme.error
        Text(label, modifier = Modifier.weight(1f))
        Text(text, color = color)
    }
}

@Composable
private fun GtfsStatus(s: SettingsState, vm: SettingsViewModel) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GTFS data", style = MaterialTheme.typography.titleMedium)
            GtfsRow("Caltrain", s.caltrainLoaded, s.caltrainLastRefresh)
            GtfsRow("BART", s.bartLoaded, s.bartLastRefresh)
            Text(
                "Auto-refreshes weekly on Wi-Fi. Tap below to fetch now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Button(onClick = { vm.refreshGtfsNow() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh schedules now")
            }
        }
    }
}

@Composable
private fun GtfsRow(label: String, loaded: Boolean, lastRefreshMs: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        if (loaded) {
            val dateText = lastRefreshMs?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "loaded"
            Text(dateText, color = MaterialTheme.colorScheme.tertiary)
        } else {
            Text("not loaded", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PlacesSection(state: SettingsState, vm: SettingsViewModel) {
    var query by remember { mutableStateOf("") }
    var pickingFor by remember { mutableStateOf<String?>(null) }

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Home & Work", style = MaterialTheme.typography.titleMedium)
            PlaceRow(
                label = "Home",
                place = state.home,
                onSetFromGps = vm::setHomeFromCurrentLocation,
                onSetSearch = { pickingFor = "home" },
            )
            PlaceRow(
                label = "Work",
                place = state.work,
                onSetFromGps = vm::setWorkFromCurrentLocation,
                onSetSearch = { pickingFor = "work" },
            )

            if (pickingFor != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.searchPlaces(it)
                    },
                    placeholder = { Text("Search address or place") },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.placeResults.forEach { suggestion ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            when (pickingFor) {
                                "home" -> vm.setHomeFromPlace(suggestion)
                                "work" -> vm.setWorkFromPlace(suggestion)
                            }
                            pickingFor = null
                            query = ""
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(suggestion.primary)
                            Text(suggestion.secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    label: String,
    place: NamedPlace?,
    onSetFromGps: () -> Unit,
    onSetSearch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                place?.name ?: "not set",
                style = MaterialTheme.typography.bodyMedium,
                color = if (place == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onSetFromGps) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Use current location")
        }
        Button(onClick = onSetSearch) { Text("Search") }
    }
}

@Composable
private fun RangeSection(s: SettingsState, vm: SettingsViewModel) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Max scooter range: ${"%.1f".format(s.maxScootMiles)} mi", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = s.maxScootMiles.toFloat(),
                onValueChange = { vm.setMaxRangeMiles(it.toDouble()) },
                valueRange = 1f..25f,
                steps = 23,
            )
            Text(
                "Used to filter candidate transit stations when planning routes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun NotificationsSection(s: SettingsState, vm: SettingsViewModel) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Service alerts", s.notifAlerts, vm::setAlertsEnabled)
            ToggleRow("Last-train warning", s.notifLastTrain, vm::setLastTrainEnabled)
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesSection(s: SettingsState, vm: SettingsViewModel) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Favorite stations", style = MaterialTheme.typography.titleMedium)
            FavGroup(
                title = "Caltrain",
                stations = s.caltrainFavs,
                searchResults = s.caltrainSearch,
                onSearch = { vm.searchStation(Agency.CALTRAIN, it) },
                onAdd = vm::addFavoriteCaltrain,
                onRemove = { vm.removeFavorite(Agency.CALTRAIN, it) },
            )
            FavGroup(
                title = "BART",
                stations = s.bartFavs,
                searchResults = s.bartSearch,
                onSearch = { vm.searchStation(Agency.BART, it) },
                onAdd = vm::addFavoriteBart,
                onRemove = { vm.removeFavorite(Agency.BART, it) },
            )
        }
    }
}

@Composable
private fun FavGroup(
    title: String,
    stations: List<Station>,
    searchResults: List<Station>,
    onSearch: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        stations.forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.name, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(st.stopId) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            placeholder = { Text("Add $title station") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        searchResults.take(8).forEach { st ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    onAdd(st.stopId); query = ""
                },
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(st.name, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

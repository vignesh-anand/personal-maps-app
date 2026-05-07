package com.scoot.transit.ui.caltrain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.navigation.NavController
import com.scoot.transit.R
import com.scoot.transit.ui.common.EmptyState
import com.scoot.transit.ui.common.LoadingState
import com.scoot.transit.ui.common.MinutesPill
import com.scoot.transit.ui.common.ServiceAlertsBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaltrainScreen(nav: NavController, vm: CaltrainViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Caltrain") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) item { LoadingState() }
            state.loadingMessage?.let { item { EmptyState(it) } }
            if (state.serviceAlerts.isNotEmpty()) {
                item { ServiceAlertsBanner(state.serviceAlerts) }
            }
            items(state.cards, key = { it.station.stopId }) { card ->
                StationCard(
                    data = card,
                    isGps = card.station.stopId == state.gpsStationId,
                    onClick = { nav.navigate("station/CT/${card.station.stopId}") }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Search station", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                        vm.search(it)
                    },
                    placeholder = { Text(stringResLocal(R.string.search_station_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(state.searchResults, key = { "search-${it.stopId}" }) { st ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        nav.navigate("station/CT/${st.stopId}")
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(st.name, modifier = Modifier.padding(12.dp))
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Plan a Caltrain trip", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PairTripCard(state, onPlan = vm::runPair)
            }
        }
    }
}

@Composable
private fun StationCard(data: StationCardData, isGps: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cleanStationName(data.station.name), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                if (isGps) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DirectionBlock(label = "Northbound", departure = data.nextNorth)
                DirectionBlock(label = "Southbound", departure = data.nextSouth)
            }
        }
    }
}

@Composable
private fun DirectionBlock(label: String, departure: com.scoot.transit.domain.Departure?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(4.dp))
        if (departure == null) {
            Text("--", style = MaterialTheme.typography.titleMedium)
        } else {
            MinutesPill(eta = departure.realtime, scheduled = departure.scheduled, source = departure.source)
            departure.headsign?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun PairTripCard(state: CaltrainState, onPlan: (String, String) -> Unit) {
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    val pool = (state.cards.map { it.station } + state.allStations).distinctBy { it.stopId }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StationTypeahead(
                label = "From",
                stations = pool,
                selectedId = fromId,
                onSelect = { fromId = it.stopId },
            )
            StationTypeahead(
                label = "To",
                stations = pool,
                selectedId = toId,
                onSelect = { toId = it.stopId },
            )
            androidx.compose.material3.Button(
                onClick = {
                    val f = fromId; val t = toId
                    if (f != null && t != null) onPlan(f, t)
                },
                enabled = fromId != null && toId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Show next trains") }
            if (state.pairLoading) {
                LoadingState()
            } else {
                state.pairResults.forEach { r ->
                    PairResultRow(r)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationTypeahead(
    label: String,
    stations: List<com.scoot.transit.domain.Station>,
    selectedId: String?,
    onSelect: (com.scoot.transit.domain.Station) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val initialName = stations.firstOrNull { it.stopId == selectedId }?.name ?: ""
    var text by remember(selectedId) { mutableStateOf(initialName) }
    val filtered = remember(text, stations) {
        if (text.isBlank()) stations.take(20)
        else stations.filter { it.name.contains(text, ignoreCase = true) }.take(20)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filtered.forEach { st ->
                    DropdownMenuItem(
                        text = { Text(st.name) },
                        onClick = {
                            text = st.name
                            onSelect(st)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PairResultRow(r: com.scoot.transit.data.PairResult) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val depTime = r.realtimeDeparture
            val schedDep = r.scheduledDeparture
            val routeLabel = r.routeShortName ?: r.routeLongName ?: r.tripId
            Text("$routeLabel ${if (r.cancelled) "(cancelled)" else ""}", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinutesPill(
                    eta = depTime,
                    scheduled = schedDep,
                    source = if (depTime != null) com.scoot.transit.domain.DataSource.GTFS_RT else com.scoot.transit.domain.DataSource.SCHEDULE,
                )
                Spacer(Modifier.width(8.dp))
                val arrFmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                    .withZone(java.time.ZoneId.of("America/Los_Angeles"))
                Text("→ ${arrFmt.format(r.realtimeArrival ?: r.scheduledArrival)}", style = MaterialTheme.typography.bodySmall)
            }
            r.headsign?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun stringResLocal(id: Int): String = androidx.compose.ui.res.stringResource(id = id)

private fun cleanStationName(raw: String): String =
    raw.replace(Regex("\\s*(Northbound|Southbound)$"), "")
        .replace(Regex("\\s*Caltrain Station$"), "")
        .trim()

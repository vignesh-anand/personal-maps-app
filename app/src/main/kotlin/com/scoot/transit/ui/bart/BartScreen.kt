package com.scoot.transit.ui.bart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scoot.transit.data.remote.BartEtdEstimate
import com.scoot.transit.data.remote.BartEtdRoute
import com.scoot.transit.domain.Station
import com.scoot.transit.ui.common.EmptyState
import com.scoot.transit.ui.common.LoadingState
import com.scoot.transit.ui.common.ServiceAlertsBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BartScreen(vm: BartViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Stations", "By line", "Pair")

    Scaffold(topBar = { TopAppBar(title = { Text("BART") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = i == tab, onClick = { tab = i }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> StationsTab(state, vm)
                1 -> ByLineTab(state.allStations)
                2 -> PairTab(state, vm)
            }
        }
    }
}

@Composable
private fun StationsTab(state: BartState, vm: BartViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoading) item { LoadingState() }
        state.loadingMessage?.let { item { EmptyState(it) } }
        if (state.serviceAlerts.isNotEmpty()) item { ServiceAlertsBanner(state.serviceAlerts) }
        items(state.cards, key = { it.station.stopId }) { card ->
            BartStationCard(card, isGps = card.station.stopId == state.gpsStationId, onToggleFavorite = { vm.toggleFavorite(card.station.stopId) })
        }
        if (!state.isLoading && state.cards.isEmpty()) {
            item { EmptyState("Add BART stations as favorites in Settings, or grant location for nearest-station auto-detect") }
        }
    }
}

@Composable
private fun BartStationCard(card: BartCard, isGps: Boolean, onToggleFavorite: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.station.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (isGps) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = "Favorite")
                }
            }
            if (card.etdRoutes.isEmpty()) {
                Text("No live data", color = MaterialTheme.colorScheme.outline)
            } else {
                card.etdRoutes.forEach { route ->
                    BartRouteRow(route)
                }
            }
        }
    }
}

@Composable
private fun BartRouteRow(route: BartEtdRoute) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(route.estimate.firstOrNull()?.hexcolor ?: route.estimate.firstOrNull()?.color)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("To ${route.destination}", style = MaterialTheme.typography.titleMedium)
            Text(
                route.estimate.joinToString(" • ") { e -> formatEtdMinutes(e) },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ColorDot(hexOrName: String?) {
    val color = parseColorHex(hexOrName) ?: MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(12.dp).background(color, shape = CircleShape))
}

private fun parseColorHex(s: String?): Color? {
    if (s.isNullOrBlank()) return null
    return runCatching {
        val raw = s.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$raw"))
    }.getOrNull()
}

private fun formatEtdMinutes(e: BartEtdEstimate): String =
    if (e.minutes == "Leaving") "Leaving" else "${e.minutes}m"

@Composable
private fun ByLineTab(allStations: List<Station>) {
    val grouped = remember(allStations) {
        BART_LINES.map { line ->
            line to allStations.filter { st -> st.name in line.stationNames }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(grouped) { (line, stations) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(14.dp).background(line.color, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(line.label, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stations.joinToString(" → ") { it.name }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairTab(state: BartState, vm: BartViewModel) {
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StationSelector("From", state.allStations, fromId) { fromId = it }
        StationSelector("To", state.allStations, toId) { toId = it }
        androidx.compose.material3.Button(
            onClick = {
                val f = fromId; val t = toId
                if (f != null && t != null) vm.runPair(f, t)
            },
            enabled = fromId != null && toId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Show next trains") }
        if (state.pairLoading) LoadingState()
        state.pairResults.forEach { r ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${r.routeShortName ?: r.routeLongName ?: r.tripId}", style = MaterialTheme.typography.titleMedium)
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                        .withZone(java.time.ZoneId.of("America/Los_Angeles"))
                    val dep = r.realtimeDeparture ?: r.scheduledDeparture
                    val arr = r.realtimeArrival ?: r.scheduledArrival
                    Text("Depart ${fmt.format(dep)} → arrive ${fmt.format(arr)}", style = MaterialTheme.typography.bodyMedium)
                    r.headsign?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationSelector(label: String, stations: List<Station>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = stations.firstOrNull { it.stopId == selected }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            stations.forEach { st ->
                DropdownMenuItem(
                    text = { Text(st.name) },
                    onClick = { onSelect(st.stopId); expanded = false },
                )
            }
        }
    }
}

private data class BartLine(val label: String, val color: Color, val stationNames: Set<String>)

private val BART_LINES = listOf(
    BartLine(
        "Yellow - Antioch / SFO",
        Color(0xFFFFD200),
        setOf(
            "Antioch", "Pittsburg Center", "Pittsburg/Bay Point", "North Concord/Martinez", "Concord",
            "Pleasant Hill/Contra Costa Centre", "Walnut Creek", "Lafayette", "Orinda", "Rockridge",
            "MacArthur", "19th St/Oakland", "12th St/Oakland City Center", "West Oakland",
            "Embarcadero", "Montgomery St", "Powell St", "Civic Center/UN Plaza",
            "16th St/Mission", "24th St/Mission", "Glen Park", "Balboa Park",
            "Daly City", "Colma", "South San Francisco", "San Bruno", "San Francisco Int'l Airport",
            "Millbrae",
        )
    ),
    BartLine(
        "Blue - Dublin/Pleasanton / Daly City",
        Color(0xFF0099CC),
        setOf(
            "Dublin/Pleasanton", "West Dublin/Pleasanton", "Castro Valley", "Bay Fair",
            "San Leandro", "Coliseum", "Fruitvale", "Lake Merritt", "12th St/Oakland City Center",
            "West Oakland", "Embarcadero", "Montgomery St", "Powell St", "Civic Center/UN Plaza",
            "16th St/Mission", "24th St/Mission", "Glen Park", "Balboa Park", "Daly City",
        )
    ),
    BartLine(
        "Green - Berryessa / Daly City",
        Color(0xFF00B140),
        setOf(
            "Berryessa/North San Jose", "Milpitas", "Warm Springs/South Fremont", "Fremont",
            "Union City", "South Hayward", "Hayward", "Bay Fair", "San Leandro", "Coliseum",
            "Fruitvale", "Lake Merritt", "12th St/Oakland City Center", "West Oakland",
            "Embarcadero", "Montgomery St", "Powell St", "Civic Center/UN Plaza",
            "16th St/Mission", "24th St/Mission", "Glen Park", "Balboa Park", "Daly City",
        )
    ),
    BartLine(
        "Orange - Berryessa / Richmond",
        Color(0xFFFF6F00),
        setOf(
            "Berryessa/North San Jose", "Milpitas", "Warm Springs/South Fremont", "Fremont",
            "Union City", "South Hayward", "Hayward", "Bay Fair", "San Leandro", "Coliseum",
            "Fruitvale", "Lake Merritt", "19th St/Oakland", "MacArthur",
            "Ashby", "Downtown Berkeley", "North Berkeley", "El Cerrito Plaza", "El Cerrito del Norte", "Richmond",
        )
    ),
    BartLine(
        "Red - Richmond / Millbrae+SFO",
        Color(0xFFE5002B),
        setOf(
            "Richmond", "El Cerrito del Norte", "El Cerrito Plaza", "North Berkeley", "Downtown Berkeley",
            "Ashby", "MacArthur", "19th St/Oakland", "12th St/Oakland City Center", "West Oakland",
            "Embarcadero", "Montgomery St", "Powell St", "Civic Center/UN Plaza",
            "16th St/Mission", "24th St/Mission", "Glen Park", "Balboa Park", "Daly City",
            "Colma", "South San Francisco", "San Bruno", "Millbrae",
        )
    ),
)

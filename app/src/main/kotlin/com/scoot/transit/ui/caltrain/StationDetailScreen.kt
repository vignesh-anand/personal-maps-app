package com.scoot.transit.ui.caltrain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scoot.transit.domain.Departure
import com.scoot.transit.domain.Direction
import com.scoot.transit.ui.common.LoadingState
import com.scoot.transit.ui.common.MinutesPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    agency: String,
    stopId: String,
    onBack: () -> Unit,
    onTripClick: (tripId: String) -> Unit = {},
    vm: StationDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.displayName.ifBlank { "Station" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                listOf("Both", "Northbound", "Southbound").forEachIndexed { i, label ->
                    Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(label) })
                }
            }

            if (state.isLoading) {
                LoadingState()
            } else {
                val filtered = when (tabIndex) {
                    1 -> state.departures.filter { it.direction == Direction.NORTHBOUND }
                    2 -> state.departures.filter { it.direction == Direction.SOUTHBOUND }
                    else -> state.departures
                }.sortedBy { it.realtime ?: it.scheduled }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { "${it.tripId}-${it.stopId}" }) {
                        DepartureRow(it, onClick = { onTripClick(it.tripId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(d: Departure, onClick: () -> Unit) {
    val dirChip = when (d.direction) {
        Direction.NORTHBOUND -> "NB"
        Direction.SOUTHBOUND -> "SB"
    }
    val dirColor = when (d.direction) {
        Direction.NORTHBOUND -> MaterialTheme.colorScheme.primary
        Direction.SOUTHBOUND -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = dirColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    dirChip,
                    style = MaterialTheme.typography.labelSmall,
                    color = dirColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(d.routeShortName ?: d.routeLongName ?: d.tripId, style = MaterialTheme.typography.titleMedium)
                d.headsign?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                if (d.cancelled) {
                    Text("Cancelled", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            MinutesPill(eta = d.realtime, scheduled = d.scheduled, source = d.source)
        }
    }
}

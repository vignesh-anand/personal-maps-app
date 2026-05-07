package com.scoot.transit.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scoot.transit.ui.common.LoadingState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    agency: String,
    tripId: String,
    focusStopId: String,
    onBack: () -> Unit,
    vm: TripDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip $tripId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(state.error!!) }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    items(state.stops, key = { it.sequence }) { visit ->
                        StopRow(
                            visit = visit,
                            isFocused = visit.stopId == state.focusStopId,
                            isFirst = visit.sequence == state.stops.first().sequence,
                            isLast = visit.sequence == state.stops.last().sequence,
                            isPast = visit.sequence < (state.stops.getOrNull(state.nextStopIndex)?.sequence ?: 0),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    visit: StopVisit,
    isFocused: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    isPast: Boolean,
) {
    val accent = if (isFocused) MaterialTheme.colorScheme.primary else if (isPast) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary
    Row(modifier = Modifier.fillMaxWidth().height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // Timeline rail (line + dot)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp).fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (isFirst) 12.dp else 16.dp)
                    .background(if (isFirst) Color.Transparent else accent.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(if (isFocused) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else accent.copy(alpha = 0.5f))
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        visit.stopName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isPast) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                    if (visit.cancelled) {
                        Text("Cancelled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    val instant = visit.realtimeArrival ?: visit.realtimeDeparture ?: visit.scheduledArrival
                    val schedInstant = visit.scheduledArrival
                    val isLive = visit.realtimeArrival != null || visit.realtimeDeparture != null
                    val mins = Duration.between(Instant.now(), instant).toMinutes()
                    val timeText = when {
                        isPast -> fmtTime(schedInstant)
                        mins <= 0 -> "Now"
                        mins < 60 -> "${mins}m"
                        else -> fmtTime(instant)
                    }
                    Text(
                        timeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPast) MaterialTheme.colorScheme.outline else if (mins <= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLive && !isPast) {
                        Text("live", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    } else if (!isPast) {
                        Text("sched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

private fun fmtTime(instant: Instant): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("America/Los_Angeles")).format(instant)
